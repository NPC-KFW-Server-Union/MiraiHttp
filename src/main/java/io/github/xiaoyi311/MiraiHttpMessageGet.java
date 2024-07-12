package io.github.xiaoyi311;

import com.alibaba.fastjson.JSONObject;
import io.github.xiaoyi311.err.RobotNotFound;
import io.github.xiaoyi311.err.VerifyKeyError;
import io.github.xiaoyi311.event.MessageEventBase;
import io.github.xiaoyi311.event.MiraiEventBase;
import io.github.xiaoyi311.util.Network;

import java.lang.reflect.Constructor;

/**
 * Http 轮询多线程<br/>
 * 使用多线程获取事件与信息
 */
public class MiraiHttpMessageGet extends Thread{
    /**
     * 查询间隔时间
     */
    private Integer checkTime = 500;

    /**
     * Mirai 连接
     */
    private final MiraiHttpConn miraiConn;

    /**
     * 初始化 Http 请求轮回
     *
     * @param miraiConn Mirai 连接
     */
    protected MiraiHttpMessageGet(MiraiHttpConn miraiConn){
        this.miraiConn = miraiConn;
    }

    /**
     * 设置轮询间隔时间
     *
     * @param time 时间
     */
    protected void setCheckTime(Integer time) { checkTime = time; }

    @Override
    public void run() {
        //直到机器人取消绑定
        while (miraiConn.isBind()){
            //获取信息队列大小
            Network.NetworkReturn ret = Network.sendGet(
                    miraiConn.getHost() + "/countMessage",
                    "sessionKey=" + miraiConn.session
            );

            //Session 是否过期，过期则等待
            if (ret.code != 3){
                //如果有信息缓存
                if (ret.data.getInteger("data") > 0){
                    //获取头部信息
                    ret = Network.sendGet(
                            miraiConn.getHost() + "/fetchMessage",
                            "sessionKey=" + miraiConn.session + "&count=5"
                    );

                    //Session 是否过期，过期则等待
                    if (ret.code != 3){

                        //遍历所有信息
                        ret.data.getJSONArray("data").forEach((data) -> {
                            JSONObject relData = (JSONObject) data;

                            //尝试获取对应事件或信息
                            //如果获取不到，便为暂不支持，不处理此事件
                            try {
                                String eventPack = "io.github.xiaoyi311.event."
                                        + relData.getString("type")
                                        + (relData.containsKey("messageChain") ? "Event" : "");
                                Constructor<?> eventConst = Class.forName(eventPack).getConstructor(MiraiHttpConn.class, JSONObject.class);
                                MiraiEventBase event = (MessageEventBase) eventConst.newInstance(miraiConn, relData);
                                event.doEvent(miraiConn);
                            } catch (Exception e) { System.out.print(e); }
                        });
                    }
                }
            } else {
                // 尝试刷新 Session 并重新绑定机器人球球
				try {
					miraiConn.refreshSessionKeyAndBindRobot();
				} catch (VerifyKeyError | RobotNotFound e) {
					throw new RuntimeException("刷新 Session 并重新绑定机器人球球失败！", e);
				}
			}

            //等待
            try {
                //noinspection BusyWait
                sleep(checkTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
