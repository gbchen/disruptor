package myExample.disruptor.extend;

import com.lmax.disruptor.ExceptionHandler;
import myExample.disruptor.LongEvent;

/**
 * @author cgb
 * @create 2018-07-04
 **/
public class MyExceptionHandler implements ExceptionHandler<LongEvent> {
    @Override
    public void handleEventException(Throwable ex, long sequence, LongEvent event) {
        System.out.println(" MyExceptionHandler ：" + event.getValue());
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        System.out.println(" Disruptor 启动异常啦！！ " );
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
        System.out.println(" Disruptor 关闭异常啦！！ " );
    }
}
