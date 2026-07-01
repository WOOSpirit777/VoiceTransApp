package com.quniu.baselib.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.graphics.Rect
import android.view.KeyEvent
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.util.GlobalKeyDispatcher
import com.quniu.baselib.util.ToastUtil
import com.quniu.baselib.viewmodel.BaseViewModel
import com.quniu.baselib.viewmodel.ViewModelFactory
import com.quniu.baselib.widget.LoadingDialog
import netlib.event.NetErrorEvent
import commonutil.CommonUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.reflect.ParameterizedType

/**
 * 尽量不实用
 * */
abstract class BaseVBActivityOpt<VM : BaseViewModel, VB : ViewBinding>(
    private val inflate: (LayoutInflater) -> VB
) : AppCompatActivity() {

    /* ====================== 延迟初始化 ====================== */
    @Suppress("UNCHECKED_CAST")
    protected val viewModel: VM by lazy {
        val vmClass = (javaClass.genericSuperclass as ParameterizedType)
            .actualTypeArguments[0] as Class<VM>
        ViewModelProvider(this, ViewModelFactory())[vmClass]
    }

    lateinit var binding: VB
        private set

    private var loadingDialog: LoadingDialog? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 处理全局按键监听 - 下发 startTracking 以支持长按判定
        if (GlobalKeyDispatcher.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // 在抬起时处理短按逻辑，并排除长按过程中产生的抬起事件
        if (GlobalKeyDispatcher.onKeyUp(keyCode, event, this)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (GlobalKeyDispatcher.onKeyLongPress(keyCode, this)) {
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    /* ====================== 生命周期 ====================== */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = CommonUtils.getHostAppPackageNameJvm(this)
        LogUtils.d("pkg: $pkg")
        // 1. 懒 EventBus
        if (useEventBus()) EventBus.getDefault().register(this)

        // 2. 唯一一次 inflate
        binding = inflate(layoutInflater)
        setContentView(binding.root)

        // 3. 生命周期
        lifecycle.addObserver(viewModel)

        // 4. 通用 UI
        registerUIEvent()

        // 5. 子类入口
        initView()
        initData()
        initListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (useEventBus()) EventBus.getDefault().unregister(this)
        dismissLoading()
        loadingDialog = null   // 防泄漏
    }

    /* ====================== 抽象入口 ====================== */

    abstract fun initView()
    abstract fun initData()
    abstract fun initListener()

    /* ====================== 开关 ====================== */

    open fun useEventBus(): Boolean = false

    /* ====================== 私有 ====================== */

    private fun registerUIEvent() {
        viewModel.defUI.showDialog.observe(this) { showLoading() }
        viewModel.defUI.dismissDialog.observe(this) { dismissLoading() }
        viewModel.defUI.toastEvent.observe(this) {
            ToastUtil.showShort(it)
        }
    }

    /* ====================== 公共工具 ====================== */
    fun hideKeyboard() {
        currentFocus?.let {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
    fun showLoading() {
        if (loadingDialog == null) loadingDialog = LoadingDialog(this)
        loadingDialog?.takeIf { !it.isShowing }?.show()
    }

    fun dismissLoading() {
        loadingDialog?.takeIf { it.isShowing && !isFinishing && !isDestroyed }?.dismiss()
    }

    /* ====================== 全局触摸处理（抽取） ====================== */
    /**
     * Subclasses can override this to disable hiding keyboard on outside touch
     */
    protected open fun shouldHideKeyboardOnOutsideTouch(): Boolean = true

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (shouldHideKeyboardOnOutsideTouch() && ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v != null) {
                // Support EditText and its AppCompat variants
                if (v is EditText || v.javaClass.simpleName == "EditText") {
                    val outRect = Rect()
                    (v as EditText).getGlobalVisibleRect(outRect)
                    val x = ev.rawX.toInt()
                    val y = ev.rawY.toInt()
                    if (!outRect.contains(x, y)) {
                        v.clearFocus()
                        hideKeyboard()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /* ====================== 网络错误 ====================== */

    @Subscribe(threadMode = ThreadMode.MAIN)
    open fun onNetError(event: NetErrorEvent) {}
}