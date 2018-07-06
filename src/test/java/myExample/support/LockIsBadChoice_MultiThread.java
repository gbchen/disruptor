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
public class LockIsBadChoice_MultiThread {

    public static final Long MAX_NUM         = 1000 * 1000 * 100L;
    public static final int ThreadCount = 2;
    public static final int BarrierNum = ThreadCount;



    public static void main(String[] args) throws BrokenBarrierException, InterruptedException {
        ExecutorService    executorService = Executors.newCachedThreadPool();

        final CyclicBarrier cyclicBarrier = new CyclicBarrier(BarrierNum);

        //原子操作
        for (int i = 1; i <= ThreadCount; ++i) {
            executorService.submit(new Runnable() {

                @Override
                public void run() {
//                    testCase.startTime = System.currentTimeMillis();
//                    while (testCase.atomicLong.get() < MAX_NUM) {
//                        testCase.increaseAtomicLongNum();
//                    }
                    try {
                        cyclicBarrier.await();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        System.out.println("原子任务正在进行...");
        cyclicBarrier.await();

        //加锁
        for (int i = 1; i <= ThreadCount; ++i) {
            executorService.submit(new Runnable() {

                @Override
                public void run() {
//                    testCase.startTime = System.currentTimeMillis();
//                    while (testCase.num < MAX_NUM) {
//                        testCase.increaseNumSynchronized();
//                    }
                    try {
                        cyclicBarrier.await();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        System.out.println("加锁任务正在进行...");
        cyclicBarrier.await();

        executorService.shutdown();
    }
}
