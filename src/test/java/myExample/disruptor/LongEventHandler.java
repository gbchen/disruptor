package myExample.disruptor;

import com.lmax.disruptor.EventHandler;

/**
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEventHandler implements EventHandler<LongEvent> {
    public void onEvent(LongEvent longEvent, long l, boolean b) throws Exception {
        System.out.println(longEvent.getValue());
    }
}