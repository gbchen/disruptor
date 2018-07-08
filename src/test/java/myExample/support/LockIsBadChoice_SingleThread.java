package myExample.support;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author cgb
 * @create 2018-06-14
 **/
public class LockIsBadChoice_SingleThread {

    public static final Long MAX_NUM    = 1000 * 1000 * 100L;
    public static final int  BarrierNum = 2;

    public static void main(String[] args) throws BrokenBarrierException, InterruptedException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        final Counter1 counter = new Counter1();
        final CyclicBarrier cyclicBarrier = new CyclicBarrier(BarrierNum);

        // 无锁
        executorService.submit(new Runnable() {

            @Override
            public void run() {
                Long startTime = System.currentTimeMillis();
                while (counter.getNoLockLong() < MAX_NUM) {
                    counter.increaseNumWithNoLock();
                }
                try {
                    System.out.println("无锁任务结束 耗时:" + (System.currentTimeMillis() - startTime) + "\n");
                    cyclicBarrier.await();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println("无锁任务正在进行...");
        cyclicBarrier.await();

        // 原子操作
        executorService.submit(new Runnable() {

            @Override
            public void run() {
                Long startTime = System.currentTimeMillis();
                while (counter.getAtomicLong().get() < MAX_NUM) {
                    counter.increaseAtomicLongNum();
                }
                try {
                    System.out.println("原子任务结束 耗时:" + (System.currentTimeMillis() - startTime) + "\n");
                    cyclicBarrier.await();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println("原子任务正在进行...");
        cyclicBarrier.await();

        // 加锁
        executorService.submit(new Runnable() {

            @Override
            public void run() {
                Long startTime = System.currentTimeMillis();
                while (counter.getLockLong() < MAX_NUM) {
                    counter.increaseNumWithLock();
                }
                try {
                    System.out.println("加锁任务结束 耗时:" + (System.currentTimeMillis() - startTime) + "\n");
                    cyclicBarrier.await();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println("加锁任务正在进行...");
        cyclicBarrier.await();

        executorService.shutdown();
    }
}

class Counter1 {

    private Long       noLockLong = 0L;
    private Long       lockLong   = 0L;
    private AtomicLong atomicLong = new AtomicLong(0);

    private Lock lock = new ReentrantLock();

    /**
     * 有锁
     * 用内置锁在单线程情况下JVM会进行优化，导致结果和预期不符
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

    public Long getLockLong() {
        return lockLong;
    }

    public AtomicLong getAtomicLong() {
        return atomicLong;
    }
}
