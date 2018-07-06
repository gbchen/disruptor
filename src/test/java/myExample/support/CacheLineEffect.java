package myExample.support;

/**
 * @author cgb
 * @create 2018-07-05
 **/
public class CacheLineEffect {
    public static final int M = 1024*1024;
    public static final int N = 256;

    // 8B * M * N
    // 8B * 1024 * 1024 * 256 = 8MB * 256 = 2048M

    // 考虑一般缓存行大小是64字节，一个 long 类型占8字节
    static long[][] arr;

    public static void main(String[] args) {
        arr = new long[M][];
        for (int i = 0; i < M; i++) {
            arr[i] = new long[N];
            for (int j = 0; j < N; j++) {
                arr[i][j] = 0L;
            }
        }
        long sum = 0L;
        long marked = System.currentTimeMillis();
        for (int i = 0; i < M; i += 1) {
            for (int j = 0; j < N; j++) {
                sum = arr[i][j];
            }
        }
        System.out.println(sum + " Loop times:" + (System.currentTimeMillis() - marked) + "ms");

        marked = System.currentTimeMillis();
        for (int i = 0; i < N; i += 1) {
            for (int j = 0; j < M; j++) {
                sum = arr[j][i];
            }
        }
        System.out.println(sum + " Loop times:" + (System.currentTimeMillis() - marked) + "ms");
    }
}
