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
        final Counter counter = new Counter();
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
