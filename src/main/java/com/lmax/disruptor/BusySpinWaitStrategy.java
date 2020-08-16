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


import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.util.ThreadHints;

/**
 * 当队列没有可消费的数据时，会一直执行循环，因此会造成CPU的使用率很高，需要特别注意
 * Busy Spin strategy that uses a busy spin loop for {@link com.lmax.disruptor.EventProcessor}s waiting on a barrier.
 * <p>
 * This strategy will use CPU resource to avoid syscalls which can introduce latency jitter.  It is best
 * used when threads can be bound to specific CPU cores.
 */
public final class BusySpinWaitStrategy implements WaitStrategy {

    @Override
    public long waitFor(final long sequence, Sequence cursor, final Sequence dependentSequence,
                        final SequenceBarrier barrier) throws AlertException, InterruptedException {
        long availableSequence;

        while ((availableSequence = dependentSequence.get()) < sequence) {
            barrier.checkAlert();
            ThreadHints.onSpinWait();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking() {
    }
}
