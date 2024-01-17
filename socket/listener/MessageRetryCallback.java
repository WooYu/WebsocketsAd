package com.oohlink.player.sdk.socket.listener;

import com.oohlink.player.sdk.socket.MessageRetryTask;

/**
 * 消息重发回调
 */
public interface MessageRetryCallback {
    /**
     * 重试失败
     */
    void retryFailed(MessageRetryTask sendTask);

    /**
     * 重发消息
     */
    void resendMsg(MessageRetryTask sendTask);
}
