package myExample;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEventMain {
    public static Long MAX_OPS = 1000 * 1000L;

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newCachedThreadPool();
        LongEventFactory factory = new LongEventFactory();
        int bufferSize = 1024;

        Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(factory, bufferSize, executor);
//        Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(factory, bufferSize, executor,
//                ProducerType.SINGLE, new YieldingWaitStrategy());

        disruptor.handleEventsWith(new LongEventHandler());
        disruptor.start();
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        LongEventProducer producer = new LongEventProducer(ringBuffer);
        LongEventProducerWithTranslator longEventProducerWithTranslator = new LongEventProducerWithTranslator(ringBuffer);

        long beginTime = System.currentTimeMillis();
        for (long l = 0; l < MAX_OPS; l++) {
//            producer.onData(l);
            longEventProducerWithTranslator.onData(l);
        }
        disruptor.shutdown();
        executor.shutdown();
        System.out.println(String.format("总共耗时%s毫秒", (System.currentTimeMillis() - beginTime)));
    }
}
