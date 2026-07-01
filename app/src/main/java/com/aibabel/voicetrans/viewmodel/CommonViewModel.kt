package com.aibabel.voicetrans.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.aibabel.voicetrans.BuildConfig
import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.BaseApplication
import com.quniu.baselib.util.MMKVConst
import com.quniu.baselib.util.MMKVUtils
import com.quniu.baselib.util.ToastUtil
import com.quniu.baselib.viewmodel.BaseViewModel
import commonutil.CommonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import netlib.NetConst
import netlib.data.VersionData
import netlib.exception.ResponseThrowable
import netlib.util.ParamUtil
import netlib.util.PasswordUtil
import java.util.Date
import java.util.UUID

open class CommonViewModel : BaseViewModel() {
    private val _initResult = MutableLiveData<Boolean>()

    // token 初始化互斥量，支持挂起操作
    private val tokenInitMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun ensureToken(): Boolean {
        // 快速判断
        if (!NetConst.TOKEN.isBlank() && !NetConst.TOKEN_TYPE.isBlank()) return true
// 若已经保存到 MMKV，则同步到 NetConst 后返回
        val savedToken = MMKVUtils.getMultiStr(MMKVConst.TOKEN, MMKVConst.MMKV_ID)
        val savedTokenType = MMKVUtils.getMultiStr(MMKVConst.TOKEN_TYPE, MMKVConst.MMKV_ID)
        if (!savedToken.isNullOrBlank() && !savedTokenType.isNullOrBlank()) {
            NetConst.TOKEN = savedToken
            NetConst.TOKEN_TYPE = savedTokenType
            return true
        }
        // 使用协程互斥量，允许在锁内执行挂起操作（如网络请求）
        return tokenInitMutex.withLock {
            // double-check
            if (!NetConst.TOKEN.isBlank() && !NetConst.TOKEN_TYPE.isBlank()) return@withLock true
            try {
                val av = CommonUtils.getVersionName(BaseApplication.mContext) ?: ""

                // 先注册 Observer，再发起 initToken，避免 initToken 返回速度快导致 missed update
                val success = suspendCancellableCoroutine { cont ->
                    val observer = object : Observer<Boolean> {
                        override fun onChanged(value: Boolean) {
                            try {
                                cont.resumeWith(Result.success(value))
                            } catch (_: Throwable) {
                            }
                            _initResult.removeObserver(this)
                        }
                    }

                    _initResult.observeForever(observer)

                    cont.invokeOnCancellation {
                        _initResult.removeObserver(observer)
                    }

                    // 在 observer 注册成功后再发起网络请求
                    try {
                        initToken(av = av)
                    } catch (e: Exception) {
                        LogUtils.e("initToken threw: ${e.message}")
                        if (!cont.isCompleted) {
                            try {
                                cont.resumeWith(Result.success(false))
                            } catch (_: Throwable) {
                            }
                        }
                        _initResult.removeObserver(observer)
                    }
                }

                success
            } catch (e: Exception) {
                LogUtils.e("ensureToken failed: ${e.message}")
                false
            }
        }
    }


    /**
     * launchGo 的包装：在执行网络请求前确保 token 就绪。
     *
     * 使用方法与原 launchGo 参数一致。
     */
    fun launchGoWithToken(
        block: suspend CoroutineScope.() -> Unit,
        isShowDialog: Boolean = false,
        error: suspend CoroutineScope.(ResponseThrowable) -> Unit = {
//            defUI.toastEvent.postValue(it.errMsg)
        },
        complete: suspend CoroutineScope.() -> Unit = {}
    ) {
        launchUI {
            // 如果需要显示 loading，先显示（后续调用 launchGo 会负责关闭）
            if (isShowDialog) defUI.showDialog.postValue("loading...")

            // 先保证 token，就绪后再发起网络请求
            val ok = try {
                ensureToken()
            } catch (e: Exception) {
                LogUtils.e("ensureToken exception: ${e.message}")
                false
            }

            if (!ok) {
                // 将失败通过 error 回调传递，使用 ResponseThrowable 表示错误
                val respErr = ResponseThrowable(0, "获取 token 失败")
                try {
                    error(respErr)
                } catch (_: Throwable) {
                    // 忽略回调内部异常，确保继续执行完成逻辑
                    LogUtils.e("error callback threw an exception")
                }
                // 关闭 dialog（如果之前显示了）并调用 complete
                if (isShowDialog) defUI.dismissDialog.postValue("loading...")
                try {
                    complete()
                } catch (_: Throwable) {
                }
                return@launchUI
            }

            // token 已就绪，复用 BaseViewModel.launchGo 做统一异常处理与 dialog 控制
            // 注意：我们已经在需要时显示了 dialog，所以传入 isShowDialog = false，避免重复 show
            launchGo(block, false, error, complete)
        }
    }

    fun initToken(
        sn: String? = null,
        av: String = BuildConfig.VERSION_NAME,
        sv: String= CommonUtils.getDeviceInfo(),
        task_id: String= UUID.randomUUID().toString(),
        time: Long= System.currentTimeMillis()/1000
    ) {
        // 延迟获取 sn，避免函数声明处触发有权限要求的调用
        val finalSn = sn ?: try { CommonUtils.getSN() } catch (_: Exception) { "" }

        val hashMap = HashMap<String, Any?>()
        hashMap.put("sn", finalSn)
        hashMap.put("av", av)
        hashMap.put("sv", sv)
        hashMap.put("task_id", task_id)
        hashMap.put("time", time)
        launchGo({
            val result = NetClient.create(ApiService::class.java)
                .registerToken(PasswordUtil.generatePassword(finalSn, Date(time)), ParamUtil.getRequestBody(hashMap))
            if (result.isSuccess()) {

                try {
                    MMKVUtils.putMultiData(MMKVConst.TOKEN, result.data.token, MMKVConst.MMKV_ID)
                    MMKVUtils.putMultiData(MMKVConst.TOKEN_TYPE, result.data.token_type, MMKVConst.MMKV_ID)
                    NetConst.TOKEN = result.data.token
                    NetConst.TOKEN_TYPE = result.data.token_type
                    delay(2000)
                } catch (e: Exception) {
                    LogUtils.e("解析 TokenData 失败: ${e.message}")
                }
                _initResult.value = true
            } else {
                _initResult.value = false
                LogUtils.e("解析 TokenData 失败")
                ToastUtil.showShort(result.msg)
            }
        }, true,{
            it.errMsg.let { msg ->
                ToastUtil.showShort(msg)
            }
            _initResult.value = false
        })
    }

    private val _checkResult = MutableLiveData<VersionData>()
    val checkResult: LiveData<VersionData> = _checkResult

    fun checkUpdate(){
        launchGo({
            val hashMap = HashMap<String, Any?>().apply {
                put("tag", "translate")
                put("av", BuildConfig.VERSION_NAME)
                put("sv", CommonUtils.getDeviceInfo())
                put("task_id", UUID.randomUUID().toString())
                put("time", System.currentTimeMillis() / 1000)
            }
            val result = NetClient.create(ApiService::class.java)
                .checkVersion(ParamUtil.getRequestBody(hashMap))
            if (result.isSuccess()) {
                _checkResult.value = result.data
            } else {
                LogUtils.e(result.msg)
            }
        })
    }

}