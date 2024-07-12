package io.github.xiaoyi311.event;

import io.github.xiaoyi311.MiraiHttpConn;

import java.util.EventObject;
import java.util.Iterator;

/**
 * MiraiHttp 事件基类<br/>
 * 实现触发事件方法
 */
public class MiraiEventBase extends EventObject {
    /**
     * Mirai 连接
     */
    public MiraiHttpConn conn;

    /**
     * 创建事件
     *
     * @param conn Mirai 连接
     */
    public MiraiEventBase(MiraiHttpConn conn) {
        super(conn);
        this.conn = conn;
    }

    /**
     * 触发事件<br/>
     * 遍历所有监听类
     */
    public void doEvent(MiraiHttpConn session) {
        Iterator iter = EventManager.getListener(session);
        if (iter != null){
            while (iter.hasNext()){
                onEvent((MiraiEventListener) iter.next());
            }
        }
    }

    /**
     * 触发事件<br/>
     * 此处为空，等待子类重写并通知监听类
     *
     * @param listener 监听类
     */
    protected void onEvent(MiraiEventListener listener) {}
}
