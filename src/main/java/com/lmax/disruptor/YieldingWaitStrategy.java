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

/**
 * 先尝试一百次，再不满足条件，当前线程就yield，让其他线程先执行
 * Yielding strategy that uses a Thread.yield() for {@link com.lmax.disruptor.EventProcessor}s waiting on a barrier
 * after an initially spinning.
 * <p>
 * This strategy will use 100% CPU, but will more readily give up the CPU than a busy spin strategy if other threads
 * require CPU resource.
 */
public final class YieldingWaitStrategy implements WaitStrategy {

    private static final int SPIN_TRIES = 100;

    @Override
    public long waitFor(final long sequence, Sequence cursor, final Sequence dependentSequence,
                        final SequenceBarrier barrier) throws AlertException, InterruptedException {
        long availableSequence;
        int counter = SPIN_TRIES;

        //循环，如果生产的最大序列号小于消费者需要的序列号，继续等待，等待次数超过counter次，线程yield
        //这里dependentSequence就是cursorSequence,在ProcessorSequencerBarrier构造函数中可以看到
        while ((availableSequence = dependentSequence.get()) < sequence) {
            counter = applyWaitMethod(barrier, counter);
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking() {
    }

    //counter大于0则减一返回，否则当前线程yield
    private int applyWaitMethod(final SequenceBarrier barrier, int counter) throws AlertException {
        barrier.checkAlert();

        if (0 == counter) {
            Thread.yield();
        } else {
            --counter;
        }

        return counter;
    }
}
