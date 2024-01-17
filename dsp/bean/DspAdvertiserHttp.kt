package com.oohlink.player.sdk.manager.dsp.bean

/**
 * 程序化广告供应商相关业务请求的响应
 */

/**
 * 获取指定设备的程序化策略
 */
data class AdvertDspStrategyResponse(
    val playcode: String?,
    val country: String?,
    val city: String?,
    val timeZone: String?,
    val periodTimes: List<DspStrategyPeriodTimeBean>?
)
