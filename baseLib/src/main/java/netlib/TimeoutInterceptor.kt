package netlib

import com.apkfuns.logutils.LogUtils
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.HttpRetryException
import java.net.MalformedURLException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class TimeoutInterceptor(private val logger: LocalFileLogger) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val requestUrl = request.url.toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        try {
            return chain.proceed(request)
        } catch (e: IOException) {
            when (e) {
                is SocketTimeoutException,
                is ConnectException -> {
                    LogUtils.e("$timestamp - Timeout occurred for request: $requestUrl - Error: ${e.message}")
                    logger.log("$timestamp - Timeout occurred for request: $requestUrl - Error: ${e.message}")
//                    EventBus.getDefault().post(PauseChangeTaskEvent())
                }

                is UnknownHostException -> {
                    LogUtils.e("$timestamp -Host not found for request: $requestUrl - Error: ${e.message}")
                    logger.log("$timestamp - Host not found for request: $requestUrl")
//                    EventBus.getDefault().post(PauseChangeTaskEvent())
                }

                is SSLHandshakeException -> {
                    LogUtils.e("$timestamp -SSL handshake failed for request: $requestUrl - Error: ${e.message}")
                    logger.log("$timestamp - SSL handshake failed for request: $requestUrl")
//                    EventBus.getDefault().post(PauseChangeTaskEvent())
                }

                is SSLPeerUnverifiedException -> {
                    logger.log("$timestamp - Server certificate invalid for request: $requestUrl")
//                    EventBus.getDefault().post(PauseChangeTaskEvent())
                }

                is ProtocolException -> {
                    logger.log("$timestamp - Protocol error for request: $requestUrl")
//                    EventBus.getDefault().post(PauseChangeTaskEvent())
                }

                is HttpRetryException -> {
                    logger.log("$timestamp - HTTP retry failed for request: $requestUrl")
//                    EventBus.getDefault().post(PauseChangeTaskEvent())
                }

                is MalformedURLException -> {
                    logger.log("$timestamp - Invalid URL format for request: $requestUrl")
//                    EventBus.getDefault().post(PauseChangeTaskEvent())
                }

                else -> {
                    logger.log("$requestUrl - Error: "+String.format(
                        "cause = %s \n msg = %s",
                        e.cause,
                        e.message
                    ))
                }
            }
            throw e
        }
    }
}
