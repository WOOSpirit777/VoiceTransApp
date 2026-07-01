package com.quniu.baselib.viewmodel

import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.viewModelScope
import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.BaseApplication
import com.quniu.baselib.event.SingleLiveEvent
import netlib.response.IBaseResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import netlib.exception.ExceptionHandler
import netlib.exception.ResponseThrowable

/**
 * @ClassName BaseViewModel
 * @Description ViewModel基类
 * @Author Vvv
 */
open class BaseViewModel : AndroidViewModel(BaseApplication.mContext), LifecycleObserver {

    val defUI: UIChange by lazy { UIChange() }
    inner class UIChange {
        val showDialog by lazy { SingleLiveEvent<String>() }
        val dismissDialog by lazy { SingleLiveEvent<String>() }
        val toastEvent by lazy { SingleLiveEvent<String>() }
        val errorEvent by lazy { SingleLiveEvent<String>() }
    }

    fun <T> launchDelayFlow(
        delayMillis: Long = 1000L,
        block: suspend () -> T
    ): Flow<T> {
        return flow {
            delay(delayMillis)
            // 在 IO 线程执行请求并发出结果
            emit(withContext(Dispatchers.IO) { block() })
        }
    }

    /*
    launchUI {
        viewModel.launchDelayFlow(2000L) {
            // 这里执行网络请求，返回结果对象
            NetClient.create(ApiService::class.java)
                .serverTime(ParamUtil.getRequestBody(mapOf("sn" to "TSA001212800001")))
        }.collect { result ->
            // 处理 result
        }
    }
    */
    /**
     * 所有网络请求都在 viewModelScope 域中启动，
     * 当页面销毁时会自动调用ViewModel的  #onCleared 方法取消所有协程
     */
    fun launchUI(block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch { block() }

    /**
     * 用流的方式进行网络请求
     */
    fun <T> launchFlow(block: suspend () -> T): Flow<T> {
        return flow {
            emit(block())
        }
    }

    /**
     *  不过滤请求结果
     * @param block 请求体
     * @param error 失败回调
     * @param complete  完成回调（无论成功失败都会调用）
     * @param isShowDialog 是否显示加载框
     */
    fun launchGo(
        block: suspend CoroutineScope.() -> Unit,
        isShowDialog: Boolean = true,
        error: suspend CoroutineScope.(ResponseThrowable) -> Unit = {
            defUI.toastEvent.postValue(it.errMsg)
            LogUtils.d("launchGo: ${it.errMsg}")
        },
        complete: suspend CoroutineScope.() -> Unit = {}
    ) {
        if (isShowDialog) defUI.showDialog.postValue("loading...")
        launchUI {
            handleException(
                withContext(Dispatchers.IO) { block },
                { error(it) },
                {
                    defUI.dismissDialog.postValue("disloading...")
                    complete()
                }
            )
        }
    }


    /**
     *  不过滤请求结果(子线程调用)
     * @param block 请求体
     * @param delayMillis 可配置延迟参数，默认为0（无延迟）
     * @param error 失败回调
     * @param complete  完成回调（无论成功失败都会调用）
     * @param isShowDialog 是否显示加载框
     */
    fun launchOutUi(
        block: suspend CoroutineScope.() -> Unit,
        delayMillis: Long = 0,
        error: suspend CoroutineScope.(ResponseThrowable) -> Unit = {
            defUI.toastEvent.postValue(it.errMsg)
            LogUtils.d("launchGo: ${it.errMsg}")
        },
        complete: suspend CoroutineScope.() -> Unit = {}
    ) {
        launchUI {
            handleException(
                withContext(Dispatchers.IO) {
                    if (delayMillis > 0) {
                        delay(delayMillis)
                    }
                    block
                },
                { error(it) },
                {
                    complete()
                }
            )
        }
    }


    /**
     * 请求结果过滤
     */
    private suspend fun <T> executeResponse(
        response: IBaseResponse<T>,
        success: suspend CoroutineScope.(T) -> Unit
    ) {
        coroutineScope {
            if (response.isSuccess()) success(response.data())
            else throw ResponseThrowable(response.status(), response.msg())
        }
    }

    /**
     * 异常统一处理
     */
    private suspend fun <T> handleException(
        block: suspend CoroutineScope.() -> IBaseResponse<T>,
        success: suspend CoroutineScope.(IBaseResponse<T>) -> Unit,
        error: suspend CoroutineScope.(ResponseThrowable) -> Unit,
        complete: suspend CoroutineScope.() -> Unit
    ) {
        coroutineScope {
            try {
                success(block())
            } catch (e: ResponseThrowable) {
                error(e)
            } finally {
                complete()
            }
        }
    }


    /**
     * 异常统一处理
     */
    private suspend fun handleException(
        block: suspend CoroutineScope.() -> Unit,
        error: suspend CoroutineScope.(ResponseThrowable) -> Unit,
        complete: suspend CoroutineScope.() -> Unit
    ) {
        coroutineScope {
            try {
                block()
            } catch (e: Throwable) {
                Log.d(
                    TAG,
                    "handleException: " + String.format(
                        "cause = %s \n msg = %s",
                        e.cause,
                        e.message
                    )
                )
//                ToastUtil.showLong(String.format(
//                    "cause = %s \n msg = %s",
//                    e.cause,
//                    e.message
//                ))

                error(ExceptionHandler.handleException(e))
            } finally {
                complete()
            }
        }
    }

    private val TAG = "BaseViewModelException"


}