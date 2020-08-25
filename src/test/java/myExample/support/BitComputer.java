package myExample.support;

/**
 * @author cgb
 * @create 2018-07-05
 **/
public class BitComputer {

    public static final long M = 1000 * 1000 * 10000L;

    public static void main(String[] args) {

        long a = 0;
        long marked = System.currentTimeMillis();
        for (long i = 0; i < M ; ++i){
//            a = i%8;
            a = i&7;
        }
        long time = (System.currentTimeMillis() - marked);
        System.out.println(" Loop times:" + time + "ms");

        marked = System.currentTimeMillis();
        for (long i = 0; i < M ; ++i){
//            a = i&7;
            a = i%8;
        }
        time = (System.currentTimeMillis() - marked);
        System.out.println( " Loop times:" + time + "ms");
    }
}
