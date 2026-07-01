package com.quniu.baselib.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.util.PageUtil
import com.quniu.baselib.util.SmartRefreshTool
import com.quniu.baselib.util.ToastUtil
import com.quniu.baselib.viewmodel.BaseViewModel
import com.quniu.baselib.viewmodel.ViewModelFactory
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

abstract class BaseVBFragmentOpt<VM : BaseViewModel, VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB
) : Fragment() {

    @Suppress("UNCHECKED_CAST")
    protected val viewModel: VM by lazy {
        val vmClass = (javaClass.genericSuperclass as ParameterizedType)
            .actualTypeArguments[0] as Class<VM>
        val owner = if (isShareVM()) requireActivity() else this
        ViewModelProvider(owner, ViewModelFactory())[vmClass]
    }


    private var _binding: VB? = null
    protected val binding: VB get() = _binding!!

    private var loadingDialog: LoadingDialog? = null

    // --- Refresh / paging fields (copied/adapted from BaseDialogFragment) ---
    private var xRefreshView: SmartRefreshLayout? = null
    /** 当前要数据偏移 */
    private var pageIndex: Int = PageUtil.MIN_PAGE_INDEX
    private var page = Page()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (useEventBus()) EventBus.getDefault().register(this)
        registerUIEvent() // 提前注册 LiveData 观察者
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
//        if (useEventBus()) EventBus.getDefault().register(this)
        _binding = inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(viewModel)
//        registerUIEvent()
        initView()
        initData()
        initListener()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (useEventBus()) EventBus.getDefault().unregister(this)
        dismissLoading()
        loadingDialog = null
        _binding = null
    }

    abstract fun initView()
    abstract fun initData()
    abstract fun initListener()

    open fun useEventBus(): Boolean = false
    open fun isShareVM(): Boolean = false

    private fun registerUIEvent() {
        viewModel.defUI.showDialog.observe(this) {
            LogUtils.d("showDialog111")
            showLoading()
        }
        viewModel.defUI.dismissDialog.observe(this) { dismissLoading() }
        viewModel.defUI.toastEvent.observe(this) {
            ToastUtil.showShort(it)
        }
    }


    /**
     * 实例化 RefreshView 和 RecycleView
     */
    protected open fun initXRefreshView(refreshLayout: SmartRefreshLayout, isRefresh: Boolean = true) {
        this.xRefreshView = refreshLayout
        SmartRefreshTool.initTable(requireActivity(), refreshLayout, isRefresh)
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
    fun showLoading() {
        LogUtils.d("showDialog")
        try {
            if (loadingDialog == null) {
                loadingDialog = context?.let { LoadingDialog(it) }
            }
            if (!activity?.isFinishing!! && !loadingDialog?.isShowing!!) {
                loadingDialog?.show()
            }
        } catch (e: Exception) {

        }
    }

    fun dismissLoading() {
        try {
            loadingDialog?.run {
                if (isShowing && !activity?.isFinishing!!) {
                    dismiss()
                    loadingDialog = null
                }
            }
        } catch (e: Exception) {

        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    open fun onNetError(event: NetErrorEvent) {}
}