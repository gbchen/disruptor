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
package com.lmax.disruptor.dsl;

import com.lmax.disruptor.event.processor.BatchEventProcessor;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.event.translator.EventTranslator;
import com.lmax.disruptor.event.translator.EventTranslatorOneArg;
import com.lmax.disruptor.event.translator.EventTranslatorThreeArg;
import com.lmax.disruptor.event.translator.EventTranslatorTwoArg;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.exception.TimeoutException;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;
import com.lmax.disruptor.util.Util;
import com.lmax.disruptor.waitstrategy.BlockingWaitStrategy;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实际上就是整个基于ringBuffer实现的生产者消费者模式的容器。
 *
 * <p>A DSL-style API for setting up the disruptor pattern around a ring buffer
 * (aka the Builder pattern).</p>
 *
 * <p>A simple example of setting up the disruptor with two event handlers that
 * must process events in order:</p>
 * <pre>
 * <code>Disruptor&lt;MyEvent&gt; disruptor = new Disruptor&lt;MyEvent&gt;(MyEvent.FACTORY, 32, Executors.newCachedThreadPool());
 * EventHandler&lt;MyEvent&gt; handler1 = new EventHandler&lt;MyEvent&gt;() { ... };
 * EventHandler&lt;MyEvent&gt; handler2 = new EventHandler&lt;MyEvent&gt;() { ... };
 * disruptor.handleEventsWith(handler1);
 * disruptor.after(handler1).handleEventsWith(handler2);
 *
 * RingBuffer ringBuffer = disruptor.start();</code>
 * </pre>
 *
 * @param <T> the type of event used.
 */
public class Disruptor<T> {

    private final RingBuffer<T>         ringBuffer;
    private final Executor              executor;
    private final ConsumerRepository<T> consumerRepository = new ConsumerRepository<>();

    /**
     * Disruptor 运行状态
     */
    private final AtomicBoolean         started            = new AtomicBoolean(false);
    private ExceptionHandler<? super T> exceptionHandler   = new ExceptionHandlerWrapper<>();

    /**
     * Create a new Disruptor. Will default to {@link BlockingWaitStrategy} and
     * {@link ProducerType}.MULTI
     *
     * @deprecated Use a {@link ThreadFactory} instead of an {@link Executor} as a the ThreadFactory
     * is able to report errors when it is unable to construct a thread to run a producer.
     *
     * @param eventFactory   the factory to create events in the ring buffer.
     * @param ringBufferSize the size of the ring buffer.
     * @param executor       an {@link Executor} to execute event processors.
     */
    @Deprecated
    public Disruptor(final EventFactory<T> eventFactory, final int ringBufferSize, final Executor executor) {
        this(RingBuffer.createMultiProducer(eventFactory, ringBufferSize), executor);
    }

    /**
     * Create a new Disruptor.
     *
     * @deprecated Use a {@link ThreadFactory} instead of an {@link Executor} as a the ThreadFactory
     * is able to report errors when it is unable to construct a thread to run a producer.
     *
     * @param eventFactory   the factory to create events in the ring buffer.
     * @param ringBufferSize the size of the ring buffer, must be power of 2.
     * @param executor       an {@link Executor} to execute event processors.
     * @param producerType   the claim strategy to use for the ring buffer.
     * @param waitStrategy   the wait strategy to use for the ring buffer.
     */
    @Deprecated
    public Disruptor(final EventFactory<T> eventFactory, final int ringBufferSize, final Executor executor,
                     final ProducerType producerType, final WaitStrategy waitStrategy) {
        this(RingBuffer.create(producerType, eventFactory, ringBufferSize, waitStrategy), executor);
    }

    /**
     * Create a new Disruptor. Will default to {@link BlockingWaitStrategy} and
     * {@link ProducerType}.MULTI
     *
     * @param eventFactory   the factory to create events in the ring buffer.
     * @param ringBufferSize the size of the ring buffer.
     * @param threadFactory  a {@link ThreadFactory} to create threads to for processors.
     */
    public Disruptor(final EventFactory<T> eventFactory, final int ringBufferSize, final ThreadFactory threadFactory) {
        this(RingBuffer.createMultiProducer(eventFactory, ringBufferSize), new BasicExecutor(threadFactory));
    }

    /**
     * Create a new Disruptor.
     *
     * @param eventFactory   the factory to create events in the ring buffer.
     * @param ringBufferSize the size of the ring buffer, must be power of 2.
     * @param threadFactory  a {@link ThreadFactory} to create threads for processors.
     * @param producerType   the claim strategy to use for the ring buffer.
     * @param waitStrategy   the wait strategy to use for the ring buffer.
     */
    public Disruptor(final EventFactory<T> eventFactory, final int ringBufferSize, final ThreadFactory threadFactory,
                     final ProducerType producerType, final WaitStrategy waitStrategy) {
        this(RingBuffer.create(producerType, eventFactory, ringBufferSize, waitStrategy), new BasicExecutor(threadFactory));
    }

    /**
     * Private constructor helper
     */
    private Disruptor(final RingBuffer<T> ringBuffer, final Executor executor) {
        this.ringBuffer = ringBuffer;
        this.executor = executor;
    }

    /**
     * 添加消费者，前置消费者默认为空=生产者cursor
     *
     * <p>Set up event handlers to handle events from the ring buffer. These handlers will process events
     * as soon as they become available, in parallel.</p>
     *
     * <p>This method can be used as the start of a chain. For example if the handler <code>A</code> must
     * process events before handler <code>B</code>:</p>
     * <pre><code>dw.handleEventsWith(A).then(B);</code></pre>
     *
     * <p>This call is additive, but generally should only be called once when setting up the Disruptor instance</p>
     *
     * @param handlers the event handlers that will process events.
     * @return a {@link EventHandlerGroup} that can be used to chain dependencies.
     */
    @SuppressWarnings("varargs")
    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWith(final EventHandler<? super T>... handlers) {
        return createEventProcessors(new Sequence[0], handlers);
    }
    /**   new Sequence[0]
     * int[] zero = new int[0];
     * int[] nil = null;
     *  zero是一个长度为0的数组，我们称之为“空数组”，空数组也是一个对象，只是包含元素个数为0。
     *  nil是一个数组类型的空引用。
     *  假设一个方法返回一个数组，如果它返回null，则调用方法必须先判断是否返回null，才能对放回数组进一步处理，而如果返回空数组，则无须null引用检查。
     *  鉴于此，返回数组的方法在没有结果时我们通常返回空数组，而不是null，这样做对于函数调用者的处理比较方便。
     */

    /**
     * 添加消费者，前置消费者默认为空=生产者cursor
     *
     * <p>Set up custom event processors to handle events from the ring buffer. The Disruptor will
     * automatically start these processors when {@link #start()} is called.</p>
     *
     * <p>This method can be used as the start of a chain. For example if the handler <code>A</code> must
     * process events before handler <code>B</code>:</p>
     * <pre><code>dw.handleEventsWith(A).then(B);</code></pre>
     *
     * <p>Since this is the start of the chain, the processor factories will always be passed an empty <code>Sequence</code>
     * array, so the factory isn't necessary in this case. This method is provided for consistency with
     * {@link EventHandlerGroup#handleEventsWith(EventProcessorFactory...)} and {@link EventHandlerGroup#then(EventProcessorFactory...)}
     * which do have barrier sequences to provide.</p>
     *
     * <p>This call is additive, but generally should only be called once when setting up the Disruptor instance</p>
     *
     * @param eventProcessorFactories the event processor factories to use to create the event processors that will process events.
     * @return a {@link EventHandlerGroup} that can be used to chain dependencies.
     */
    @SafeVarargs
    public final EventHandlerGroup<T> handleEventsWith(final EventProcessorFactory<T>... eventProcessorFactories) {
        final Sequence[] barrierSequences = new Sequence[0];
        return createEventProcessors(barrierSequences, eventProcessorFactories);
    }

    /**
     * 添加消费者，前置消费者默认为空=生产者cursor
     *
     * <p>Set up custom event processors to handle events from the ring buffer. The Disruptor will
     * automatically start this processors when {@link #start()} is called.</p>
     *
     * <p>This method can be used as the start of a chain. For example if the processor <code>A</code> must
     * process events before handler <code>B</code>:</p>
     * <pre><code>dw.handleEventsWith(A).then(B);</code></pre>
     *
     * @param processors the event processors that will process events.
     * @return a {@link EventHandlerGroup} that can be used to chain dependencies.
     */
    public EventHandlerGroup<T> handleEventsWith(final EventProcessor... processors) {
        for (final EventProcessor processor : processors) {
            consumerRepository.add(processor);
        }

        final Sequence[] sequences = new Sequence[processors.length];
        for (int i = 0; i < processors.length; i++) {
            sequences[i] = processors[i].getSequence();
        }

        ringBuffer.addGatingSequences(sequences);

        return new EventHandlerGroup<>(this, consumerRepository, Util.getSequencesFor(processors));
    }


    /**
     * 创建多线程消费者
     *
     * Set up a {@link WorkerPool} to distribute an event to one of a pool of work handler threads.
     * Each event will only be processed by one of the work handlers.
     * The Disruptor will automatically start this processors when {@link #start()} is called.
     *
     * @param workHandlers the work handlers that will process events.
     * @return a {@link EventHandlerGroup} that can be used to chain dependencies.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final EventHandlerGroup<T> handleEventsWithWorkerPool(final WorkHandler<T>... workHandlers) {
        return createWorkerPool(new Sequence[0], workHandlers);
    }

    /**
     * <p>Specify an exception handler to be used for any future event handlers.</p>
     *
     * <p>Note that only event handlers set up after calling this method will use the exception handler.</p>
     *
     * @param exceptionHandler the exception handler to use for any future {@link EventProcessor}.
     * @deprecated This method only applies to future event handlers. Use setDefaultExceptionHandler instead which applies to existing and new event handlers.
     */
    public void handleExceptionsWith(final ExceptionHandler<? super T> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * <p>Specify an exception handler to be used for event handlers and worker pools created by this Disruptor.</p>
     *
     * <p>The exception handler will be used by existing and future event handlers and worker pools created by this Disruptor instance.</p>
     *
     * @param exceptionHandler the exception handler to use.
     */
    @SuppressWarnings("unchecked")
    public void setDefaultExceptionHandler(final ExceptionHandler<? super T> exceptionHandler) {
        checkNotStarted();
        if (!(this.exceptionHandler instanceof ExceptionHandlerWrapper)) {
            throw new IllegalStateException("setDefaultExceptionHandler can not be used after handleExceptionsWith");
        }
        ((ExceptionHandlerWrapper<T>) this.exceptionHandler).switchTo(exceptionHandler);
    }

    /**
     * Override the default exception handler for a specific handler.
     * <pre>disruptorWizard.handleExceptionsIn(eventHandler).with(exceptionHandler);</pre>
     *
     * @param eventHandler the event handler to set a different exception handler for.
     * @return an ExceptionHandlerSetting dsl object - intended to be used by chaining the with method call.
     */
    public ExceptionHandlerSetting<T> handleExceptionsFor(final EventHandler<T> eventHandler) {
        return new ExceptionHandlerSetting<>(eventHandler, consumerRepository);
    }

    /**
     * <p>Create a group of event handlers to be used as a dependency.
     * For example if the handler <code>A</code> must process events before handler <code>B</code>:</p>
     *
     * <pre><code>dw.after(A).handleEventsWith(B);</code></pre>
     *
     * @param handlers the event handlers, previously set up with {@link #handleEventsWith(com.lmax.disruptor.EventHandler[])},
     *                 that will form the barrier for subsequent handlers or processors.
     * @return an {@link EventHandlerGroup} that can be used to setup a dependency barrier over the specified event handlers.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final EventHandlerGroup<T> after(final EventHandler<T>... handlers) {
        final Sequence[] sequences = new Sequence[handlers.length];
        for (int i = 0, handlersLength = handlers.length; i < handlersLength; i++) {
            sequences[i] = consumerRepository.getSequenceFor(handlers[i]);
        }

        return new EventHandlerGroup<>(this, consumerRepository, sequences);
    }

    /**
     * Create a group of event processors to be used as a dependency.
     *
     * @param processors the event processors, previously set up with {@link #handleEventsWith(com.lmax.disruptor.EventProcessor...)},
     *                   that will form the barrier for subsequent handlers or processors.
     * @return an {@link EventHandlerGroup} that can be used to setup a {@link SequenceBarrier} over the specified event processors.
     * @see #after(com.lmax.disruptor.EventHandler[])
     */
    public EventHandlerGroup<T> after(final EventProcessor... processors) {
        for (final EventProcessor processor : processors) {
            consumerRepository.add(processor);
        }

        return new EventHandlerGroup<>(this, consumerRepository, Util.getSequencesFor(processors));
    }

    /**
     * Publish an event to the ring buffer.
     *
     * @param eventTranslator the translator that will load data into the event.
     */
    public void publishEvent(final EventTranslator<T> eventTranslator) {
        ringBuffer.publishEvent(eventTranslator);
    }

    /**
     * Publish an event to the ring buffer.
     *
     * @param <A> Class of the user supplied argument.
     * @param eventTranslator the translator that will load data into the event.
     * @param arg             A single argument to load into the event
     */
    public <A> void publishEvent(final EventTranslatorOneArg<T, A> eventTranslator, final A arg) {
        ringBuffer.publishEvent(eventTranslator, arg);
    }

    /**
     * Publish a batch of events to the ring buffer.
     *
     * @param <A> Class of the user supplied argument.
     * @param eventTranslator the translator that will load data into the event.
     * @param arg             An array single arguments to load into the events. One Per event.
     */
    public <A> void publishEvents(final EventTranslatorOneArg<T, A> eventTranslator, final A[] arg) {
        ringBuffer.publishEvents(eventTranslator, arg);
    }

    /**
     * Publish an event to the ring buffer.
     *
     * @param <A> Class of the user supplied argument.
     * @param <B> Class of the user supplied argument.
     * @param eventTranslator the translator that will load data into the event.
     * @param arg0            The first argument to load into the event
     * @param arg1            The second argument to load into the event
     */
    public <A, B> void publishEvent(final EventTranslatorTwoArg<T, A, B> eventTranslator, final A arg0, final B arg1) {
        ringBuffer.publishEvent(eventTranslator, arg0, arg1);
    }

    /**
     * Publish an event to the ring buffer.
     *
     * @param eventTranslator the translator that will load data into the event.
     * @param <A> Class of the user supplied argument.
     * @param <B> Class of the user supplied argument.
     * @param <C> Class of the user supplied argument.
     * @param arg0            The first argument to load into the event
     * @param arg1            The second argument to load into the event
     * @param arg2            The third argument to load into the event
     */
    public <A, B, C> void publishEvent(final EventTranslatorThreeArg<T, A, B, C> eventTranslator, final A arg0,
                                       final B arg1, final C arg2) {
        ringBuffer.publishEvent(eventTranslator, arg0, arg1, arg2);
    }

    /**
     * <p>Starts the event processors and returns the fully configured ring buffer.</p>
     *
     * <p>The ring buffer is set up to prevent overwriting any entry that is yet to
     * be processed by the slowest event processor.</p>
     *
     * <p>This method must only be called once after all event processors have been added.</p>
     *
     * @return the configured ring buffer.
     */
    public RingBuffer<T> start() {
        //遍历消费者，启动消费者线程
        checkOnlyStartedOnce();
        for (final ConsumerInfo consumerInfo : consumerRepository) {
            consumerInfo.start(executor);
        }

        return ringBuffer;
    }

    /**
     * Calls {@link com.lmax.disruptor.EventProcessor#halt()} on all of the event processors created via this disruptor.
     */
    public void halt() {
        for (final ConsumerInfo consumerInfo : consumerRepository) {
            consumerInfo.halt();
        }
    }

    /**
     * <p>Waits until all events currently in the disruptor have been processed by all event processors
     * and then halts the processors.  It is critical that publishing to the ring buffer has stopped
     * before calling this method, otherwise it may never return.</p>
     *
     * <p>This method will not shutdown the executor, nor will it await the final termination of the
     * processor threads.</p>
     */
    public void shutdown() {
        try {
            shutdown(-1, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            exceptionHandler.handleOnShutdownException(e);
        }
    }

    /**
     * <p>Waits until all events currently in the disruptor have been processed by all event processors
     * and then halts the processors.</p>
     *
     * <p>This method will not shutdown the executor, nor will it await the final termination of the
     * processor threads.</p>
     *
     * @param timeout  the amount of time to wait for all events to be processed. <code>-1</code> will give an infinite timeout
     * @param timeUnit the unit the timeOut is specified in
     * @throws TimeoutException if a timeout occurs before shutdown completes.
     */
    public void shutdown(final long timeout, final TimeUnit timeUnit) throws TimeoutException {
        final long timeOutAt = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        while (hasBacklog()) {
            if (timeout >= 0 && System.currentTimeMillis() > timeOutAt) {
                throw TimeoutException.INSTANCE;
            }
            // Busy spin
        }
        halt();
    }

    /**
     * The {@link RingBuffer} used by this Disruptor.  This is useful for creating custom
     * event processors if the behaviour of {@link BatchEventProcessor} is not suitable.
     *
     * @return the ring buffer used by this Disruptor.
     */
    public RingBuffer<T> getRingBuffer() {
        return ringBuffer;
    }

    /**
     * Get the value of the cursor indicating the published sequence.
     *
     * @return value of the cursor for events that have been published.
     */
    public long getCursor() {
        return ringBuffer.getCursor();
    }

    /**
     * The capacity of the data structure to hold entries.
     *
     * @return the size of the RingBuffer.
     * @see com.lmax.disruptor.Sequencer#getBufferSize()
     */
    public long getBufferSize() {
        return ringBuffer.getBufferSize();
    }

    /**
     * Get the event for a given sequence in the RingBuffer.
     *
     * @param sequence for the event.
     * @return event for the sequence.
     * @see RingBuffer#get(long)
     */
    public T get(final long sequence) {
        return ringBuffer.get(sequence);
    }

    /**
     * Get the {@link SequenceBarrier} used by a specific handler. Note that the {@link SequenceBarrier}
     * may be shared by multiple event handlers.
     *
     * @param handler the handler to get the barrier for.
     * @return the SequenceBarrier used by <i>handler</i>.
     */
    public SequenceBarrier getBarrierFor(final EventHandler<T> handler) {
        return consumerRepository.getBarrierFor(handler);
    }

    /**
     * Gets the sequence value for the specified event handlers.
     *
     * @param b1 eventHandler to get the sequence for.
     * @return eventHandler's sequence
     */
    public long getSequenceValueFor(final EventHandler<T> b1) {
        return consumerRepository.getSequenceFor(b1).get();
    }

    /**
     * Confirms if all messages have been consumed by all event processors
     */
    private boolean hasBacklog() {
        final long cursor = ringBuffer.getCursor();
        for (final Sequence consumer : consumerRepository.getLastSequenceInChain(false)) {
            if (cursor > consumer.get()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 1、创建BatchEventProcessor
     * 2、更新gatingSequence：取消跟踪barrierSequences，添加跟踪每个BatchEventProcessor(eventHandler).getSequence
     * 3、new EventHandlerGroup(consumerRepository.add(batchEventProcessor),Processor.getSequence())
     */
    EventHandlerGroup<T> createEventProcessors(final Sequence[] barrierSequences, final EventHandler<? super T>[] eventHandlers) {
        checkNotStarted();
        // Sequence 保存消费者最近读过的数据位置，读过则表示此位置可被生产者写入
        final Sequence[] processorSequences = new Sequence[eventHandlers.length];
        // 消费者从 SequenceBarrier 获取下一个可消费数据，多组消费者使用同一个 SequenceBarrier
        final SequenceBarrier barrier = ringBuffer.newBarrier(barrierSequences);

        // 这里多个 eventHandler 表示多组消费者，同一份数据会交给所有 eventHandler 处理
        for (int i = 0, eventHandlersLength = eventHandlers.length; i < eventHandlersLength; i++) {
            final EventHandler<? super T> eventHandler = eventHandlers[i];

            final BatchEventProcessor<T> batchEventProcessor = new BatchEventProcessor<>(ringBuffer, barrier, eventHandler);

            if (exceptionHandler != null) {
                batchEventProcessor.setExceptionHandler(exceptionHandler);
            }

            //添加到消费者仓库
            consumerRepository.add(batchEventProcessor, eventHandler, barrier);
            //获得processor记录的消费到的seq对象
            processorSequences[i] = batchEventProcessor.getSequence();
        }
        //更新Sequencer监听的消费者seq为最后一个消费者的seq，同时删除前置消费者的seq不再监听，修改前置消费者seq不为链上最后消费者seq
        updateGatingSequencesForNextInChain(barrierSequences, processorSequences);

        return new EventHandlerGroup<>(this, consumerRepository, processorSequences);
    }

    /**只需保证最慢的消费者seq不被生产者覆盖，前置消费者的seq不需要监听 */
    private void updateGatingSequencesForNextInChain(final Sequence[] barrierSequences, final Sequence[] processorSequences) {
        if (processorSequences.length > 0) {
            //seq晚于barrierSequences，只需保证最慢的消费者seq不被生产者覆盖
            ringBuffer.addGatingSequences(processorSequences);
            for (final Sequence barrierSequence : barrierSequences) {
                //只需保证最慢的消费者seq不被生产者覆盖
                ringBuffer.removeGatingSequence(barrierSequence);
            }
            //设置前置消费者seq不是链上最后一个消费者seq
            consumerRepository.unMarkEventProcessorsAsEndOfChain(barrierSequences);
        }
    }

    EventHandlerGroup<T> createEventProcessors(final Sequence[] barrierSequences,
                                               final EventProcessorFactory<T>[] processorFactories) {
        final EventProcessor[] eventProcessors = new EventProcessor[processorFactories.length];
        for (int i = 0; i < processorFactories.length; i++) {
            eventProcessors[i] = processorFactories[i].createEventProcessor(ringBuffer, barrierSequences);
        }

        return handleEventsWith(eventProcessors);
    }

    EventHandlerGroup<T> createWorkerPool(final Sequence[] barrierSequences, final WorkHandler<? super T>[] workHandlers) {
        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier(barrierSequences);
        final WorkerPool<T> workerPool = new WorkerPool<>(ringBuffer, sequenceBarrier, exceptionHandler, workHandlers);

        consumerRepository.add(workerPool, sequenceBarrier);

        final Sequence[] workerSequences = workerPool.getWorkerSequences();

        updateGatingSequencesForNextInChain(barrierSequences, workerSequences);

        return new EventHandlerGroup<>(this, consumerRepository, workerSequences);
    }

    private void checkNotStarted() {
        if (started.get()) {
            throw new IllegalStateException("All event handlers must be added before calling starts.");
        }
    }

    /**
     * 设置启动状态位started为true，并检查Disruptor只启动了一次，防止重复启动Disruptor
     */
    private void checkOnlyStartedOnce() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Disruptor.start() must only be called once.");
        }
    }

    @Override
    public String toString() {
        return "Disruptor{" + "ringBuffer=" + ringBuffer + ", started=" + started + ", executor=" + executor + '}';
    }
}
