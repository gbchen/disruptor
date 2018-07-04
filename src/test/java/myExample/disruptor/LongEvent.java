package myExample.disruptor;

/**
 * Disruptor中的数据单元
 * @author cgb
 * @create 2018-06-18
 **/
public class LongEvent {

    private long value;

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }
}
