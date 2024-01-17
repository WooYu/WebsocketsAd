package com.oohlink.player.sdk.socket.bean;

/**
 * WebSocket配置类
 */
public class WebSocketConfig {
    private String token;
    private String url;
    /**
     * 默认连接超时时间
     */
    private int connectTimeout = 30;
    /**
     * 默认通信超时时间
     */
    private int communicationTimeout = 60;
    /**
     * 默认心跳间隔时间
     */
    private int heartbeatInterval = 10;
    /**
     * 连接未建立时，最大重连次数
     */
    private int maxRetryCountOfReconnect = 10;
    /**
     * 默认允许丢失的心跳次数
     */
    private int maxHeartbeatLoss = 5;
    /**
     * 发送消息的最大重发次数
     */
    private int maxRetryCountOfResendMsg = 3;

    /**
     * 业务消息的最大缓存容量
     * 3600 按照消息60s超时计算，最大并发60条/秒
     */
    private int maxCapacityOfBusinessMsg = 3600;

    public WebSocketConfig url(String url) {
        this.url = url;
        return this;
    }

    public WebSocketConfig token(String token) {
        this.token = token;
        return this;
    }

    public WebSocketConfig connectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public WebSocketConfig communicationTimeout(int communicationTimeout) {
        this.communicationTimeout = communicationTimeout;
        return this;
    }

    public WebSocketConfig heartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
        return this;
    }

    public WebSocketConfig maxRetryCountOfReconnect(int maxRetryCountOfReconnect) {
        this.maxRetryCountOfReconnect = maxRetryCountOfReconnect;
        return this;
    }

    public WebSocketConfig maxHeartbeatLoss(int maxHeartbeatLoss) {
        this.maxHeartbeatLoss = maxHeartbeatLoss;
        return this;
    }

    public WebSocketConfig maxRetryCountOfResendMsg(int maxRetryCountOfResendMsg) {
        this.maxRetryCountOfResendMsg = maxRetryCountOfResendMsg;
        return this;
    }

    public WebSocketConfig maxCapacityOfBusinessMsg(int maxCapacityOfBusinessMsg) {
        this.maxCapacityOfBusinessMsg = maxCapacityOfBusinessMsg;
        return this;
    }

    //##############Get/Set##############
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getCommunicationTimeout() {
        return communicationTimeout;
    }

    public void setCommunicationTimeout(int communicationTimeout) {
        this.communicationTimeout = communicationTimeout;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public int getMaxRetryCountOfReconnect() {
        return maxRetryCountOfReconnect;
    }

    public void setMaxRetryCountOfReconnect(int maxRetryCountOfReconnect) {
        this.maxRetryCountOfReconnect = maxRetryCountOfReconnect;
    }

    public int getMaxHeartbeatLoss() {
        return maxHeartbeatLoss;
    }

    public void setMaxHeartbeatLoss(int maxHeartbeatLoss) {
        this.maxHeartbeatLoss = maxHeartbeatLoss;
    }

    public int getMaxRetryCountOfResendMsg() {
        return maxRetryCountOfResendMsg;
    }

    public void setMaxRetryCountOfResendMsg(int maxRetryCountOfResendMsg) {
        this.maxRetryCountOfResendMsg = maxRetryCountOfResendMsg;
    }

    public int getMaxCapacityOfBusinessMsg() {
        return maxCapacityOfBusinessMsg;
    }

    public void setMaxCapacityOfBusinessMsg(int maxCapacityOfBusinessMsg) {
        this.maxCapacityOfBusinessMsg = maxCapacityOfBusinessMsg;
    }

    @Override
    public String toString() {
        return "WebSocketConfig{" +
                "token='" + token + '\'' +
                ", url='" + url + '\'' +
                ", connectTimeout=" + connectTimeout +
                ", communicationTimeout=" + communicationTimeout +
                ", heartbeatInterval=" + heartbeatInterval +
                ", maxRetryCountOfReconnect=" + maxRetryCountOfReconnect +
                ", maxHeartbeatLoss=" + maxHeartbeatLoss +
                ", maxRetryCountOfResendMsg=" + maxRetryCountOfResendMsg +
                ", maxCapacityOfBusinessMsg=" + maxCapacityOfBusinessMsg +
                '}';
    }
}
