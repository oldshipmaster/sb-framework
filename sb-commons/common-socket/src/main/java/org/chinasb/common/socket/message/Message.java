package org.chinasb.common.socket.message;

import java.io.Serializable;

import org.chinasb.common.socket.codec.MessageType;

/**
 * 消息抽象
 * @author zhujuan
 *
 */
public abstract class Message implements Serializable {
    private static final long serialVersionUID = 8883352360594899581L;

    /**
     * 默认消息序列号
     */
    public static final int DEFAULT_SN = -1;

    /**
     * 消息序列号
     */
    private int sn = DEFAULT_SN;
    /**
     * 消息编码类型
     */
    private int messageType = MessageType.STRING.ordinal();
    /**
     * 功能模块
     */
    private int module;
    /**
     * 模块指令
     */
    private int cmd;
    /**
     * 消息时间
     */
    private long time = System.currentTimeMillis();
    /**
     * 消息内容
     */
    private Object value;

    /**
     * 获取消息序列号
     * @return
     */
    public int getSn() {
        return sn;
    }

    /**
     * 设置消息序列号
     * @param sn
     */
    public void setSn(int sn) {
        this.sn = sn;
    }

    /**
     * 获取消息类型
     * @return
     */
    public int getMessageType() {
        return messageType;
    }

    /**
     * 设置消息类型
     * @param messageType
     */
    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    /**
     * 获取功能模块
     * @return
     */
    public int getModule() {
        return module;
    }

    /**
     * 设置功能模块
     * @param module
     */
    public void setModule(int module) {
        this.module = module;
    }

    /**
     * 获取模块指令
     * @return
     */
    public int getCmd() {
        return cmd;
    }

    /**
     * 设置模块指令
     * @param cmd
     */
    public void setCmd(int cmd) {
        this.cmd = cmd;
    }

    /**
     * 获取消息时间
     * @return
     */
    public long getTime() {
        return time;
    }

    /**
     * 设置消息时间
     * @param time
     */
    public void setTime(long time) {
        this.time = time;
    }

    /**
     * 获取消息内容
     * @return
     */
    public Object getValue() {
        return value;
    }

    /**
     * 设置消息内容
     * @param value
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public String toString() {
        return "[module=" + this.module + ", cmd=" + this.cmd + ", messageType=" + this.messageType
                + ", sn=" + this.sn + ", value=" + this.value + "]";
    }
}
