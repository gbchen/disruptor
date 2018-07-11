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
package com.lmax.disruptor.event.processor;

import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.EventReleaseAware;
import com.lmax.disruptor.EventReleaser;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.Sequencer;
import com.lmax.disruptor.TimeoutHandler;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;
import com.lmax.disruptor.exception.AlertException;
import com.lmax.disruptor.exception.TimeoutException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>A {@link WorkProcessor} wraps a single {@link WorkHandler}, effectively consuming the sequence
 * and ensuring appropriate barriers.</p>
 *
 * <p>Generally, this will be used as part of a {@link WorkerPool}.</p>
 *
 * @param <T> event implementation storing the details for the work to processed.
 */
public final class WorkProcessor<T> implements EventProcessor {

    private final AtomicBoolean               running       = new AtomicBoolean(false);
    /** 当前处理到的seq */
    private final Sequence                    sequence      = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private final RingBuffer<T>               ringBuffer;
    private final SequenceBarrier             sequenceBarrier;
    private final WorkHandler<? super T>      workHandler;
    private final ExceptionHandler<? super T> exceptionHandler;
    /** 多线程共享，上一次处理的事件的seq */
    private final Sequence                    workSequence;

    private final EventReleaser               eventReleaser = new EventReleaser() {

                                                                @Override
                                                                public void release() {
                                                                    sequence.set(Long.MAX_VALUE);
                                                                }
                                                            };

    private final TimeoutHandler              timeoutHandler;

    /**
     * Construct a {@link WorkProcessor}.
     *
     * @param ringBuffer       to which events are published.
     * @param sequenceBarrier  on which it is waiting.
     * @param workHandler      is the delegate to which events are dispatched.
     * @param exceptionHandler to be called back when an error occurs
     * @param workSequence     from which to claim the next event to be worked on.  It should always be initialised
     *                         as {@link Sequencer#INITIAL_CURSOR_VALUE}
     */
    public WorkProcessor(final RingBuffer<T> ringBuffer, final SequenceBarrier sequenceBarrier,
                         final WorkHandler<? super T> workHandler, final ExceptionHandler<? super T> exceptionHandler,
                         final Sequence workSequence) {
        this.ringBuffer = ringBuffer;
        this.sequenceBarrier = sequenceBarrier;
        this.workHandler = workHandler;
        this.exceptionHandler = exceptionHandler;
        this.workSequence = workSequence;

        if (this.workHandler instanceof EventReleaseAware) {
            ((EventReleaseAware) this.workHandler).setEventReleaser(eventReleaser);
        }

        timeoutHandler = (workHandler instanceof TimeoutHandler) ? (TimeoutHandler) workHandler : null;
    }

    @Override
    public Sequence getSequence() {
        return sequence;
    }

    @Override
    public void halt() {
        running.set(false);
        //通知阻塞等待的消费者，sequenceBarrier.waitFor中checkAlert会抛异常，中断线程
        sequenceBarrier.alert();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * It is ok to have another thread re-run this method after a halt().
     *
     * @throws IllegalStateException if this processor is already running
     */
    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Thread is already running");
        }
        sequenceBarrier.clearAlert();

        notifyStart();

        // 标志位，用来标志一次消费过程
        boolean processedSequence = true;
        // 用来缓存消费者可以使用的RingBuffer最大序列号
        long cachedAvailableSequence = Long.MIN_VALUE;
        // 记录被分配的WorkSequence的序列号，也是去RingBuffer取数据的序列号
        long nextSequence = sequence.get();
        T event = null;
        while (true) {
            try {
                // if previous sequence was processed - fetch the next sequence and set
                // that we have successfully processed the previous sequence
                // typically, this will be true
                // this prevents the sequence getting too far forward if an exception
                // is thrown from the WorkHandler
                // 上一事件是否处理完成，是的话，进入新事件处理流程
                if (processedSequence) {
                    processedSequence = false;
                    // 使用CAS算法从WorkPool的序列WorkSequence取得可用序列nextSequence，保证多线程下多个消费者不会消费到同一个event
                    do {
                        // workSequence 是消费者消费到的位置
                        // 获得下一要处理的seq
                        nextSequence = workSequence.get() + 1L;
                        // 更新当前已处理到的seq为请求的seq-1
                        sequence.set(nextSequence - 1L);
                        // 尝试cas下一workSequence = workSequence+1直到成功
                    } while (!workSequence.compareAndSet(nextSequence - 1L, nextSequence));
                }

                // 如果可使用的最大序列号cachedAvaliableSequence大于等于我们要使用的序列号nextSequence，直接从RingBuffer取数据；不然进入else
                // 请求的seq是否小于缓存的最大可用seq，是的话获得请求的事件，进行处理，处理完成后，processedSequence标记为true
                if (cachedAvailableSequence >= nextSequence) {
                    // 可以满足消费的条件，根据序列号去RingBuffer去读取数据
                    event = ringBuffer.get(nextSequence);
                    workHandler.onEvent(event);
                    // 一次消费结束，设置标志位
                    processedSequence = true;
                } else {// 等待生产者生产，获取到最大的可以使用的序列号
                    // 否则，阻塞等待当前可用的最大seq，并更新cache，继续下一循环
                    cachedAvailableSequence = sequenceBarrier.waitFor(nextSequence);
                }
            } catch (final TimeoutException e) {
                notifyTimeout(sequence.get());
            } catch (final AlertException ex) {
                if (!running.get()) {
                    break;
                }
            } catch (final Throwable ex) {
                // 预留的异常处理接口
                exceptionHandler.handleEventException(ex, nextSequence, event);
                processedSequence = true;
            }
        }

        notifyShutdown();

        running.set(false);
    }

    private void notifyTimeout(final long availableSequence) {
        try {
            if (timeoutHandler != null) {
                timeoutHandler.onTimeout(availableSequence);
            }
        } catch (Throwable e) {
            exceptionHandler.handleEventException(e, availableSequence, null);
        }
    }

    private void notifyStart() {
        if (workHandler instanceof LifecycleAware) {
            try {
                ((LifecycleAware) workHandler).onStart();
            } catch (final Throwable ex) {
                exceptionHandler.handleOnStartException(ex);
            }
        }
    }

    private void notifyShutdown() {
        if (workHandler instanceof LifecycleAware) {
            try {
                ((LifecycleAware) workHandler).onShutdown();
            } catch (final Throwable ex) {
                exceptionHandler.handleOnShutdownException(ex);
            }
        }
    }
}
