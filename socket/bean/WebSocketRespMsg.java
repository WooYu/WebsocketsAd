package com.oohlink.player.sdk.socket.bean;

/**
 * 响应体
 */
public class WebSocketRespMsg {

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
    private Object data;
    /**
     * 状态码，200表明成功
     */
    private String code;
    /**
     * 状态信息描述
     */
    private String message;

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

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public WebSocketRespMsg(String cmd, String seq, String source, String target, Object data, String code, String message) {
        this.cmd = cmd;
        this.seq = seq;
        this.source = source;
        this.target = target;
        this.data = data;
        this.code = code;
        this.message = message;
    }

    @Override
    public String toString() {
        return "WebSocketResponseMsg{" +
                "cmd='" + cmd + '\'' +
                ", seq='" + seq + '\'' +
                ", source='" + source + '\'' +
                ", target='" + target + '\'' +
                ", data=" + data +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
