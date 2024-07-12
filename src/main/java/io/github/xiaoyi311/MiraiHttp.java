package io.github.xiaoyi311;

import io.github.xiaoyi311.err.RobotNotFound;
import io.github.xiaoyi311.err.VerifyKeyError;
import io.github.xiaoyi311.event.EventManager;
import io.github.xiaoyi311.event.MiraiEventListener;

/**
 * MiraiHttp 主类
 */
public class MiraiHttp {
    /**
     * 创建到 Mirai 服务器的连接，并绑定机器人<br/>
     * 可避免 Session 超时，但是如果不快速注册监听器，部分信息可能无法及时收到
     *
     * @param verifyKey       验证密钥
     * @param host            连接地址，类似于：127.0.0.1:8080
     * @param qq              机器人 QQ
     * @return                Session 管理
     * @throws VerifyKeyError 验证密钥错误
     * @throws RobotNotFound  指定机器人未找到
     */
    public static MiraiHttpConn createConn(String verifyKey, String host, Long qq) throws VerifyKeyError, RobotNotFound {
        return new MiraiHttpConn(verifyKey, host, qq);
    }

    /**
     * 创建到 Mirai 服务器的连接<br/>
     * 并不推荐，如果长时间（30s）不绑定需要重新创建
     *
     * @param verifyKey       验证密钥
     * @param host            连接地址，类似于：127.0.0.1:8080
     * @return                Session 管理
     * @throws VerifyKeyError 验证密钥错误
     */
    public static MiraiHttpConn createConn(String verifyKey, String host) throws VerifyKeyError {
        return new MiraiHttpConn(verifyKey, host);
    }

    /**
     * 注册事件监听器<br/>
     * 建议在绑定机器人前进行注册，防止部分信息无法接收
     *
     * @param listener 监听器
     * @param conn  对应的 Mirai 连接
     */
    public static void registerListener(MiraiEventListener listener, MiraiHttpConn conn){
        EventManager.addListener(conn, listener);
    }

    /**
     * 卸载事件监听器
     *
     * @param listener 监听器
     * @param conn  对应的 Mirai 连接
     */
    public static void removeListener(MiraiEventListener listener, MiraiHttpConn conn){
        EventManager.removeListener(conn, listener);
    }
}
