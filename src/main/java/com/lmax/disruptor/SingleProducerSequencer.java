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

import com.lmax.disruptor.util.Util;

abstract class SingleProducerSequencerPad extends AbstractSequencer {

    protected long p1, p2, p3, p4, p5, p6, p7;

    SingleProducerSequencerPad(int bufferSize, WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
    }
}

abstract class SingleProducerSequencerFields extends SingleProducerSequencerPad {

    SingleProducerSequencerFields(int bufferSize, WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
    }

    /**
     * Set to -1 as sequence starting point
     */
    long nextValue   = Sequence.INITIAL_VALUE;
    long cachedValue = Sequence.INITIAL_VALUE;
}

/**
 * <p>Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.
 * Not safe for use from multiple threads as it does not implement any barriers.</p>
 *
 * <p>* Note on {@link Sequencer#getCursor()}:  With this sequencer the cursor value is updated after the call
 * to {@link Sequencer#publish(long)} is made.</p>
 *
 *         RingBuffer持有Sequencer.
 *         SingleProducerSequencer有nextValue和cachedValue两个成员变量:
 *           nextValue记录生产者生产到的位置.
 *           cachedValue记录消费者线程中序列号最小的序列号，即是在最后面的消费者的序号.
 */

public final class SingleProducerSequencer extends SingleProducerSequencerFields {

    protected long p1, p2, p3, p4, p5, p6, p7;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize   the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public SingleProducerSequencer(int bufferSize, WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
    }

    /**
     * @see Sequencer#hasAvailableCapacity(int)
     */
    @Override
    public boolean hasAvailableCapacity(int requiredCapacity) {
        return hasAvailableCapacity(requiredCapacity, false);
    }

    private boolean hasAvailableCapacity(int requiredCapacity, boolean doStore) {
        long nextValue = this.nextValue;

        long wrapPoint = (nextValue + requiredCapacity) - bufferSize;
        long cachedGatingSequence = this.cachedValue;

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {
            if (doStore) {
                cursor.setVolatile(nextValue); // StoreLoad fence
            }

            long minSequence = Util.getMinimumSequence(gatingSequences, nextValue);
            this.cachedValue = minSequence;

            if (wrapPoint > minSequence) {
                return false;
            }
        }

        return true;
    }

    /**
     * @see Sequencer#next()
     */
    @Override
    public long next() {
        return next(1);
    }

    /**
     * @see Sequencer#next(int)
     */
    @Override
    public long next(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be > 0");
        }

        //该生产者发布的最大序列号
        long nextValue = this.nextValue;

        //记录下一个要生产的序号，欲发布的序列号
        long nextSequence = nextValue + n;

        //覆盖点，即该生产者如果发布了这次的序列号，那它最终会落在哪个位置
        //得到下一个要生产的序号对应的位置
        //wrapPoint是一个很关键的变量，这个变量决定生产者是否可以覆盖序列号nextSequence，
        //wrapPoint为什么是nextSequence - bufferSize：
        // RingBuffer表现出来的是一个环形的数据结构，实际上是一个长度为bufferSize的数组，
        // 如果nextSequence小于bufferSize,wrapPoint是负数，表示可以一直生产(就是刚开始的情况，队列为空的，从未放过数据)。
        // 如果nextSequence大于bufferSize,wrapPoint是一个大于0的数，由于生产者和消费者的序列号差距不能超过bufferSize（超过bufferSize会覆盖消费者未消费的数据），
        // wrapPoint要小于等于多个消费者线程中消费的最小的序列号，即cachedValue的值,这就是下面if判断的根据
        long wrapPoint = nextSequence - bufferSize;

        //cachedValue记录消费者线程中序列号最小的序列号，即是在最后面的消费者的序号
        //TODO 所有消费者中消费得最慢那个的前一个序列号??
        long cachedGatingSequence = this.cachedValue;

        //这里两个判断条件：
        // 一是看生产者生产是不是超过了消费者，所以判断的是覆盖点是否超过了最慢消费者；
        // 二是看消费者是否超过了当前生产者的最大序号，判断的是消费者是不是比生产者还快这种异常情况
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {
            cursor.setVolatile(nextValue); // StoreLoad fence

            long minSequence;

            //判断覆盖点wrapPoint是否大于消费者线程最小的序列号，如果大于，不能写入，继续等待
            //覆盖点是不是已经超过了最慢消费者和当前生产者序列号的最小者
            //这两个有点难理解，实际上就是覆盖点不能超过最慢那个生产者，也不能超过当前自身，
            //比如一次发布超过bufferSize），gatingSequences的处理也是类似算术处理，也可以看成是相对于原点是正还是负
            while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue))) {
                LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
            }

            //满足生产条件了，缓存这次消费者线程最小消费序号，供下次使用
            this.cachedValue = minSequence;
        }

        //缓存生产者最大生产序列号
        //把当前序列号更新为欲发布序列号
        this.nextValue = nextSequence;

        return nextSequence;
    }

    /**
     * @see Sequencer#tryNext()
     */
    @Override
    public long tryNext() throws InsufficientCapacityException {
        return tryNext(1);
    }

    /**
     * @see Sequencer#tryNext(int)
     */
    @Override
    public long tryNext(int n) throws InsufficientCapacityException {
        if (n < 1) {
            throw new IllegalArgumentException("n must be > 0");
        }

        if (!hasAvailableCapacity(n, true)) {
            throw InsufficientCapacityException.INSTANCE;
        }

        long nextSequence = this.nextValue += n;

        return nextSequence;
    }

    /**
     * @see Sequencer#remainingCapacity()
     */
    @Override
    public long remainingCapacity() {
        long nextValue = this.nextValue;

        long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
        long produced = nextValue;
        return getBufferSize() - (produced - consumed);
    }

    /**
     * @see Sequencer#claim(long)
     */
    @Override
    public void claim(long sequence) {
        this.nextValue = sequence;
    }

    /**
     * @see Sequencer#publish(long)
     */
    @Override
    public void publish(long sequence) {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * @see Sequencer#publish(long, long)
     */
    @Override
    public void publish(long lo, long hi) {
        publish(hi);
    }

    /**
     * @see Sequencer#isAvailable(long)
     */
    @Override
    public boolean isAvailable(long sequence) {
        return sequence <= cursor.get();
    }

    @Override
    public long getHighestPublishedSequence(long lowerBound, long availableSequence) {
        return availableSequence;
    }
}
