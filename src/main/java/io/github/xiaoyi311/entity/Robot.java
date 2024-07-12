package io.github.xiaoyi311.entity;

import io.github.xiaoyi311.MiraiHttpConn;

/**
 * 机器人实体
 */
public class Robot {
    /**
     * Mirai 连接
     */
    public MiraiHttpConn conn;

    /**
     * QQ 号
     */
    public Long qq;

    /**
     * 名称
     */
    public String name;

    /**
     * 签名
     */
    public String remark;
}
