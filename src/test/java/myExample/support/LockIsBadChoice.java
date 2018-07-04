package myExample.support;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author cgb
 * @create 2018-06-14
 **/
public class LockIsBadChoice {

    public static Long MAX_NUM         = 1000 * 1000 * 1000L;


    public Long        num             = 0L;
    public AtomicLong  atomicLong      = new AtomicLong(0);
    public Long        startTime;

    public synchronized void increaseNumSynchronized() {
        this.num++;
        if (num.equals(MAX_NUM)) {
            System.out.println(System.currentTimeMillis() - startTime);
        }
    }

    public void increaseAtomicLongNum() {
        atomicLong.incrementAndGet();
        if (MAX_NUM.equals(atomicLong.get())) {
            System.out.println(System.currentTimeMillis() - startTime);
        }
    }

    public void increaseNum() {
        this.num++;
        if (num.equals(MAX_NUM)) {
            System.out.println(System.currentTimeMillis() - startTime);
        }
    }

    public static void main(String[] args) {
        ExecutorService    executorService = Executors.newCachedThreadPool();

        final LockIsBadChoice testCase1 = new LockIsBadChoice();
        for (int i = 1; i <= 1; ++i) {
            executorService.submit(new Runnable() {

                @Override
                public void run() {
                    testCase1.startTime = System.currentTimeMillis();
                    while (testCase1.num < MAX_NUM) {
                        testCase1.increaseNumSynchronized();
                    }
                    System.out.println("testCase1 end " + testCase1.num);
                }
            });
        }


        final LockIsBadChoice testCase2 = new LockIsBadChoice();
        for (int i = 1; i <= 1; ++i) {
            executorService.submit(new Runnable() {

                @Override
                public void run() {
                    testCase2.startTime = System.currentTimeMillis();
                    while (testCase2.num < MAX_NUM) {
                        testCase2.increaseNum();
                    }
                    System.out.println("testCase2 end " + testCase2.num);
                }
            });
        }


        final LockIsBadChoice testCase3 = new LockIsBadChoice();
        for (int i = 1; i <= 1; ++i) {
            executorService.submit(new Runnable() {

                @Override
                public void run() {
                    testCase3.startTime = System.currentTimeMillis();
                    while (testCase3.atomicLong.get() < MAX_NUM) {
                        testCase3.increaseAtomicLongNum();
                    }
                    System.out.println("testCase3 end " + testCase3.num);
                }
            });
        }
    }
}
