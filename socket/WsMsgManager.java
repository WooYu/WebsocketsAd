package com.oohlink.player.sdk.socket;

import com.oohlink.player.sdk.socket.bean.WebSocketCodeMessage;
import com.oohlink.player.sdk.socket.bean.WebSocketReqMsg;
import com.oohlink.player.sdk.socket.bean.WebSocketRespMsg;
import com.oohlink.player.sdk.util.GsonUtils;

import java.util.UUID;

/**
 * 解析消息和生成消息
 */
public class WsMsgManager {

    //##################常量定义#######################
    /**
     * 业务类型定义
     */
    /**
     * 心跳:心跳消息，与WebSocket Server进行心跳收发
     */
    public static String HeartBeatMessage = "HeartBeat";

    /**
     * 上行消息:主动上行消息，用于业务客户端向业务服务端上传消息
     */
    private final String UpMessage = "UpMessage";

    /**
     * 上行消息:主动下行消息，用于业务服务端向业务客户端下发消息
     */
    public static String DnMessage = "DnMessage";


    /**
     * 节点id定义
     */
    /**
     * 业务客户端：广告SDK
     */
    private String NODE_ID_1001 = "1001";

    /**
     * 业务服务端：广告平台
     */
    private String NODE_ID_1002 = "1002";

    //##################单例#######################
    private WsMsgManager() {
    }

    private static class SingletonHolder {
        private static final WsMsgManager INSTANCE = new WsMsgManager();
    }

    public static WsMsgManager getInstance() {
        return SingletonHolder.INSTANCE;
    }


    //##################生成消息参数##################

    /**
     * 生成上行消息指令
     */
    private String cmdGenerate(String cmd) {
        return cmdGenerate(cmd, null, null, null);
    }

    /**
     * 生成上行消息指令
     */
    private String cmdGenerate(String cmd, String source, String target, String data) {
        String seq = getSeq();
        return cmdGenerate(cmd, seq, source, target, data);
    }

    /**
     * 生成上行消息指令
     */
    private String cmdGenerate(String cmd, String seq, String source, String target, String data) {
        WebSocketReqMsg downCmd = new WebSocketReqMsg(cmd, seq, source, target, data);
        return GsonUtils.toJson(downCmd);
    }

    /**
     * 生成下行消息指令（消息回执）
     */
    private String dnMsgCmdGenerate(String cmd, String seq, String source, String target, String data, String code, String message) {
        WebSocketRespMsg downCmd = new WebSocketRespMsg(cmd, seq, source, target, data, code, message);
        return GsonUtils.toJson(downCmd);
    }
    //##################对外暴露的方法##################

    /**
     * 获取seq
     */
    public String getSeq() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成心跳指令
     */
    public String generateHeartBeatCmd(String seq) {
        return cmdGenerate(HeartBeatMessage, seq, null, null, null);
    }

    /**
     * 生成上行指令:发送消息回执
     */
    public String generateUpCmdOfReceipt(String seq, String source, String target) {
        return dnMsgCmdGenerate(UpMessage, seq, source, target, "",
                WebSocketCodeMessage.SUCCESS.getCode(), WebSocketCodeMessage.SUCCESS.getMessage());
    }

    /**
     * 生成上行指令:广告SDK发消息给广告平台
     */
    public String generateUpCmdOfSendData(String seq, String data) {
        return cmdGenerate(UpMessage, seq, NODE_ID_1001, NODE_ID_1002, data);
    }
}
