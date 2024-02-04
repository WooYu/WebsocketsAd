package com.oohlink.player.sdk.socket.listener;

import com.oohlink.player.sdk.socket.bean.WebSocketRespMsg;

/**
 * webSocket消息回调
 */
public interface WsMsgCallback {
    /**
     * 连接已建立
     */
    void onConnected();

    /**
     * 连接已断开
     */
    void onDisconnected();

    /**
     * 接受新消息
     */
    void onMessageReceived(WebSocketRespMsg respMsg);

    /**
     * 消息发送失败
     */
    void onMessageSendFailed(String seq, String content);

    /**
     * 消息发送成功（接受到回执）
     */
    void onMessageSendSuccess(String seq, String content);
}
