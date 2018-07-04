package myExample.disruptor.Main;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.waitstrategy.BlockingWaitStrategy;
import com.lmax.disruptor.waitstrategy.YieldingWaitStrategy;
import myExample.disruptor.LongEvent;
import myExample.disruptor.LongEventFactory;
import myExample.disruptor.LongEventHandler;
import myExample.disruptor.LongEventProducer;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author cgb
 * @create 2018-06-18
 **/
public class Main2_ProducerType_WaitStrategy {

    public static void main(String[] args) {
        // ExecutorService executor = Executors.newCachedThreadPool();
        ThreadFactory threadFactory = Executors.defaultThreadFactory();

        LongEventFactory factory = new LongEventFactory();
        int bufferSize = 1024;

        //默认使用ProducerType.MULTI
//        Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(factory, bufferSize, threadFactory);
        Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(factory, bufferSize, threadFactory, ProducerType.MULTI,
                                                                  new BlockingWaitStrategy());

        disruptor.handleEventsWith(new LongEventHandler());
        disruptor.start();

        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        final LongEventProducer producer = new LongEventProducer(ringBuffer);

        final CyclicBarrier cb = new CyclicBarrier(10);
        ExecutorService executor1 = Executors.newCachedThreadPool();
        for (long i = 0; i < 10; ++i){
            final long value = i;
            executor1.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.out.println(value + " is ready...");
                        cb.await();
                        producer.onData(value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

    }
}
