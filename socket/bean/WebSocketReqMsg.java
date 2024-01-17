package com.oohlink.player.sdk.socket.bean;

/**
 * 请求消息
 */
public class WebSocketReqMsg {
    /**
     * 业务类型
     */
    private String cmd;
    /**
     * 消息序列，递增，到最⼤值后归零
     */
    private String seq;
    /**
     * 源地址，表明发送方，参考《节点ID定义》
     */
    private String source;
    /**
     * 目的地址，表明接收方，参考《节点ID定义》
     */
    private String target;
    /**
     * 数据，json格式，若⽆特别说明可为空
     */
    private String data;

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getSeq() {
        return seq;
    }

    public void setSeq(String seq) {
        this.seq = seq;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public WebSocketReqMsg(String cmd, String seq, String source, String target, String data) {
        this.cmd = cmd;
        this.seq = seq;
        this.source = source;
        this.target = target;
        this.data = data;
    }

    @Override
    public String toString() {
        return "WebSocketRequestParam{" +
                "cmd='" + cmd + '\'' +
                ", seq='" + seq + '\'' +
                ", source='" + source + '\'' +
                ", target='" + target + '\'' +
                ", data='" + data + '\'' +
                '}';
    }
}


