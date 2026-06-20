package tech.qiantong.qknow.thirdparty.constant;

/**
 * 连接池常量
 *
 * @author qknow
 */
public class HttpClientConstants {

    /**
     * 默认连接超时
     */
    public static final long DEFAULT_CONNECT_TIMEOUT = 10000L;

    /**
     * 默认读取超时
     */
    public static final long DEFAULT_READ_TIMEOUT = 120000L;

    /**
     * 默认写入超时
     */
    public static final long DEFAULT_WRITE_TIMEOUT = 120000L;

    /**
     * 默认总时长
     */
    public static final long DEFAULT_TIMEOUT = DEFAULT_CONNECT_TIMEOUT + DEFAULT_READ_TIMEOUT + DEFAULT_WRITE_TIMEOUT;
}
