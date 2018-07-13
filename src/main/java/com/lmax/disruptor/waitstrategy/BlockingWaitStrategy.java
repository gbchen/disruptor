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
package com.lmax.disruptor.waitstrategy;

import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.exception.AlertException;
import com.lmax.disruptor.util.ThreadHints;

/**
 * 如果实际可消费的索引号小于消费者期望消费的所以号，消费者就进入等待状态。后续生产者通过 publish 方法将消费者唤醒。
 *
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 * <p>
 * This strategy can be used when throughput and low-latency are not as important as CPU resource.
 */
public final class BlockingWaitStrategy implements WaitStrategy {

    private final Object mutex = new Object();

    // 等待最大可用序列号
    @Override
    public long waitFor(long sequence, Sequence cursorSequence, Sequence dependentSequence,
                        SequenceBarrier barrier) throws AlertException, InterruptedException {
        long availableSequence;
        // sequence 是消费者期望的消费索引号，相当于读指针
        // cursorSequence 就是生产者的写指针
        if (cursorSequence.get() < sequence) {
            synchronized (mutex) {
                while (cursorSequence.get() < sequence) {
                    //循环检查是否 请求的seq > 生产者seq，是的话检查是否已解除屏障，是则抛异常，终止循环，否则等待。
                    barrier.checkAlert();
                    mutex.wait();
                }
            }
        }

        // 当消费者之间没有依赖关系的时候，dependentSequence 就是 cursorSequence
        // 存在依赖关系的时候，dependentSequence 里存放的是一组依赖的 Sequence，get 方法得到的是消费最慢的依赖的位置
        while ((availableSequence = dependentSequence.get()) < sequence) {
            barrier.checkAlert();
            ThreadHints.onSpinWait();
        }

        return availableSequence;
    }

    //通知所有等待的消费者
    @Override
    public void signalAllWhenBlocking() {
        synchronized (mutex) {
            mutex.notifyAll();
        }
    }

    @Override
    public String toString() {
        return "BlockingWaitStrategy{" + "mutex=" + mutex + '}';
    }
}
