package com.oohlink.player.sdk.manager.dsp

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.oohlink.player.sdk.common.PlayerConstants
import com.oohlink.player.sdk.dataRepository.remote.http.HttpAdVendorApi
import com.oohlink.player.sdk.dataRepository.remote.http.HttpManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DspAdvertiserApiManager private constructor() {

    companion object {
        @Volatile
        private var instance: DspAdvertiserApiManager? = null

        fun getInstance(): DspAdvertiserApiManager {
            return instance ?: synchronized(this) {
                instance ?: DspAdvertiserApiManager().also { instance = it }
            }
        }
    }

    private val compositeDisposable = CompositeDisposable()

    fun cancelAllApiCalls() {
        compositeDisposable.clear()
    }

    private fun <T> makeApiCall(call: Call<T>): Observable<String> {
        return Observable.create { emitter ->
            val clonedCall = call.clone() // Clone the call to avoid reusing it

            // Execute the call asynchronously
            clonedCall.enqueue(object : retrofit2.Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    if (!emitter.isDisposed) {
                        response.body()?.let {
                            if (it is ResponseBody) {
                                emitter.onNext(it.string())
                            }
                        }
                        response.errorBody()?.let {
                            emitter.onNext(it.string())
                        }
                        emitter.onComplete()
                    }
                }

                override fun onFailure(call: Call<T>, t: Throwable) {
                    if (!emitter.isDisposed) {
                        emitter.onError(t)
                    }
                }
            })

            // Dispose the call when the Observable is disposed
            emitter.setCancellable { clonedCall.cancel() }
        }
    }

    private fun isJsonValid(jsonInString: String): Boolean {
        return try {
            Gson().fromJson(jsonInString, Any::class.java)
            true
        } catch (ex: JsonSyntaxException) {
            false
        }
    }

    fun executeRequest(
        requestMethod: String?,
        requestUrl: String?,
        requestBodyString: String?,
        requestHeader: Map<String, String>?
    ): Observable<String?> {
        if (requestMethod?.isBlank() == true || requestUrl?.isBlank() == true) {
            return Observable.error(IllegalArgumentException("requestMethod and requestUrl cannot be empty"))
        }

        //初始化retrofit
        val gson = GsonBuilder().setLenient().create()
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(PlayerConstants.getApiBaseUrl())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(HttpManager.getInstance().adVendorOkhttpClient)
            .build()

        val apiService: HttpAdVendorApi = retrofit.create(HttpAdVendorApi::class.java)

        val call = if (requestMethod.equals("POST", ignoreCase = true)) {
            var requestBody: RequestBody? = null
            requestBodyString?.let { bodyStr ->
                val mediaType =
                    if (isJsonValid(bodyStr)) "application/json; charset=utf-8" else "text/plain"
                requestBody = bodyStr.toRequestBody(mediaType.toMediaTypeOrNull())
            }
            apiService.postRequest(requestUrl!!, requestBody, requestHeader ?: emptyMap())
        } else {
            apiService.getRequest(requestUrl!!, requestHeader ?: emptyMap())
        }

        return makeApiCall(call)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }
}
