package myExample.disruptor.extend;

import com.lmax.disruptor.EventHandler;
import myExample.disruptor.LongEvent;

/**
 * 消费者调用的处理接口
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEventHandlerThrowsException implements EventHandler<LongEvent> {

    /**
     * 从RingBuffer中获取到数据之后会调用此方法
     * @param longEvent 发布到RingBuffer中的时间
     * @param sequence 当前正在处理的事件序号
     * @param endOfBatch 是否为RingBuffer的最后一个
     * @throws Exception
     */
    public void onEvent(LongEvent longEvent, long sequence, boolean endOfBatch) throws Exception {
        if (longEvent.getValue() == 5){
            throw new Exception("异常!!!");
        }
        System.out.println(longEvent.getValue());
    }
}
