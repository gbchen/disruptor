package com.lmax.disruptor.waitstrategy;

import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.exception.AlertException;
import com.lmax.disruptor.exception.TimeoutException;

import java.util.concurrent.TimeUnit;

import static com.lmax.disruptor.util.Util.awaitNanos;

public class TimeoutBlockingWaitStrategy implements WaitStrategy {

    private final Object mutex = new Object();
    private final long   timeoutInNanos;

    public TimeoutBlockingWaitStrategy(final long timeout, final TimeUnit units) {
        timeoutInNanos = units.toNanos(timeout);
    }

    @Override
    public long waitFor(final long sequence, final Sequence cursorSequence, final Sequence dependentSequence,
                        final SequenceBarrier barrier) throws AlertException, InterruptedException, TimeoutException {
        long timeoutNanos = timeoutInNanos;

        long availableSequence;
        if (cursorSequence.get() < sequence) {
            synchronized (mutex) {
                while (cursorSequence.get() < sequence) {
                    barrier.checkAlert();
                    timeoutNanos = awaitNanos(mutex, timeoutNanos);
                    if (timeoutNanos <= 0) {
                        throw TimeoutException.INSTANCE;
                    }
                }
            }
        }

        while ((availableSequence = dependentSequence.get()) < sequence) {
            barrier.checkAlert();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking() {
        synchronized (mutex) {
            mutex.notifyAll();
        }
    }

    @Override
    public String toString() {
        return "TimeoutBlockingWaitStrategy{" + "mutex=" + mutex + ", timeoutInNanos=" + timeoutInNanos + '}';
    }
}
