//package com.quniu.netlib

//import github.leavesczy.monitor.MonitorInterceptor
import android.os.Environment
import android.util.Log
import com.google.gson.GsonBuilder
import com.quniu.baselib.BuildConfig
import netlib.event.ReLoginEvent
import commonutil.TokenHelper
import netlib.LocalFileLogger
import netlib.NetConst
import netlib.TimeoutInterceptor
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * @ClassName NetClient
 * @Description 网络请求封装
 * @Author Vvv
 */
object NetClient {
    private fun getOkHttpClient(): OkHttpClient {

        val logDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "aibabel")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        val headerInterceptor = Interceptor { chain ->
            val build = chain.request().newBuilder()
                .addHeader("Authorization","${NetConst.TOKEN_TYPE} ${NetConst.TOKEN}")
                .build()
            val response = chain.proceed(build)
            getResponseContent(response)
            response
        }
        val errorFile = File(logDir, "error_log.txt")
        val timeOutInterceptor = TimeoutInterceptor(LocalFileLogger(errorFile))
        return OkHttpClient.Builder()
            .connectTimeout(60L, TimeUnit.SECONDS)
            .writeTimeout(60L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)

            .addInterceptor(headerInterceptor)
//            .addInterceptor(timeOutInterceptor)
            .connectionPool(ConnectionPool(5, 15, TimeUnit.SECONDS))
            .retryOnConnectionFailure(true)
//            .dispatcher(dispatcher)
            .build()
    }

    private fun getResponseContent(response: Response) {
        if (!response.isSuccessful) {
            return
        }
        try {
            val body = response.body
            val contentLength: Long? = body?.contentLength()
            val source = body?.source()
            source?.request(Long.MAX_VALUE)
            val buffer = source?.buffer
            val charset = Charsets.UTF_8
            if (contentLength != 0L) {
                val content = buffer?.clone()?.readString(charset)
                content?.let {
                    val responseJson = JSONObject(content)
                    if (responseJson.optInt("code", 200) == 401) {
                        EventBus.getDefault().post(ReLoginEvent())
                    }
                    if (responseJson.optInt("code", 200) == -1 && responseJson.optString("msg", "") == "key中sn与参数中sn不一致") {
                        TokenHelper.clearToken()
                    }
                }
            }
        } catch (e: Exception) {
            Log.i("getResponseContent", "getResponseContent: ${e.cause}")
            Log.i("getResponseContent", "getResponseContent: ${e.message}")
        }
    }

    fun <T> create(service: Class<T>): T {
       return Retrofit.Builder()
            .client(getOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(
                GsonBuilder()
                    .setLenient()
                    .create()
            ))
            .baseUrl(NetConst.BASE_URL2)
//            .baseUrl("http://192.168.1.226:18030")
            .build().create(service) ?: throw RuntimeException("Api service is null!")
    }


}