package myExample;

import com.lmax.disruptor.EventFactory;

/**
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEventFactory implements EventFactory {
    public Object newInstance() {
        return new LongEvent();
    }
}