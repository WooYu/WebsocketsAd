package com.oohlink.player.sdk.manager.dsp

import android.text.TextUtils
import com.oohlink.player.sdk.common.LogTag
import com.oohlink.player.sdk.dataRepository.DefaultSharedPreferenceDao
import com.oohlink.player.sdk.dataRepository.PlayerDataRepository
import com.oohlink.player.sdk.dataRepository.local.room.entity.DspAdvertiserRequestParamsEntity
import com.oohlink.player.sdk.dataRepository.local.room.entity.DspStrategyPeriodTimeEntity
import com.oohlink.player.sdk.dataRepository.remote.http.HttpManager
import com.oohlink.player.sdk.dataRepository.remote.http.entities.DspAdMaterial
import com.oohlink.player.sdk.manager.DeviceActiveManager
import com.oohlink.player.sdk.manager.dsp.bean.DspSupplierRequestResultBean
import com.oohlink.player.sdk.util.Logger
import com.oohlink.player.sdk.util.SdkTimeUtils
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.json.JSONObject

/**
 * 广告供应商策略
 */
object DspAdvertiserPolicyManager {
    private const val logTag = LogTag.DSP_POLICY

    /**
     * 广告供应商生成接口请求
     */
    private val advertiserApiManager by lazy {
        DspAdvertiserApiManager.getInstance()
    }

    /**
     * 获取设备信息
     */
    private val deviceActiveManager by lazy {
        DeviceActiveManager.getInstance()
    }

    /**
     * 获取接口数据或者数据库的数据
     */
    private val playerDataRepository by lazy {
        PlayerDataRepository.getInstance()
    }

    /**
     * 供应商请求参数
     */
    private var disposableOfVendorRequestParams: Disposable? = null

    /**
     * 播放策略的
     */
    private var disposableOfPlayStrategy: Disposable? = null

    /**
     * 供应商请求参数
     */
    private var disposableOfHandleEnvChange: Disposable? = null

    fun initialize() {
        Logger.d(logTag, "DspAdvertiserPolicyManager->initialize()")
        handleEnvironmentChange("initialize()")
    }

    fun destroy(logPrefix: String) {
        Logger.d(logTag, "DspAdvertiserPolicyManager->stop()->from = $logPrefix")
        destroyDisposable(disposableOfPlayStrategy)
        destroyDisposable(disposableOfVendorRequestParams)
        destroyDisposable(disposableOfHandleEnvChange)
        advertiserApiManager.cancelAllApiCalls()
    }

    /**
     * 需要重新请求程序化广告播放策略
     */
    fun handleEnvironmentChange(logPrefix: String) {
        Logger.d(logTag, "handleEnvironmentChange()->logPrefix = $logPrefix")
        val token = DefaultSharedPreferenceDao.getInstance().deviceToken
        if (TextUtils.isEmpty(token)) {
            Logger.e(logTag, "handleEnvironmentChange()->token is empty!")
            return
        }
        try {
            // 异步处理请求，使用Completable作为返回值，可以更好地处理请求完成、错误等情况
            destroyDisposable(disposableOfHandleEnvChange)
            disposableOfHandleEnvChange = Completable
                .fromAction {
                    val playPolicyCompletable = requestDspPlayPolicyData()
                    val advertiserRequestCompletable = requestDspAdvertiserRequestMethod()

                    // 合并请求的Completable，使用andThen按顺序执行
                    playPolicyCompletable.andThen(advertiserRequestCompletable).blockingAwait()
                }
                .subscribeOn(Schedulers.io()) // 在io线程中执行异步任务
                .observeOn(AndroidSchedulers.mainThread()) // 在主线程中处理结果
                .subscribe({
                    Logger.d(logTag, "handleEnvironmentChange()->onComplete()")
                }, { error ->
                    error.printStackTrace()
                    Logger.d(logTag, "handleEnvironmentChange()->onError(): ${error.message}")
                })
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.e(logTag, "handleEnvironmentChange()->Abnormal!")
        }
    }

    /**
     * 获取对应时间段的广告素材
     */
    fun getCreativesForTimePeriod(
        playCode: String,
        adId: String,
        startTime: Long,
        endTime: Long,
        timeFrame: Long
    ): Observable<List<DspAdMaterial>> {
        Logger.d(
            logTag,
            "getCreativesForTimePeriod(), playerCode = $playCode , adId = $adId ," +
                    " startTime = $startTime or ${SdkTimeUtils.secondsToFormattedTime(startTime.toString())}, " +
                    "endTime = $endTime or ${SdkTimeUtils.secondsToFormattedTime(endTime.toString())}, " +
                    "timeFrame = $timeFrame"
        )
        val stopSignal = PublishSubject.create<Unit>()
        return getDspPlayPolicyData(playCode, startTime, endTime).concatMap { dspCodes ->
            Observable.fromIterable(dspCodes).concatMap { dspCode ->
                getDspAdvertiserRequestMethod(dspCode).concatMap { entity ->
                    Logger.e(
                        logTag,
                        "getCreativesForTimePeriod() $dspCode Ad Vendor Request Parameters entity = $entity"
                    )
                    val adSupplierRequestResult = DspSupplierRequestResultBean(
                        playCode,
                        adId,
                        dspCode,
                        ""
                    )
                    getAdvertiserDataIfNotEmpty(
                        entity,
                        adSupplierRequestResult,
                        timeFrame
                    ).takeUntil(stopSignal)
                }
            }.filter { result ->
                val shouldStop = result.isNotEmpty() && result[0].id.isNotEmpty()
                if (shouldStop) {
//                    Logger.d(logTag, "getCreativesForTimePeriod() shouldStop!")
                    stopSignal.onNext(Unit)
                }
                shouldStop
            }.firstElement().toObservable()
        }
    }
    //###########保留给外界的方法End############

    private fun destroyDisposable(disposable: Disposable?) {
        if (disposable?.isDisposed == false) {
            disposable.dispose()
        }
    }

    /**
     * 请求各时段的程序化广告播放策略
     */
    private fun requestDspPlayPolicyData(): Completable {
        val macAddress = deviceActiveManager.playCode
//        Logger.d(logTag, "requestDspPlayPolicyData()->playCode=$macAddress")
        destroyDisposable(disposableOfPlayStrategy)

        if (TextUtils.isEmpty(macAddress)) {
            return Completable.complete()
        }
        return Completable.create { emitter ->
            disposableOfPlayStrategy =
                HttpManager.getInstance().getDspAdvertiserStrategy(macAddress)
                    .flatMap { response ->
                        //各时段的使用的广告商
                        val periodTimeStrategyList = mutableListOf<DspStrategyPeriodTimeEntity>()
                        if (TextUtils.isEmpty(response.playcode)) {
                            return@flatMap Observable.just(periodTimeStrategyList)
                        }
                        //periodTimes 不为空，组装DspStrategyPeriodTimeEntity存储到数据库
                        response.periodTimes?.forEachIndexed { _, periodTimeInfo ->
                            if (periodTimeInfo.prioritys?.isEmpty() == true) {
                                return@forEachIndexed
                            }
                            periodTimeStrategyList.add(
                                DspStrategyPeriodTimeEntity(
                                    0,
                                    response.playcode!!,
                                    periodTimeInfo.beginTime,
                                    periodTimeInfo.endTime,
                                    periodTimeInfo.prioritys?.joinToString(separator = ",") ?: ""
                                )
                            )
                        }
                        Observable.just(periodTimeStrategyList)
                    }.observeOn(Schedulers.io())
                    .doOnNext {
                        Logger.d(
                            logTag,
                            "requestDspPlayPolicyData()->Returned playback policy size = " + it.size
                        )
                        //清除数据
                        playerDataRepository.deleteDspPlayStrategyOfEachTime()
                        //存储到数据库
                        playerDataRepository.saveDspPlayPriorityOfEachTime(it)
                    }
                    .observeOn(AndroidSchedulers.mainThread()) // Observe on the main thread for UI updates (optional)
                    .subscribe({
                        Logger.d(
                            logTag,
                            "requestDspPlayPolicyData()->The playback strategy of each time period is successfully stored in the database."
                        )
                        emitter.onComplete()
                    }, {
                        Logger.e(
                            logTag,
                            "The playback strategy of each time period is stored in the database, and an exception occurs!"
                        )
                        emitter.onError(it)
                    })
        }
    }

    /**
     * 请求广告供应商的请求方法
     */
    private fun requestDspAdvertiserRequestMethod(): Completable {
        destroyDisposable(disposableOfVendorRequestParams)
        val macAddress = deviceActiveManager.playCode

        if (TextUtils.isEmpty(macAddress)) {
            return Completable.complete()
        }
        return Completable.create { emitter ->
            disposableOfVendorRequestParams =
                HttpManager.getInstance().getDspAdvertiserRequestParams(macAddress)
                    .observeOn(Schedulers.io())
                    .doOnNext {
                        //清除数据
                        playerDataRepository.deleteDspAdvertiserRequestParams()
                        //供应商请求参数存储到数据库
                        playerDataRepository.saveDspAdvertiserRequestParams(it)
                    }
                    .observeOn(AndroidSchedulers.mainThread()) // Observe on the main thread for UI updates (optional)
                    .subscribe({
                        Logger.d(
                            logTag,
                            "requestDspAdvertiserRequestMethod()->Supplier request parameters are stored to the database."
                        )
                        emitter.onComplete()
                    }, {
                        Logger.e(
                            logTag,
                            "requestDspAdvertiserRequestMethod()->Error storing supplier request parameters to database!"
                        )
                        it.printStackTrace()
                        emitter.onError(it)
                    })
        }
    }

    /**
     * 从数据库获取各时段的播放策略
     * "prioritys":["visterMidea","hiveStack"]
     */
    private fun getDspPlayPolicyDataFromDatabase(
        playCode: String,
        beginTime: Long,
        endTime: Long
    ): Observable<List<String>> {
        return Observable.fromCallable {
            val strategy =
                playerDataRepository.getDspAdvertisersByTime(playCode, beginTime, endTime)
//            Logger.d(logTag, "getDspPlayPolicyDataFromDatabase() strategy = $strategy")
            strategy?.priority?.split(",") ?: emptyList()
        }.subscribeOn(Schedulers.io())
    }

    /**
     * 从数据库获取广告供应商的请求参数
     */
    private fun getDspAdvertiserRequestParamsFromDatabase(dspCode: String): Observable<DspAdvertiserRequestParamsEntity?> {
        return Observable.fromCallable {
            val entity = playerDataRepository.getDspAdvertiserRequestParams(dspCode)
            return@fromCallable entity ?: DspAdvertiserRequestParamsEntity()
        }.subscribeOn(Schedulers.io())
    }

    /**
     * 从数据库读取缓存的广告供应商请求参数，并生成请求
     */
    private fun getAdvertiserData(entity: DspAdvertiserRequestParamsEntity?): Observable<String?> {
        return advertiserApiManager.executeRequest(
            entity?.requestMethod,
            entity?.requestUrl,
            entity?.requestBody,
            entity?.requestHeader
        )
    }

    /**
     * 获取各时段的播放策略
     */
    private fun getDspPlayPolicyData(
        playCode: String,
        beginTime: Long,
        endTime: Long
    ): Observable<List<String>> {
        // 从数据库获取数据
        return getDspPlayPolicyDataFromDatabase(
            playCode,
            beginTime,
            endTime
        ).flatMap { databaseResult ->
//            Logger.d(logTag, "getDspPlayPolicyData() databaseResult = $databaseResult")
            if (databaseResult.isNotEmpty()) {
                // 如果数据库中有数据，则直接返回数据库结果
                Observable.just(databaseResult)
            } else {
                // 如果数据库中无数据，则从网络获取数据
                requestDspPlayPolicyData().andThen(
                    getDspPlayPolicyDataFromDatabase(playCode, beginTime, endTime)
                )
            }
        }
    }

    /**
     * 获取广告供应商的请求参数
     */
    private fun getDspAdvertiserRequestMethod(dspCode: String): Observable<DspAdvertiserRequestParamsEntity?> {
        return getDspAdvertiserRequestParamsFromDatabase(dspCode)
            .flatMap { databaseResult ->
//                Logger.d(
//                    logTag,
//                    "getDspAdvertiserRequestMethod() dspCode = $dspCode , databaseResult = $databaseResult"
//                )
                if (TextUtils.isEmpty(databaseResult.requestUrl)) {
                    // 如果数据库中无数据，则从网络获取数据
                    requestDspAdvertiserRequestMethod().andThen(
                        getDspAdvertiserRequestParamsFromDatabase(dspCode)
                    )
                } else {
                    // 如果数据库中有数据，则直接返回数据库结果
                    Observable.just(databaseResult)
                }
            }
    }

    /**
     * 获取是否请求到供应商的广告数据adContent
     */
    private fun getAdvertiserDataIfNotEmpty(
        entity: DspAdvertiserRequestParamsEntity?,
        adSupplierRequestResult: DspSupplierRequestResultBean,
        timeFrame: Long
    ): Observable<List<DspAdMaterial>> {
        if (TextUtils.isEmpty(entity?.requestUrl)) {
            return Observable.just(emptyList())
        }

        //特殊处理
        if (TextUtils.equals(adSupplierRequestResult.dspCode, "VistarMedia")) {
            entity?.requestBody = resetDisplayTime(entity?.requestBody, timeFrame)
        }

        return getAdvertiserData(entity).flatMap { adContent ->
//            Logger.d(
//                logTag,
//                "getAdvertiserData() result = $adContent ! adSupplierRequestResult = $adSupplierRequestResult"
//            )
            adSupplierRequestResult.adContent = adContent

            PlayerDataRepository.getInstance().getProgrammeAdRemoteMaterial(
                adSupplierRequestResult.adId,
                adSupplierRequestResult.dspCode,
                adSupplierRequestResult.adContent
            )
        }.onErrorReturn {
            Logger.e(
                logTag,
                "getAdvertiserData() error! adSupplierRequestResult = $adSupplierRequestResult"
            )
            it.printStackTrace()
            emptyList<DspAdMaterial>()
        }
    }

    /**
     * VistarMedia广告纠正时间，display_time字段的特殊处理
     */
    private fun resetDisplayTime(jsonString: String?, timeFrame: Long): String? {
        try {
            if (TextUtils.isEmpty(jsonString)) {
                return jsonString
            }
            val jsonObject = JSONObject(jsonString!!)
            if (jsonObject.has("display_time")) {
                //要转换成秒
                val newDisplayTime = System.currentTimeMillis() / 1000 + timeFrame
                jsonObject.put("display_time", newDisplayTime)
//                Logger.d(logTag, "resetDisplayTime() newDisplayTime = $newDisplayTime")
            }
            return jsonObject.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return jsonString
        }
    }
}