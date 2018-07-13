/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.exception.InsufficientCapacityException;
import sun.misc.Unsafe;

import com.lmax.disruptor.util.Util;


/**
 * 这个类没有缓存行填充，因为主要的四个域中，可变的域有两个：gatingSequenceCache 和availableBuffer；
 * gatingSequenceCache 本身为Sequence，做了缓存行填充。
 * availableBuffer是一个很大的数组，其中的每个元素都会改变，但是同一时刻只会有一个线程读取访问修改其中的元素的值，所以，没必要做缓冲行填充。
 *
 * <p>Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.
 * Suitable for use for sequencing across multiple publisher threads.</p>
 *
 * <p> * Note on {@link Sequencer#getCursor()}:  With this sequencer the cursor value is updated after the call
 * to {@link Sequencer#next()}, to determine the highest available sequence that can be read, then
 * {@link Sequencer#getHighestPublishedSequence(long, long)} should be used.</p>
 */
public final class MultiProducerSequencer extends AbstractSequencer {

    private static final Unsafe UNSAFE              = Util.getUnsafe();

    /** 数组第一个元素的偏移量（内存位置） */
    private static final long   BASE                = UNSAFE.arrayBaseOffset(int[].class);

    /** 数组每个元素大小 */
    private static final long   SCALE               = UNSAFE.arrayIndexScale(int[].class);

    /** 最慢消费者seq缓存，不是每次都计算,只有覆盖点>最慢消费者seq时，再次请求计算最新的最慢消费者seq */
    private final Sequence      gatingSequenceCache = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    /**
     * 这个数组标记RingBuffer的状态,判断可以消费的sequence
     * 每个槽存过几个Event，就是sequence到底转了多少圈，存在这个数组里，下标就是每个槽。
     * 为什么不直接将sequence存入availableBuffer，因为这样sequence值会过大，很容易溢出
     */
    private final int[]         availableBuffer;

    /**bufferSize-1,用于取模求圈数 */
    private final int           indexMask;

    /**8->3,为了后面>>>运算，m>>>n = m除以2的n次方，就是上面的n，用来定位某个sequence到底转了多少圈，用来标识已被发布的sequence*/
    private final int           indexShift;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize   the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public MultiProducerSequencer(int bufferSize, final WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
        availableBuffer = new int[bufferSize];
        indexMask = bufferSize - 1;
        indexShift = Util.log2(bufferSize);
        initialiseAvailableBuffer();
    }

    /**
     * @see Sequencer#hasAvailableCapacity(int)
     */
    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity) {
        return hasAvailableCapacity(gatingSequences, requiredCapacity, cursor.get());
    }

    private boolean hasAvailableCapacity(Sequence[] gatingSequences, final int requiredCapacity, long cursorValue) {
        //覆盖点
        long wrapPoint = (cursorValue + requiredCapacity) - bufferSize;
        //最慢消费者的消费到的seq
        long cachedGatingSequence = gatingSequenceCache.get();

        //覆盖点过了最慢消费者的seq，或者cursorValue=Long.maxValue+1位负数，最慢消费者的seq大于cursorValue（不知是否理解有误）
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > cursorValue) {
            //重新计算获得最慢消费者的seq
            long minSequence = Util.getMinimumSequence(gatingSequences, cursorValue);
            gatingSequenceCache.set(minSequence);

            //如果仍然覆盖，则返回false
            if (wrapPoint > minSequence) {
                return false;
            }
        }

        return true;
    }

    /**
     * @see Sequencer#claim(long)
     */
    @Override
    public void claim(long sequence) {
        cursor.set(sequence);
    }

    /**
     * 阻塞获取可生产填充的下一seq
     * @see Sequencer#next()
     */
    @Override
    public long next() {
        return next(1);
    }

    /**
     * 阻塞获取可生产填充的下seq
     * 允许一次获取多个写节点
     * @see Sequencer#next(int)
     */
    @Override
    public long next(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be > 0");
        }

        long current;
        long next;

        // while循环主要服务CAS算法，不成功就重来
        do {
            // 生产者当前写入到的最新序列号
            current = cursor.get();
            // 要获取的序列号
            next = current + n;

            // wrapPoint是一个很关键的变量，这个变量决定生产者是否可以覆盖序列号nextSequence，
            // wrapPoint是为什么是nextSequence - bufferSize；RingBuffer表现出来的是一个环形的数据结构，实际上是一个长度为bufferSize的数组，
            // nextSequence - bufferSize如果nextSequence小于bufferSize wrapPoint是负数，表示可以一直生产；
            // 如果nextSequence大于bufferSize wrapPoint是一个大于0的数，由于生产者和消费者的序列号差距不能超过bufferSize
            // （超过bufferSize会覆盖消费者未消费的数据），wrapPoint要小于等于多个消费者线程中消费的最小的序列号，即cachedGatingSequence的值,这就是下面if判断的根据
            long wrapPoint = next - bufferSize;
            // cachedGatingSequence, gatingSequenceCache这两个变量记录着上一次获取消费者中最小的消费序列号,也就是最慢的消费者消费的位置
            long cachedGatingSequence = gatingSequenceCache.get();

            // 生产者不能继续写入，否则会覆盖消费者未消费的数据
            if (wrapPoint > cachedGatingSequence || cachedGatingSequence > current) {
                // 获取最新的消费者最小的消费序号
                long gatingSequence = Util.getMinimumSequence(gatingSequences, current);

                // 依然不能满足写入条件(写入会覆盖未消费的数据)
                if (wrapPoint > gatingSequence) {
                    // 锁一会，结束本次循环，重来
                    LockSupport.parkNanos(1); // TODO, should we spin based on the wait strategy?
                    continue;
                }

                // 缓存一下消费者中最小的消费序列号
                gatingSequenceCache.set(gatingSequence);
            } else if (cursor.compareAndSet(current, next)) {
                // 满足消费条件，有空余的空间让生产者写入，使用CAS算法，成功则跳出本次循环，不成功则重来
                break;
            }
        } while (true);

        return next;
    }

    /**
     * 不阻塞，获取失败，抛异常
     * @see Sequencer#tryNext()
     */
    @Override
    public long tryNext() throws InsufficientCapacityException {
        return tryNext(1);
    }

    /**
     * 不阻塞，获取失败，抛异常
     * @see Sequencer#tryNext(int)
     */
    @Override
    public long tryNext(int n) throws InsufficientCapacityException {
        if (n < 1) {
            throw new IllegalArgumentException("n must be > 0");
        }

        long current;
        long next;

        //尝试获取一次，若不成功，则抛InsufficientCapacityException
        do {
            current = cursor.get();
            next = current + n;

            if (!hasAvailableCapacity(gatingSequences, n, current)) {
                throw InsufficientCapacityException.INSTANCE;
            }
        } while (!cursor.compareAndSet(current, next));

        return next;
    }

    /**
     * @see Sequencer#remainingCapacity()
     */
    @Override
    public long remainingCapacity() {
        long consumed = Util.getMinimumSequence(gatingSequences, cursor.get());
        long produced = cursor.get();
        return getBufferSize() - (produced - consumed);
    }

    /**
     * 初始化int[]值为-1，代表圈数
     */
    private void initialiseAvailableBuffer() {
        for (int i = availableBuffer.length - 1; i != 0; i--) {
            setAvailableBufferValue(i, -1);
        }

        setAvailableBufferValue(0, -1);
    }

    /**
     * @see Sequencer#publish(long)
     */
    @Override
    public void publish(final long sequence) {
        //记录seq所在槽的圈数
        setAvailable(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * @see Sequencer#publish(long, long)
     */
    @Override
    public void publish(long lo, long hi) {
        for (long l = lo; l <= hi; l++) {
            setAvailable(l);
        }
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * 发布某个sequence之前的都可以被消费了需要将availableBuffer上对应sequence下标的值设置为第几次用到这个槽
     *
     * The below methods work on the availableBuffer flag.
     * <p>
     * The prime reason is to avoid a shared sequence object between publisher threads.
     * (Keeping single pointers tracking start and end would require coordination
     * between the threads).
     * <p>
     * --  Firstly we have the constraint that the delta between the cursor and minimum
     * gating sequence will never be larger than the buffer size (the code in
     * next/tryNext in the Sequence takes care of that).
     * -- Given that; take the sequence value and mask off the lower portion of the
     * sequence as the index into the buffer (indexMask). (aka modulo operator)
     * -- The upper portion of the sequence becomes the value to check for availability.
     * ie: it tells us how many times around the ring buffer we've been (aka division)
     * -- Because we can't wrap without the gating sequences moving forward (i.e. the
     * minimum gating sequence is effectively our last available position in the
     * buffer), when we have new data and successfully claimed a slot we can simply
     * write over the top.
     */
    private void setAvailable(final long sequence) {
        setAvailableBufferValue(calculateIndex(sequence), calculateAvailabilityFlag(sequence));
    }

    /**
     * 按内存地址位置指定int[]元素的值，值为圈数
     *     index：槽位，flag：圈数
     */
    private void setAvailableBufferValue(int index, int flag) {
        //计算槽位对应的内存地址
        long bufferAddress = (index * SCALE) + BASE;
        //设置availableBuffer中偏移量（内存地址）为bufferAddress的值
        UNSAFE.putOrderedInt(availableBuffer, bufferAddress, flag);
    }

    /**
     * 是否可用：seq计算出的圈数是否已被设置到availableBuffer
     * @see Sequencer#isAvailable(long)
     */
    @Override
    public boolean isAvailable(long sequence) {
        //槽位
        int index = calculateIndex(sequence);
        //圈数
        int flag = calculateAvailabilityFlag(sequence);
        long bufferAddress = (index * SCALE) + BASE;
        return UNSAFE.getIntVolatile(availableBuffer, bufferAddress) == flag;
    }

    /**
     * lowerBound 到 availableSequence之间 最大可用seq
     */
    @Override
    public long getHighestPublishedSequence(long lowerBound, long availableSequence) {
        for (long sequence = lowerBound; sequence <= availableSequence; sequence++) {
            if (!isAvailable(sequence)) {
                return sequence - 1;
            }
        }

        return availableSequence;
    }

    /**
     * 某个sequence右移indexShift，代表这个Sequence是第几次用到这个ringBuffer的某个槽，也就是这个sequence转了多少圈
     */
    private int calculateAvailabilityFlag(final long sequence) {
        //圈数
        return (int) (sequence >>> indexShift);
    }

    /**
     * 定位ringBuffer上某个槽用于生产event，对2^n取模 = 对2^n -1
     */
    private int calculateIndex(final long sequence) {
        //槽位
        return ((int) sequence) & indexMask;
    }
}
