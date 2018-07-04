package myExample.disruptor.extend;

import com.lmax.disruptor.EventHandler;
import myExample.disruptor.LongEvent;

/**
 * 消费者调用的处理接口
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEventHandler3 implements EventHandler<LongEvent> {

    public void onEvent(LongEvent longEvent, long sequence, boolean endOfBatch) throws Exception {
        System.out.println("LongEventHandler3 开始处理:" + longEvent.getValue());
        Thread.sleep(3000);
        System.out.println("LongEventHandler3 处理完毕:" + longEvent.getValue()+ "\n");
    }
}
