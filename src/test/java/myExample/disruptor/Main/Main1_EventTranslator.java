package myExample.disruptor.Main;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import myExample.disruptor.LongEvent;
import myExample.disruptor.LongEventFactory;
import myExample.disruptor.LongEventHandler;
import myExample.disruptor.LongEventProducerWithTranslator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author cgb
 * @create 2018-06-18
 **/
public class Main1_EventTranslator {

    public static Long MAX_OPS = 1000L;

    public static void main(String[] args) {
        // 初始化线程池
        ExecutorService executor = Executors.newCachedThreadPool();

        LongEventFactory factory = new LongEventFactory();

        int bufferSize = 1024;

        Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(factory, bufferSize, executor);

        disruptor.handleEventsWith(new LongEventHandler());

        disruptor.start();

        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        LongEventProducerWithTranslator longEventProducerWithTranslator = new LongEventProducerWithTranslator(ringBuffer);

        for (long l = 0; l < MAX_OPS; l++) {
            longEventProducerWithTranslator.onData(l);
//            longEventProducerWithTranslator.onData(l,l);
        }

        disruptor.shutdown();
        executor.shutdown();
    }
}
