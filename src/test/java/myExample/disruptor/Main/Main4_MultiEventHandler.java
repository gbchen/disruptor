package myExample.disruptor.Main;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import myExample.disruptor.LongEvent;
import myExample.disruptor.LongEventFactory;
import myExample.disruptor.extend.LongEventHandler1;
import myExample.disruptor.extend.LongEventHandler2;
import myExample.disruptor.extend.LongEventHandler3;
import myExample.disruptor.extend.LongEventHandler4;
import myExample.disruptor.LongEventProducerWithTranslator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 强大的依赖处理
 * event在依赖处理过程中，都是同一个对象
 * @author cgb
 * @create 2018-06-18
 **/
public class Main4_MultiEventHandler {

    public static Long MAX_OPS = 2L;

    public static void main(String[] args) {
        // 初始化线程池
        ExecutorService executor = Executors.newCachedThreadPool();
        LongEventFactory factory = new LongEventFactory();
        int bufferSize = 8;
        Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(factory, bufferSize, executor);

        EventHandler<LongEvent> eventHandler1 = new LongEventHandler1();
        EventHandler<LongEvent> eventHandler2 = new LongEventHandler2();
        EventHandler<LongEvent> eventHandler3 = new LongEventHandler3();
        EventHandler<LongEvent> eventHandler4 = new LongEventHandler4();

        /*
         * 串行处理 h1--h2
         */
         disruptor.handleEventsWith(eventHandler1).handleEventsWith(eventHandler2);

        /*
         * 并行处理,两种写法 h1 -- h2
         */
//         disruptor.handleEventsWith(eventHandler1);
//         disruptor.handleEventsWith(eventHandler2);
//         disruptor.handleEventsWith(eventHandler2,eventHandler1);

        /*
         * h1
         *    --h3--h4
         * h2
         */
//        EventHandlerGroup<LongEvent> group1 = disruptor.handleEventsWith(eventHandler1);
//        EventHandlerGroup<LongEvent> group2 = disruptor.handleEventsWith(eventHandler2);
//        group1.and(group2).handleEventsWith(eventHandler3).handleEventsWith(eventHandler4);

        /*
         * h1--h2
         *       --h1
         * h3--h4
         */
//        EventHandlerGroup<LongEvent> group1 = disruptor.handleEventsWith(eventHandler1).handleEventsWith(eventHandler2);
//        EventHandlerGroup<LongEvent> group2 = disruptor.handleEventsWith(eventHandler3).handleEventsWith(eventHandler4);
//        group1.and(group2).handleEventsWith(eventHandler1);

        /*
         *      h2
         * h1--    --h4
         *      h3
         */
//        disruptor.handleEventsWith(eventHandler1).handleEventsWith(eventHandler2,eventHandler3).handleEventsWith(eventHandler4);

        /*
         * h1    h3
         *    --
         * h2    h4
         */
//        disruptor.handleEventsWith(eventHandler1,eventHandler2).handleEventsWith(eventHandler3,eventHandler4);

        disruptor.start();

        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        LongEventProducerWithTranslator longEventProducerWithTranslator = new LongEventProducerWithTranslator(ringBuffer);

        for (long l = 0; l < MAX_OPS; l++) {
            longEventProducerWithTranslator.onData(l);
        }

    }
}
