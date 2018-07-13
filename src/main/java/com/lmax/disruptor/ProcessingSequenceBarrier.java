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


import com.lmax.disruptor.exception.AlertException;
import com.lmax.disruptor.exception.TimeoutException;

/**
 * {@link SequenceBarrier} handed out for gating {@link EventProcessor}s on a cursor sequence and optional dependent {@link EventProcessor}(s),
 * using the given WaitStrategy.
 */
final class ProcessingSequenceBarrier implements SequenceBarrier {

    private final WaitStrategy waitStrategy;

    /** 前置消费者的Sequence，如果没有，则等于生产者的Sequence */
    private final Sequence     dependentSequence;

    /** 是否已通知 */
    private volatile boolean   alerted = false;

    /** 生产者的Sequence */
    private final Sequence     cursorSequence;

    /** 生产者seq管理器，获得最大可用long，MultiProducerSequencer 或 SingleProducerSequencer */
    private final Sequencer    sequencer;

    ProcessingSequenceBarrier(final Sequencer sequencer, final WaitStrategy waitStrategy, final Sequence cursorSequence,
                              final Sequence[] dependentSequences) {
        this.sequencer = sequencer;
        this.waitStrategy = waitStrategy;
        this.cursorSequence = cursorSequence;
        if (0 == dependentSequences.length) {
            dependentSequence = cursorSequence;
        } else {
            dependentSequence = new FixedSequenceGroup(dependentSequences);
        }
    }

    /**
     * 等待生产者生产出更多的产品用来消费
     * waitFor方法第一个参数是消费者期望消费的索引序列号，
     * cursorSequence是生产者的current，
     * 返回值availableSequence是实际可消费的索引号，这个值返回后，生产者还要做检查，就是通过最下面的 getHighestPublishedSequence方法：
     */
    @Override
    public long waitFor(final long sequence) throws AlertException, InterruptedException, TimeoutException {
        //先检测是否已通知生产者，通知过则发异常
        checkAlert();

        //根据等待策略来等待可用的序列值
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);

        //如果可用的序列值小于请求的序列，那么直接返回可用的序列。
        if (availableSequence < sequence) {
            return availableSequence;
        }

        //availableSequence >= next时，检查生产者的位置信息的标志是否正常.这个是和生产者的publish方法联系起来的. 否则，返回能使用（已发布事件）的最大的序列值，
        //消费者可以在此追赶生产者
        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }

    @Override
    public long getCursor() {
        return dependentSequence.get();
    }

    @Override
    public boolean isAlerted() {
        return alerted;
    }

    @Override
    public void alert() {
        //设置通知标记
        alerted = true;
        //如果有线程以阻塞的方式等待序列，将其唤醒。
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert() {
        alerted = false;
    }

    ////循环检查是否有其他线程已唤醒消费者,是的话则抛异常,等同于是否已解除屏障
    @Override
    public void checkAlert() throws AlertException {
        if (alerted) {
            throw AlertException.INSTANCE;
        }
    }
}
