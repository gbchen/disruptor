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

import com.lmax.disruptor.event.processor.WorkProcessor;
import com.lmax.disruptor.util.Util;
import com.lmax.disruptor.waitstrategy.BlockingWaitStrategy;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WorkerPool contains a pool of {@link WorkProcessor}s that will consume sequences so jobs can be farmed out across a pool of workers.
 * Each of the {@link WorkProcessor}s manage and calls a {@link WorkHandler} to process the events.
 *
 * @param <T> event to be processed by a pool of workers
 */
public final class WorkerPool<T> {
    
    private final AtomicBoolean      started      = new AtomicBoolean(false);
    /**多个Processor共用一个处理的seq */
    private final Sequence           workSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private final RingBuffer<T>      ringBuffer;

    // WorkProcessors are created to wrap each of the provided WorkHandlers
    /**处理线程组*/
    private final WorkProcessor<?>[] workProcessors;

    /**
     * Create a worker pool to enable an array of {@link WorkHandler}s to consume published sequences.
     * <p>
     * This option requires a pre-configured {@link RingBuffer} which must have {@link RingBuffer#addGatingSequences(Sequence...)}
     * called before the work pool is started.
     *
     * @param ringBuffer       of events to be consumed.
     * @param sequenceBarrier  on which the workers will depend.
     * @param exceptionHandler to callback when an error occurs which is not handled by the {@link WorkHandler}s.
     * @param workHandlers     to distribute the work load across.
     */
    @SafeVarargs
    public WorkerPool(final RingBuffer<T> ringBuffer, final SequenceBarrier sequenceBarrier,
                      final ExceptionHandler<? super T> exceptionHandler, final WorkHandler<? super T>... workHandlers) {
        this.ringBuffer = ringBuffer;
        final int numWorkers = workHandlers.length;
        //一个handler一个线程
        workProcessors = new WorkProcessor[numWorkers];

        for (int i = 0; i < numWorkers; i++) {
            workProcessors[i] = new WorkProcessor<>(ringBuffer, sequenceBarrier, workHandlers[i], exceptionHandler, workSequence);
        }
    }

    /**
     * Construct a work pool with an internal {@link RingBuffer} for convenience.
     * <p>
     * This option does not require {@link RingBuffer#addGatingSequences(Sequence...)} to be called before the work pool is started.
     *
     * @param eventFactory     for filling the {@link RingBuffer}
     * @param exceptionHandler to callback when an error occurs which is not handled by the {@link WorkHandler}s.
     * @param workHandlers     to distribute the work load across.
     */
    @SafeVarargs
    public WorkerPool(final EventFactory<T> eventFactory, final ExceptionHandler<? super T> exceptionHandler,
                      final WorkHandler<? super T>... workHandlers) {
        ringBuffer = RingBuffer.createMultiProducer(eventFactory, 1024, new BlockingWaitStrategy());
        final SequenceBarrier barrier = ringBuffer.newBarrier();
        final int numWorkers = workHandlers.length;
        //创建一个和workHandlers个数相同的WorkProcessor数组,也就是线程数
        workProcessors = new WorkProcessor[numWorkers];

        for (int i = 0; i < numWorkers; i++) {
            workProcessors[i] = new WorkProcessor<>(ringBuffer, barrier, workHandlers[i], exceptionHandler, workSequence);
        }

        //添加消费者seq
        ringBuffer.addGatingSequences(getWorkerSequences());
    }

    /**
     * 每个Processor消费到的Sequence加上workSequence，共同作为消费者seq
     * Get an array of {@link Sequence}s representing the progress of the workers.
     *
     * @return an array of {@link Sequence}s representing the progress of the workers.
     */
    public Sequence[] getWorkerSequences() {
        final Sequence[] sequences = new Sequence[workProcessors.length + 1];
        for (int i = 0, size = workProcessors.length; i < size; i++) {
            sequences[i] = workProcessors[i].getSequence();
        }
        sequences[sequences.length - 1] = workSequence;

        return sequences;
    }

    /**
     * 根据一系列的引用，找到消费者程序WorkProcessor，初始化每个WorkProcessor的sequence，然后执行提交到线程池执行
     * Start the worker pool processing events in sequence.
     *
     * @param executor providing threads for running the workers.
     * @return the {@link RingBuffer} used for the work queue.
     * @throws IllegalStateException if the pool has already been started and not halted yet
     */
    public RingBuffer<T> start(final Executor executor) {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("WorkerPool has already been started and cannot be restarted until halted.");
        }

        //生产者当前生产到的seq
        final long cursor = ringBuffer.getCursor();
        //从生产者当前生产到的seq开始消费，作为workSeq
        workSequence.set(cursor);

        for (WorkProcessor<?> processor : workProcessors) {
            //遍历processor，设置workSeq
            processor.getSequence().set(cursor);
            //放入线程池
            executor.execute(processor);
        }

        return ringBuffer;
    }

    /**
     * 等待消费者处理完，然后暂停
     * Wait for the {@link RingBuffer} to drain of published events then halt the workers.
     */
    public void drainAndHalt() {
        //获得消费者消费到的seq
        Sequence[] workerSequences = getWorkerSequences();
        //如果生产者覆盖了消费者未消费的seq，则等待
        while (ringBuffer.getCursor() > Util.getMinimumSequence(workerSequences)) {
            Thread.yield();
        }

        for (WorkProcessor<?> processor : workProcessors) {
            //消费者中断阻塞
            processor.halt();
        }

        started.set(false);
    }

    /**
     * Halt all workers immediately at the end of their current cycle.
     */
    public void halt() {
        for (WorkProcessor<?> processor : workProcessors) {
            //消费者中断阻塞
            processor.halt();
        }

        //停止线程池
        started.set(false);
    }

    public boolean isRunning() {
        return started.get();
    }
}
