package netlib.exception

import android.net.ParseException
import android.util.Log
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import netlib.event.NetErrorEvent
import netlib.event.PauseChangeTaskEvent
import org.greenrobot.eventbus.EventBus
import org.json.JSONException
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

object ExceptionHandler {

    @Volatile
    var errMsg: String = ""

    fun handleException(e: Throwable): ResponseThrowable {
        val ex: ResponseThrowable
        if (e is ResponseThrowable) {
            ex = e
        }
        /*java.security.cert.CertificateException: Chain validation failed
 msg = Chain validation failed*/
        else if (e is HttpException) {
            // HTTP错误
            ex = ResponseThrowable(ERROR.HTTP_ERROR, e)
        } else if (e is JsonParseException || e is JSONException || e is ParseException || e is MalformedJsonException) {
            // 解析错误
            ex = ResponseThrowable(ERROR.PARSE_ERROR, e)
        } else if (e is ConnectException || e is UnknownHostException) {
            // 连接错误
            ex = ResponseThrowable(ERROR.NETWORD_ERROR, e)
        } else if (e is SocketTimeoutException) {
            // 连接超时
            ex = ResponseThrowable(ERROR.TIMEOUT_ERROR, e)
        } else if (e is SSLException || e is CertificateException) {
            // SSL证书错误
            ex = ResponseThrowable(ERROR.SSL_ERROR, e)
        } else
        {
            ex = if (!e.message.isNullOrEmpty()) ResponseThrowable(1000, e.message!!, e)
            else ResponseThrowable(ERROR.UNKNOWN, e)
        }
        if (!e.message.equals("Job was cancelled")) {
            EventBus.getDefault().post(NetErrorEvent())
            if (e is JsonParseException
                || e is JSONException
                || e is ParseException || e is MalformedJsonException
            ) {
                Log.d("ExceptionHandler==", "解析错误-不切换域名")
            }else{
//                NetClient.cancelAllRequests()
                EventBus.getDefault().post(PauseChangeTaskEvent())

            }
        }
        Log.d("ExceptionHandler==", "handleException: ${e.cause}---${e.message}")
        return ex
    }
}