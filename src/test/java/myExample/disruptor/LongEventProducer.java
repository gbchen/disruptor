package myExample.disruptor;

import com.lmax.disruptor.RingBuffer;

/**
 * 事件发布模板
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEventProducer {

    private final RingBuffer<LongEvent> ringBuffer;

    public LongEventProducer(RingBuffer<LongEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    /**
     * onData用来发布事件，每调用一次就发布一次事件事件 它的参数会通过事件传递给消费者
     */
    public void onData(Long l) {
        // 可以把ringBuffer看做一个事件队列，那么next就是得到下面一个事件槽
        long sequence = ringBuffer.next();
        try {
            // 用上面的索引取出一个空的(或过时的)事件用于填充
            LongEvent event = ringBuffer.get(sequence);
            event.setValue(l);
        } finally {
            // 发布序号,发布后可以被消费
            ringBuffer.publish(sequence);
        }
    }
}
