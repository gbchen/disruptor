package myExample.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

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

        // 初始化EventFactory
        LongEventFactory factory = new LongEventFactory();

        // RingBuffer的大小，必须为2的指数
        int bufferSize = 1024;

        // 初始化RingBuffer
        Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(factory, bufferSize, executor);

        // 指定事件处理器
        disruptor.handleEventsWith(new LongEventHandler());

        // 开启Disruptor,开启所有线程(只能调用一次，并且所有的EventHandler、ExceptionHandler必须在start方法之前添加)
        disruptor.start();

        // 获取RingBuffer
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        LongEventProducerWithTranslator longEventProducerWithTranslator = new LongEventProducerWithTranslator(ringBuffer);

        for (long l = 0; l < MAX_OPS; l++) {
            longEventProducerWithTranslator.onData(l);
//            longEventProducerWithTranslator.onData(l,l);
        }

    }
}
