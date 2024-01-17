package com.oohlink.player.sdk.manager.dsp.bean

/**
 * 程序化广告供应商相关业务请求响应的data字段
 */
/**
 * 程序化广告播放，各时段的广告供应商
 * eg:
 *   {"beginTime":"时间戳","endTime":"时间戳","prioritys":["visterMidea","hiveStack"]}
 */
data class DspStrategyPeriodTimeBean(
    val beginTime: Long,
    val endTime: Long,
    val prioritys: List<String>?
)

/**
 * 程序化广告的广告供应商请求结果
 */
data class DspSupplierRequestResultBean(
    val playCode: String,
    val adId: String,
    val dspCode: String,
    var adContent: String
) {
    constructor() : this("", "", "", "")
}