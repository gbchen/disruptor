package myExample.disruptor;

import com.lmax.disruptor.EventHandler;

/**
 * 消费者调用的处理接口
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEventHandler implements EventHandler<LongEvent> {

    /**
     * 从RingBuffer中获取到数据之后会调用此方法
     * @param longEvent 发布到RingBuffer中的时间
     * @param sequence 当前正在处理的事件序号
     * @param endOfBatch 是否为批处理的最后一个
     * @throws Exception
     */
    public void onEvent(LongEvent longEvent, long sequence, boolean endOfBatch) throws Exception {

        System.out.println("LongEventHandler:" + longEvent.getValue() + " seq:" + sequence + " endOfBatch:" + endOfBatch  + " threadName" + Thread.currentThread().getName());
    }
}
