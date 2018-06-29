package myExample;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.event.translator.EventTranslatorOneArg;

/**
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEventProducerWithTranslator {

    // 一个translator可以看做一个事件初始化器，publicEvent方法会调用它
    // 填充Event
    private static final EventTranslatorOneArg<LongEvent, Long> TRANSLATOR = new EventTranslatorOneArg<LongEvent, Long>() {

                                                                               public void translateTo(LongEvent event,
                                                                                                       long sequence,
                                                                                                       Long l) {
                                                                                   event.setValue(l);
                                                                               }
                                                                           };
    private final RingBuffer<LongEvent> ringBuffer;

    public LongEventProducerWithTranslator(RingBuffer<LongEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    public void onData(Long l) {
        ringBuffer.publishEvent(TRANSLATOR, l);
    }
}
