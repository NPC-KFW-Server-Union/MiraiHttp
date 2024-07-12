package io.github.xiaoyi311.err;

/**
 * Session 失效
 */
public class SessionOutDate extends Exception {
    /**
     * 创建错误
     */
    public SessionOutDate(){
        super("Session 已失效! 请刷新或重新创建 MiraiHttpConn!");
    }
}
