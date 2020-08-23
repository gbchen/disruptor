package myExample.disruptor;

import com.lmax.disruptor.EventFactory;

/**
 * 预初始化(实例化)
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEventFactory implements EventFactory {

    /**
     * 初始化操作
     * @return 数据单元
     */
    @Override
    public Object newInstance() {
        return new LongEvent();
    }
}
