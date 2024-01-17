package com.oohlink.player.sdk.socket;

import com.oohlink.player.sdk.socket.listener.MessageRetryCallback;

/**
 * 消息重试
 */
public class MessageRetryTask implements Runnable {
    private final String seq;
    /**
     * 原始发送的消息
     */
    private final String originMsg;
    /**
     * 剩余重试次数
     */
    private int retriesLeft;

    private MessageRetryCallback retryResultCallback;

    public MessageRetryTask(String seq, String originMsg, int retriesLeft) {
        this.seq = seq;
        this.originMsg = originMsg;
        this.retriesLeft = retriesLeft;
    }

    public String getSeq() {
        return seq;
    }

    public String getOriginMsg() {
        return originMsg;
    }

    public int getRetriesLeft() {
        return retriesLeft;
    }

    public void setRetryResultCallback(MessageRetryCallback retryResultCallback) {
        this.retryResultCallback = retryResultCallback;
    }

    @Override
    public void run() {
        if (retriesLeft > 0) {
            if (null != retryResultCallback) {
                retryResultCallback.resendMsg(this);
            }
            retriesLeft--;
        } else {
            if (null != retryResultCallback) {
                retryResultCallback.retryFailed(this);
            }
            cancel();
        }
    }

    /**
     * 消息发生成功时，取消重试
     */
    public void cancel() {
        retriesLeft = 0;
        retryResultCallback = null;
    }
}
