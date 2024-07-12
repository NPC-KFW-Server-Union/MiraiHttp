package io.github.xiaoyi311;

import com.alibaba.fastjson.JSONObject;
import io.github.xiaoyi311.entity.Robot;
import io.github.xiaoyi311.err.*;
import io.github.xiaoyi311.util.Network;

/**
 * 一个到 Mirai 服务器的连接。
 */
public class MiraiHttpConn {
    /**
     * 连接地址
     */
    private final String host;

    /**
     * 验证密钥
     */
    private final String verifyKey;

    /**
     * 綁定的机器人球球号
     */
    protected Long boundRobotQq;

    /**
     * 轮询
     */
    private MiraiHttpMsgFetchingThread msgGettingThread;

    /**
     * Session 信息
     */
    protected String session;


    /**
     * 網絡錯誤處理策略
     */
    private final MiraiHttpMsgFetchingThread.NetworkErrorStrategy networkErrorStrategy;

    /**
     * Session 過期錯誤處理策略
     */
    private final MiraiHttpMsgFetchingThread.SessionOutDateErrorStrategy sessionOutDateErrorStrategy;


    /**
     * 创建 MiraiHttpConn
     *
     * @param verifyKey                     密钥
     * @param host                          地址，类似于：127.0.0.1:8080
     * @param networkErrorStrategy          網絡錯誤處理策略
     * @param sessionOutDateErrorStrategy   Session 過期錯誤處理策略
     * @throws VerifyKeyError 验证密钥错误
     */
    protected MiraiHttpConn(
            String verifyKey,
            String host,
            MiraiHttpMsgFetchingThread.NetworkErrorStrategy networkErrorStrategy,
            MiraiHttpMsgFetchingThread.SessionOutDateErrorStrategy sessionOutDateErrorStrategy
    ) throws VerifyKeyError {
        this.host = host;
        this.verifyKey = verifyKey;
        this.networkErrorStrategy = networkErrorStrategy;
        this.sessionOutDateErrorStrategy = sessionOutDateErrorStrategy;
        this.session = getSessionKey();
    }

    /**
     * 创建 MiraiHttpConn，并绑定机器人<br>
     * 需要快速绑定监听器
     *
     * @param verifyKey                     密钥
     * @param host                          地址，类似于：127.0.0.1:8080
     * @param qq                            机器人 QQ 号
     * @param networkErrorStrategy          網絡錯誤處理策略
     * @param sessionOutDateErrorStrategy   Session 過期錯誤處理策略
     * @throws VerifyKeyError 验证密钥错误
     * @throws RobotNotFound  机器人未找到
     */
    protected MiraiHttpConn(
            String verifyKey,
            String host,
            Long qq,
            MiraiHttpMsgFetchingThread.NetworkErrorStrategy networkErrorStrategy,
            MiraiHttpMsgFetchingThread.SessionOutDateErrorStrategy sessionOutDateErrorStrategy
    ) throws VerifyKeyError, RobotNotFound, NetworkIOError {
        this.host = host;
        this.verifyKey = verifyKey;
        this.networkErrorStrategy = networkErrorStrategy;
        this.sessionOutDateErrorStrategy = sessionOutDateErrorStrategy;
        this.session = getSessionKey();
        try {
            bindRobot(qq);
        } catch (SessionOutDate e) {
            //获取完直接绑定也能失效？这就离谱了，直接丢错吧，我不管了
            throw new RuntimeException(e);
        } catch (SessionIsBind e) {
            //我刚新建的也能被绑定！？！？离谱死我了
            throw new RuntimeException(e);
        }
    }


    /**
     * 刷新 SessionKey 并重新绑定机器人。
     */
    protected void refreshSessionKeyAndBindRobot() throws VerifyKeyError, RobotNotFound, NetworkIOError {
        this.session = getSessionKey();
        try {
            if(this.isBound()) {
                long qq = boundRobotQq;
                unbind();
                bindRobot(qq);
            }
        } catch (SessionOutDate e) {
            //获取完直接绑定也能失效？这就离谱了，直接丢错吧，我不管了
            throw new RuntimeException(e);
        } catch (SessionIsBind e) {
            //我刚新建的也能被绑定！？！？离谱死我了
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取 Session
     *
     * @return                Session
     * @throws VerifyKeyError 验证密钥错误
     */
    private String getSessionKey() throws VerifyKeyError {
        //构建参数
        JSONObject data = new JSONObject();
        data.put("verifyKey", verifyKey);

        //获取 Session
        Network.NetworkReturn ret = Network.sendPost(host + "/verify", data.toJSONString());

        //是否验证密钥错误
        if (ret.code == 1){
            throw new VerifyKeyError(verifyKey);
        }
        return ret.data.getString("session");
    }

    /**
     * 启动 Http 轮询，获取事件信息
     */
    private void launchMsgGettingThread() {
        if (msgGettingThread == null || !msgGettingThread.isAlive()) {
            msgGettingThread = new MiraiHttpMsgFetchingThread(this, networkErrorStrategy, sessionOutDateErrorStrategy);
            msgGettingThread.start();
        }
    }

    /**
     * 获取连接地址
     *
     * @return 链接地址
     */
    public String getHost() {
        return host;
    }

    /**
     * 是否绑定机器人完成
     *
     * @return 是否完成
     */
    public boolean isBound() {
        return boundRobotQq != null;
    }

    /**
     * 解除 qq 號的綁定，並使 MiraiHttpConn 無效。一旦調用此函數，此类對象不应再使用，需重新新建<br>
     */
    public void unbind() throws NetworkIOError {
        releaseSession();

        //释放 Session

        boundRobotQq = null;
    }

    /**
     * 釋放 Session，用於解綁機器人 qq，或刷新 session。<br>
     * 在绑定成功以前不应该释放，会抛出未绑定错误
     */
    private void releaseSession() throws NetworkIOError {
        //Session 是否绑定机器人
        if (!isBound()){
            throw new SessionNotBind();
        }

        //构建参数
        JSONObject data = new JSONObject();
        data.put("verifyKey", verifyKey);
        data.put("qq", getRobot().qq);

        Network.sendPost(host + "/release", data.toJSONString());
    }

    /**
     * 绑定到机器人<br>
     * 一旦绑定，事件系统就会启动，所以需在绑定前绑定事件
     *
     * @param qq               指定机器人 QQ
     * @throws RobotNotFound   指定机器人未找到
     * @throws SessionIsBind   Session 已绑定到某个 QQ，不可重复绑定
     * @throws SessionNotBind  Session 已失效，需要重新新建
     */
    public Robot bindRobot(Long qq) throws RobotNotFound, SessionIsBind, SessionOutDate, NetworkIOError {
        //Session 是否绑定机器人
        if (isBound()){
            throw new SessionIsBind();
        }

        //构建参数
        JSONObject data = new JSONObject();
        data.put("sessionKey", session);
        data.put("qq", qq);

        //绑定
        Network.NetworkReturn ret = Network.sendPost(
                host + "/bind",
                data.toJSONString()
        );

        //机器人是否存在
        if (ret.code == 2){
            throw new RobotNotFound(qq);
        }

        //Session 是否失效
        if (ret.code == 3){
            throw new SessionOutDate();
        }

        boundRobotQq = qq;
        launchMsgGettingThread();
        return getRobot();
    }

    /**
     * 获取机器人信息<br>
     * 在绑定成功以前不应该获取，会抛出未绑定错误
     *
     * @return 机器人实体
     */
    public Robot getRobot() throws NetworkIOError {
        //Session 是否绑定机器人
        if (!isBound()){
            throw new SessionNotBind();
        }

        //获取会话信息
        Network.NetworkReturn ret = Network.sendGet(
                host + "/sessionInfo",
                "sessionKey=" + session
        );

        //制作回参
        Robot robot = new Robot();
        robot.qq = ret.data.getLong("id");
        robot.name = ret.data.getString("nickname");
        robot.remark = ret.data.getString("remark");
        robot.conn = this;
        return robot;
    }

    /**
     * 获取 Api 管理器<br>
     * 可进行 Api 操作
     *
     * @return Api 管理器
     */
    public MiraiHttpApi getApi(){
        return new MiraiHttpApi(this);
    }

    /**
     * 设置新信息查询间隔时间
     * @param time 时间（毫秒）
     */
    public void setCheckTime(Integer time){ msgGettingThread.setCheckTime(time); }
}
