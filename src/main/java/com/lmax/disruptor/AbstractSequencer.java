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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.lmax.disruptor.util.Util;

/**
 *
 * Base class for the various sequencer types (single/multi).  Provides
 * common functionality like the management of gating(控制) sequences (add/remove) and
 * ownership(所有权) of the current cursor.
 */
public abstract class AbstractSequencer implements Sequencer {
    /**
     * 原子更新需要跟踪的消费者seq,gatingSequences
     */
    private static final AtomicReferenceFieldUpdater<AbstractSequencer, Sequence[]> SEQUENCE_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(AbstractSequencer.class, Sequence[].class, "gatingSequences");

    /**
     * ringBuffer大小
     */
    protected final int bufferSize;

    /**
     * 等待策略
     */
    protected final WaitStrategy waitStrategy;

    /**
     * 生产者生产到的位置
     */
    protected final Sequence cursor = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    /**
     * 需跟踪的消费者组消费到的seqs
     */
    protected volatile Sequence[] gatingSequences = new Sequence[0];

    /**
     * Create with the specified buffer size and wait strategy.
     *
     * @param bufferSize   The total number of entries, must be a positive power of 2. bufferSize 必须大于0且为2的倍数
     * @param waitStrategy The wait strategy used by this sequencer
     */
    public AbstractSequencer(int bufferSize, WaitStrategy waitStrategy) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("bufferSize must not be less than 1");
        }
        if (Integer.bitCount(bufferSize) != 1) {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }

        this.bufferSize = bufferSize;
        this.waitStrategy = waitStrategy;
    }

    /**
     * @see Sequencer#getCursor()
     */
    @Override
    public final long getCursor() {
        return cursor.get();
    }

    /**
     * @see Sequencer#getBufferSize()
     */
    @Override
    public final int getBufferSize() {
        return bufferSize;
    }

    /**
     * 原子添加需跟踪的前置消费者seq到数组
     * @see Sequencer#addGatingSequences(Sequence...)
     */
    @Override
    public final void addGatingSequences(Sequence... gatingSequences) {
        SequenceGroups.addSequences(this, SEQUENCE_UPDATER, this, gatingSequences);
    }

    /**
     * 原子删除需跟踪的前置消费者seq
     * @see Sequencer#removeGatingSequence(Sequence)
     */
    @Override
    public boolean removeGatingSequence(Sequence sequence) {
        return SequenceGroups.removeSequence(this, SEQUENCE_UPDATER, sequence);
    }

    /**
     * 获得前置消费者中消费最慢的seq
     * @see Sequencer#getMinimumSequence()
     */
    @Override
    public long getMinimumSequence() {
        return Util.getMinimumSequence(gatingSequences, cursor.get());
    }

    /**
     * 生成消费者屏障，指定前置消费者seq
     * @see Sequencer#newBarrier(Sequence...)
     */
    @Override
    public SequenceBarrier newBarrier(Sequence... sequencesToTrack) {
        return new ProcessingSequenceBarrier(this, waitStrategy, cursor, sequencesToTrack);
    }

    /**
     * 不建议使用
     * Creates an event poller for this sequence that will use the supplied data provider and gating sequences.
     *
     * @param dataProvider The data source for users of this event poller
     * @param gatingSequences Sequence to be gated on.
     * @return A poller that will gate on this ring buffer and the supplied sequences.
     */
    @Override
    public <T> EventPoller<T> newPoller(DataProvider<T> dataProvider, Sequence... gatingSequences) {
        return EventPoller.newInstance(dataProvider, this, new Sequence(), cursor, gatingSequences);
    }

    @Override
    public String toString() {
        return "AbstractSequencer{" + "waitStrategy=" + waitStrategy + ", cursor=" + cursor + ", gatingSequences="
               + Arrays.toString(gatingSequences) + '}';
    }
}
