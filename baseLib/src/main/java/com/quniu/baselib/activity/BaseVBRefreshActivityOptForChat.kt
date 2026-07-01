package com.quniu.baselib.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.util.PageUtil
import com.quniu.baselib.util.SmartRefreshTool
import com.quniu.baselib.util.ToastUtil
import com.quniu.baselib.viewmodel.BaseViewModel
import com.quniu.baselib.viewmodel.ViewModelFactory
import com.quniu.baselib.widget.EmptyView
import com.quniu.baselib.widget.LoadingDialog
import netlib.event.NetErrorEvent
import netlib.request.Page
import com.scwang.smart.refresh.layout.SmartRefreshLayout
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.reflect.ParameterizedType

abstract class BaseVBRefreshActivityOptForChat<VM : BaseViewModel, VB : ViewBinding>(
    private val inflate: (LayoutInflater) -> VB
) : AppCompatActivity() {

    /* ====================== 延迟初始化 ====================== */
    @Suppress("UNCHECKED_CAST")
    val viewModel: VM by lazy {
        val vmClass = (javaClass.genericSuperclass as ParameterizedType)
            .actualTypeArguments[0] as Class<VM>
        ViewModelProvider(this, ViewModelFactory())[vmClass]
    }

    lateinit var binding: VB
        private set

    private var loadingDialog: LoadingDialog? = null

    // --- Refresh / paging fields ---
    protected var xRefreshView: SmartRefreshLayout? = null
    /** 当前要数据偏移 */
    private var pageIndex: Int = PageUtil.MIN_PAGE_INDEX
    private var page = Page()

    /* ====================== 生命周期 ====================== */

/*    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (GlobalKeyDispatcher.onKeyLongPress(keyCode, this)) {
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    /* ====================== 私有/可重写 ====================== */

    protected open fun registerUIEvent() {
        viewModel.defUI.showDialog.observe(this) {
            LogUtils.d("showDialog")
            showLoading()
        }
        viewModel.defUI.dismissDialog.observe(this) { dismissLoading() }
        viewModel.defUI.toastEvent.observe(this) {
            ToastUtil.showShort(it)
        }
    }

    protected open fun findEmptyView(): EmptyView? {
        xRefreshView?.let { rl ->
            for (i in 0 until rl.childCount) {
                val v = rl.getChildAt(i)
                if (v is EmptyView) return v
            }
        }
        return null
    }

    /**
     * 实例化 RefreshView 和 RecycleView
     */
    protected open fun initXRefreshView(refreshLayout: SmartRefreshLayout, isRefresh: Boolean = true) {
        // assign to field so other methods can use the refresh layout
        this.xRefreshView = refreshLayout
        SmartRefreshTool.initTable(this, refreshLayout, isRefresh)
        refreshLayout.setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onRefresh(refreshLayout: RefreshLayout) {
                pageIndex = PageUtil.MIN_PAGE_INDEX
                page.page_index = pageIndex
                loadForList(false, page)
            }

            override fun onLoadMore(refreshLayout: RefreshLayout) {
                pageIndex += 1
                page.page_index = pageIndex
                loadForList(true, page)
            }
        })
    }

    protected abstract fun loadForList(more: Boolean, page: Page)

    fun onLoadForListComplete(more: Boolean, isSuccess: Boolean, data: List<Any>) {
        if (!more) {
            onRefreshComplete(data)
        } else {
            onLoadMoreComplete(data)
        }
        if (!isSuccess) {
            onLoadForListFailed(more)
        }
    }

    open fun onRefreshComplete(data: List<Any>) {
        xRefreshView!!.finishRefresh()
    }

    open fun onLoadMoreComplete(data: List<Any>) {
        if (data.isNotEmpty()) {
            xRefreshView!!.finishLoadMore()
        } else {
            xRefreshView!!.finishLoadMoreWithNoMoreData()
        }
    }

    open fun onLoadForListFailed(more: Boolean) {
        if (!more) {
            xRefreshView!!.finishRefresh()
        } else {
            pageIndex = if (pageIndex == PageUtil.MIN_PAGE_INDEX) pageIndex else pageIndex - 1
            xRefreshView!!.finishLoadMore()
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
        if (loadingDialog == null) {
            loadingDialog = LoadingDialog(this)
        }
        if (!isFinishing && !loadingDialog?.isShowing!!) {
            loadingDialog?.show()
        }
    }

    fun dismissLoading() {
        loadingDialog?.run { if (isShowing && !isFinishing) dismiss() }
        loadingDialog = null
    }

    /* ====================== 网络错误 ====================== */

    @Subscribe(threadMode = ThreadMode.MAIN)
    open fun onNetError(event: NetErrorEvent) {}
}