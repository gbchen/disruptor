package myExample.support;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author cgb
 * @create 2018-07-06
 **/
public class Counter {

    private Long       noLockLong = 0L;
    private Long       lockLong   = 0L;
    private AtomicLong atomicLong = new AtomicLong(0);

    private Lock lock = new ReentrantLock();

    /**
     * 有锁
     */
    public void increaseNumWithLock() {
        lock.lock();
        this.lockLong++;
        lock.unlock();
    }

    /**
     * 原子
     */
    public void increaseAtomicLongNum() {
        atomicLong.incrementAndGet();
    }

    /**
     * 无锁
     */
    public void increaseNumWithNoLock() {
        this.noLockLong++;
    }






    public Long getNoLockLong() {
        return noLockLong;
    }

    public void setNoLockLong(Long noLockLong) {
        this.noLockLong = noLockLong;
    }

    public Long getLockLong() {
        return lockLong;
    }

    public void setLockLong(Long lockLong) {
        this.lockLong = lockLong;
    }

    public AtomicLong getAtomicLong() {
        return atomicLong;
    }

    public void setAtomicLong(AtomicLong atomicLong) {
        this.atomicLong = atomicLong;
    }
}
