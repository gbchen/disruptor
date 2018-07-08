package myExample.support;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author cgb
 * @create 2018-06-14
 **/
public class LockIsBadChoice_2_MultiThread {

    public static final int ThreadCount = 2;
    public static final int BarrierNum = ThreadCount + 1;



    public static void main(String[] args) throws BrokenBarrierException, InterruptedException, ExecutionException {
        ExecutorService    executorService = Executors.newCachedThreadPool();
        final Counter counter = new Counter();
        final CyclicBarrier cyclicBarrier = new CyclicBarrier(BarrierNum);
        Future<?>[] futures = new Future[ThreadCount];

        // 原子操作---------------------------------------------------------------------------------------------
        for (int i = 0; i < ThreadCount; ++i) {
            futures[i] = executorService.submit(new CountThread(cyclicBarrier, counter, "原子"));
        }
        System.out.println("原子任务正在进行...");
        long startTime = System.currentTimeMillis();
        cyclicBarrier.await();

        for (int i = 0; i < ThreadCount; i++) {
            futures[i].get();
        }
        System.out.println("原子任务执行完毕,耗时:" + (System.currentTimeMillis() - startTime));



        // 加锁-----------------------------------------------------------------------------------------------
        for (int i = 0; i < ThreadCount; ++i) {
            futures[i] = executorService.submit(new CountThread(cyclicBarrier, counter, "加锁"));
        }
        System.out.println("加锁任务正在进行...");
        startTime = System.currentTimeMillis();
        cyclicBarrier.await();

        for (int i = 0; i < ThreadCount; i++) {
            futures[i].get();
        }
        System.out.println("加锁任务执行完毕,耗时:" + (System.currentTimeMillis() - startTime));

        executorService.shutdown();
    }
}


class CountThread implements Runnable{
    public static final Long MAX_NUM         = 1000 * 1000 * 100L;

    private CyclicBarrier cyclicBarrier;
    private Counter counter;
    private String funcName;

    public CountThread(CyclicBarrier cyclicBarrier, Counter counter, String funcName) {
        this.cyclicBarrier = cyclicBarrier;
        this.counter = counter;
        this.funcName = funcName;
    }

    @Override
    public void run() {
        switch (funcName) {
            case "原子":{
                try {
                    cyclicBarrier.await();

                    while (counter.getAtomicLong().get() < MAX_NUM) {
                        counter.increaseAtomicLongNum();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case "加锁":{
                try {
                    cyclicBarrier.await();

                    while (counter.getLockLong() < MAX_NUM) {
                        counter.increaseNumWithLock();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
        }

    }
}



class Counter {

    private Long       lockLong   = 0L;
    private AtomicLong atomicLong = new AtomicLong(0);

    private ReentrantLock lock = new ReentrantLock();

    /**
     * 有锁
     * 用内置锁在单线程情况下JVM会进行优化，导致结果和预期不符
     */
    public void increaseNumWithLock() {
        final ReentrantLock lock = this.lock;
        try {
            lock.lock();
            this.lockLong++;
        }finally {
            lock.unlock();
        }
    }

    /**
     * 原子
     */
    public void increaseAtomicLongNum() {
        atomicLong.incrementAndGet();
    }



    public Long getLockLong() {
        final ReentrantLock lock = this.lock;
        try {
            lock.lock();
            return lockLong;
        }finally {
            lock.unlock();
        }
    }

    public AtomicLong getAtomicLong() {
        return atomicLong;
    }
}