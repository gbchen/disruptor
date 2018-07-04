package myExample.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.event.translator.EventTranslator;
import com.lmax.disruptor.event.translator.EventTranslatorOneArg;
import com.lmax.disruptor.event.translator.EventTranslatorTwoArg;

/**
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEventProducerWithTranslator {

    // 一个translator可以看做一个事件初始化器，publicEvent方法会调用它
    // 填充Event
    private static final EventTranslatorOneArg<LongEvent, Long>       TRANSLATOR_ONE_ARG = new EventTranslatorOneArg<LongEvent, Long>() {

                                                                                             public void translateTo(LongEvent event,
                                                                                                                     long sequence,
                                                                                                                     Long l) {
                                                                                                 event.setValue(l);
                                                                                             }
                                                                                         };
    private static final EventTranslatorTwoArg<LongEvent, Long, Long> TRANSLATOR_TWO_ARG = new TranslatorTwo();





    private final RingBuffer<LongEvent>                               ringBuffer;

    public LongEventProducerWithTranslator(RingBuffer<LongEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }




    public void onData(Long l) {
        ringBuffer.publishEvent(TRANSLATOR_ONE_ARG, l);
    }

    public void onData(long a, long b) {
        ringBuffer.publishEvent(TRANSLATOR_TWO_ARG, a, b);
    }
}

/**
 * 两个参数
 */
class TranslatorTwo implements EventTranslatorTwoArg<LongEvent, Long, Long> {

    @Override
    public void translateTo(LongEvent event, long sequence, Long arg0, Long arg1) {
        event.setValue(arg0 + arg1);
    }
}
