package com.oohlink.player.sdk.socket.bean

/**
 * 状态码，200表明成功
 */
enum class WebSocketCodeMessage(val code: String, val message: String) {
    SUCCESS("200", "Success"),
    ERROR("500", "Internal Server Error")
    // 其他常量键值对
}