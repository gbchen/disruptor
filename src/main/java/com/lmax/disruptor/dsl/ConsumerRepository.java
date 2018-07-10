/*
 * Copyright 2011 LMAX Ltd. Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package com.lmax.disruptor.dsl;

import com.lmax.disruptor.*;

import java.util.*;

/**
 * Provides a repository mechanism(机制、原理) to associate {@link EventHandler}s with {@link EventProcessor}s
 *
 * @param <T> the type of the {@link EventHandler}
 */
class ConsumerRepository<T> implements Iterable<ConsumerInfo> {

    /**
     * IdentityHashMap
     *     区别与其他的键不能重复的容器，IdentityHashMap允许key值重复，
     *     但是——key必须是两个不同的对象，即对于k1和k2，当k1==k2时，IdentityHashMap认为两个key相等，
     *     而HashMap只有在k1.equals(k2) == true 时才会认为两个key相等。
     */
    private final Map<EventHandler<?>, EventProcessorInfo<T>> eventProcessorInfoByEventHandler = new IdentityHashMap<>();
    private final Map<Sequence, ConsumerInfo>                 eventProcessorInfoBySequence     = new IdentityHashMap<>();
    private final Collection<ConsumerInfo>                    consumerInfos                    = new ArrayList<>();

    public void add(final EventProcessor eventprocessor, final EventHandler<? super T> handler, final SequenceBarrier barrier) {
        final EventProcessorInfo<T> consumerInfo = new EventProcessorInfo<>(eventprocessor, handler, barrier);
        eventProcessorInfoByEventHandler.put(handler, consumerInfo);
        eventProcessorInfoBySequence.put(eventprocessor.getSequence(), consumerInfo);
        consumerInfos.add(consumerInfo);
    }

    public void add(final EventProcessor processor) {
        final EventProcessorInfo<T> consumerInfo = new EventProcessorInfo<>(processor, null, null);
        eventProcessorInfoBySequence.put(processor.getSequence(), consumerInfo);
        consumerInfos.add(consumerInfo);
    }

    public void add(final WorkerPool<T> workerPool, final SequenceBarrier sequenceBarrier) {
        final WorkerPoolInfo<T> workerPoolInfo = new WorkerPoolInfo<>(workerPool, sequenceBarrier);
        consumerInfos.add(workerPoolInfo);
        for (Sequence sequence : workerPool.getWorkerSequences()) {
            eventProcessorInfoBySequence.put(sequence, workerPoolInfo);
        }
    }

    /**
     * 获得处理链上的最后一个消费者消费到的Seqs
     */
    public Sequence[] getLastSequenceInChain(boolean includeStopped) {
        List<Sequence> lastSequence = new ArrayList<>();
        for (ConsumerInfo consumerInfo : consumerInfos) {
            //如不包含已停止的，且已停止，继续遍历直到碰到未停止的，判断是否是最后一个消费者，如果包含已停止的，则继续下面的代码,
            // 是否是最后一个消费者
            if ((includeStopped || consumerInfo.isRunning()) && consumerInfo.isEndOfChain()) {
                //获得当前处理到的seq，如果是BatchEventProcess数量为1，如果是WorkerPool则为多个：每个Processor的Seq+workSeq
                final Sequence[] sequences = consumerInfo.getSequences();
                Collections.addAll(lastSequence, sequences);
            }
        }

        return lastSequence.toArray(new Sequence[lastSequence.size()]);
    }

    public EventProcessor getEventProcessorFor(final EventHandler<T> handler) {
        final EventProcessorInfo<T> eventprocessorInfo = getEventProcessorInfo(handler);
        if (eventprocessorInfo == null) {
            throw new IllegalArgumentException("The event handler " + handler + " is not processing events.");
        }

        return eventprocessorInfo.getEventProcessor();
    }

    public Sequence getSequenceFor(final EventHandler<T> handler) {
        return getEventProcessorFor(handler).getSequence();
    }

    /**
     * 设置这些Processor不为最后一个Processor
     */
    public void unMarkEventProcessorsAsEndOfChain(final Sequence... barrierEventProcessors) {
        for (Sequence barrierEventProcessor : barrierEventProcessors) {
            getEventProcessorInfo(barrierEventProcessor).markAsUsedInBarrier();
        }
    }

    @Override
    public Iterator<ConsumerInfo> iterator() {
        return consumerInfos.iterator();
    }

    public SequenceBarrier getBarrierFor(final EventHandler<T> handler) {
        final ConsumerInfo consumerInfo = getEventProcessorInfo(handler);
        return consumerInfo != null ? consumerInfo.getBarrier() : null;
    }

    private EventProcessorInfo<T> getEventProcessorInfo(final EventHandler<T> handler) {
        return eventProcessorInfoByEventHandler.get(handler);
    }

    private ConsumerInfo getEventProcessorInfo(final Sequence barrierEventProcessor) {
        return eventProcessorInfoBySequence.get(barrierEventProcessor);
    }
}
