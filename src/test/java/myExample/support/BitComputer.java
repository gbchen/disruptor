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
            a = i%8;
        }
        System.out.println(" Loop times:" + (System.currentTimeMillis() - marked) + "ms");

        marked = System.currentTimeMillis();
        for (long i = 0; i < M ; ++i){
            a = i&(8-1);
        }
        System.out.println( " Loop times:" + (System.currentTimeMillis() - marked) + "ms");
    }
}
