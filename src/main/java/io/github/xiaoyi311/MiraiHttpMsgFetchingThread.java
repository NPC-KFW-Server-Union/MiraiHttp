package io.github.xiaoyi311;

import com.alibaba.fastjson.JSONObject;
import io.github.xiaoyi311.err.NetworkIOError;
import io.github.xiaoyi311.err.RobotNotFound;
import io.github.xiaoyi311.err.VerifyKeyError;
import io.github.xiaoyi311.event.MessageEventBase;
import io.github.xiaoyi311.event.MiraiEventBase;
import io.github.xiaoyi311.util.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

/**
 * Http 轮询多线程<br>
 * 使用多线程获取事件与信息
 */
public class MiraiHttpMsgFetchingThread extends Thread{

    private static final Logger logger = LoggerFactory.getLogger("MiraiHttp.MiraiHttpMsgFetchingThread");

    /**
     * 網絡錯誤處理策略
     */
    public enum NetworkErrorStrategy {
        // 拋出異常，終止輪詢線程
        TERMINATE_PROCESS,
        // 不管，並照常進行下一回輪詢
        CONTINUE
    }

    /**
     * Session 過期錯誤處理策略
     */
    public enum SessionOutDateErrorStrategy {
        // 拋出異常，終止輪詢線程
        TERMINATE_PROCESS,
        // 不管，並照常進行下一回輪詢
        CONTINUE,
        // 嘗試刷新 MiraiHttpConn 的 Session
        REFRESH
    }

    /**
     * 查询间隔时间
     */
    private Integer checkTime = 500;

    /**
     * Mirai 连接
     */
    private final MiraiHttpConn miraiConn;

    /**
     * 網絡錯誤處理策略
     */
    private final NetworkErrorStrategy networkErrorStrategy;

    /**
     * Session 過期錯誤處理策略
     */
    private final SessionOutDateErrorStrategy sessionOutDateErrorStrategy;

    /**
     * 初始化 Http 请求轮回
     *
     * @param miraiConn                     Mirai 连接
     * @param networkErrorStrategy          網絡錯誤處理策略
     * @param sessionOutDateErrorStrategy   Session 過期錯誤處理策略
     */
    protected MiraiHttpMsgFetchingThread(MiraiHttpConn miraiConn, NetworkErrorStrategy networkErrorStrategy, SessionOutDateErrorStrategy sessionOutDateErrorStrategy){
        this.miraiConn = miraiConn;
        this.networkErrorStrategy = networkErrorStrategy;
        this.sessionOutDateErrorStrategy = sessionOutDateErrorStrategy;
    }

    /**
     * 设置轮询间隔时间
     *
     * @param time 时间
     */
    protected void setCheckTime(Integer time) { checkTime = time; }

    /**
     * 進行一次 GET 請求，並根據網絡錯誤處理策略處理網絡錯誤。
     *
     * @return 當請求失敗時：
     * 如果網絡錯誤處理策略是 TERMINATE_PROCESS，則直接拋出 RuntimeException，
     * 終止輪詢線程；如果網絡錯誤處理策略是 CONTINUE，則返回空。
     */
    private Network.NetworkReturn sendGetWithHandlingError(String url, String param) {
        try {
            return Network.sendGet(url, param);
        } catch (NetworkIOError e) {
            switch (networkErrorStrategy) {
                case TERMINATE_PROCESS:
                    logger.warn("發送請求 {}, {} 時出錯，根據策略，拋出異常終止子進程。", url, param, e);
                    throw new RuntimeException("輪詢線程因網絡錯誤而終止", e);
                case CONTINUE:
                    logger.warn("發送請求 {}, {} 時出錯，根據策略，跳過本回輪詢。", url, param, e);
            }
        }

        return null;
    }

    private void fetchMsgsAndRaiseEventOnce() {
        //获取信息队列大小
        Network.NetworkReturn ret = sendGetWithHandlingError(
                miraiConn.getHost() + "/countMessage",
                "sessionKey=" + miraiConn.session
        );

        // 請求出錯，跳過本回輪詢。
        if (ret == null) {
            logger.warn("獲取信息隊列大小時發生網絡錯誤，根據策略跳過本回輪詢。");
            return;
        }

        //Session 是否过期，过期则等待
        if (ret.code != 3){
            //如果有信息缓存
            if (ret.data.getInteger("data") > 0){
                //获取头部信息
                ret = sendGetWithHandlingError(
                        miraiConn.getHost() + "/fetchMessage",
                        "sessionKey=" + miraiConn.session + "&count=5"
                );

                // 請求出錯，跳過本回輪詢。
                if (ret == null) {
                    logger.warn("獲取头部信息時發生網絡錯誤，根據策略跳過本回輪詢。");
                    return;
                }

                //Session 是否过期，过期则啥都不幹
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
            // 處理 Session 過期
            logger.warn("MiraiHttpConn 的 session 已經過期。");
            switch (sessionOutDateErrorStrategy) {
                case TERMINATE_PROCESS:
                    logger.warn("根據策略，拋出異常終止子進程。");
                    throw new RuntimeException("輪詢線程因 Mirai 連接的 Session 過期而終止");
                case REFRESH:
                    logger.warn("根據策略，正在嘗試刷新 session。");
                    try {
                        miraiConn.refreshSessionKeyAndBindRobot();
                    } catch (VerifyKeyError | RobotNotFound | NetworkIOError e) {
                        throw new RuntimeException("輪詢線程因刷新 Session 并重新绑定机器人球球失败而終止！", e);
                    }
                    logger.warn("刷新 session 成功了。");
                    break;
                case CONTINUE:
                    logger.warn("根據策略，不管。");
            }
        }
    }

    @Override
    public void run() {
        //直到机器人取消绑定
        while (miraiConn.isBound()){
            fetchMsgsAndRaiseEventOnce();

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
