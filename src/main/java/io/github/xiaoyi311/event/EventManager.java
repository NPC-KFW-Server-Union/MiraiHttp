package io.github.xiaoyi311.event;

import io.github.xiaoyi311.MiraiHttpConn;

import java.util.*;

/**
 * MiraiHttp 监听管理器<br/>
 * 管理并通知所有监听类
 */
public class EventManager {
    /**
     * 所有监听类与 Mirai 连接对应表
     */
    private static final Map<MiraiHttpConn, Collection<MiraiEventListener>> listeners = new HashMap<>();

    /**
     * 注册监听类到指定机器人
     *
     * @param conn  机器人 QQ
     * @param listener 监听类
     */
    public static void addListener(MiraiHttpConn conn, MiraiEventListener listener){
        Collection<MiraiEventListener> temp = listeners.get(conn);
        Collection<MiraiEventListener> temp1 = temp == null ? new ArrayList<>() : temp;
        temp1.add(listener);
        listeners.put(conn, temp1);
    }

    /**
     * 卸载指定机器人的监听类
     *
     * @param conn  机器人 QQ
     * @param listener 监听类
     */
    public static void removeListener(MiraiHttpConn conn, MiraiEventListener listener){
        Collection<MiraiEventListener> temp = listeners.get(conn);
        temp.remove(listener);
        listeners.put(conn, temp);
    }

    /**
     * 获取机器人对应监听类
     *
     * @param conn      Mirai 连接
     * @return          对应的监听类租
     */
    protected static Iterator getListener(MiraiHttpConn conn){
        Collection<MiraiEventListener> mel = listeners.get(conn);
        return mel != null ? mel.iterator() : null;
    }
}
