package myExample.disruptor.Main;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.IgnoreExceptionHandler;
import myExample.disruptor.LongEvent;
import myExample.disruptor.LongEventFactory;
import myExample.disruptor.extend.LongEventHandlerThrowsException;
import myExample.disruptor.LongEventProducerWithTranslator;
import myExample.disruptor.extend.MyExceptionHandler;
import myExample.disruptor.extend.MyExceptionHandler2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author cgb
 * @create 2018-06-18
 **/
public class Main3_ExceptionHandler {

    public static Long MAX_OPS = 10L;

    public static void main(String[] args) {
        // 初始化线程池
        ExecutorService executor = Executors.newCachedThreadPool();
        LongEventFactory factory = new LongEventFactory();
        int bufferSize = 8;
        Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(factory, bufferSize, executor);

        EventHandler<LongEvent> eventHandler = new LongEventHandlerThrowsException();

        disruptor.handleEventsWith(eventHandler);

        //默认没设置异常处理器，这样出现异常，就会终止程序
        disruptor.setDefaultExceptionHandler(new IgnoreExceptionHandler());
        //第二个会覆盖之前的
        disruptor.setDefaultExceptionHandler(new MyExceptionHandler());
        //具体的为handler设置的会覆盖之前的
        disruptor.handleExceptionsFor(eventHandler).with(new MyExceptionHandler2());

        disruptor.start();

        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        LongEventProducerWithTranslator longEventProducerWithTranslator = new LongEventProducerWithTranslator(ringBuffer);

        for (long l = 0; l < MAX_OPS; l++) {
            longEventProducerWithTranslator.onData(l);
        }

    }
}
