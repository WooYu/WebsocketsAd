package com.oohlink.player.sdk.socket

enum class ConnectionState {
    /**
     * 连接中
     */
    CONNECTING,

    /**
     * 已连接
     */
    CONNECTED,

    /**
     * 未连接
     */
    DISCONNECTED
}