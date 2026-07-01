package com.quniu.baselib.fragment

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.quniu.baselib.R
import com.quniu.baselib.util.GlobalKeyDispatcher
import com.quniu.baselib.util.ToastUtil
import com.quniu.baselib.viewmodel.BaseViewModel
import com.quniu.baselib.viewmodel.ViewModelFactory
import netlib.event.NetErrorEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.reflect.ParameterizedType


/**
 * @ClassName BaseFragment
 * @Description Fragment基类
 * @Author Vvv
 */
abstract class BaseCenterDialogFragment<VM : BaseViewModel, DB : ViewDataBinding> :
    DialogFragment() {

    protected lateinit var viewModel: VM
    protected lateinit var dataBinding: DB

    private var mContext: Context? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    /**
     * 当前界面是否可见
     */
    private var isVisibleToUser = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.BottomDialogStyle)
    }


    override fun onStart() {
        super.onStart()
        initWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (useEventBus()) EventBus.getDefault().unregister(this)
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (useEventBus()) EventBus.getDefault().register(this)
        dataBinding = DataBindingUtil.inflate(inflater, getLayoutId(), container, false)
        dataBinding.lifecycleOwner = this
        return dataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        createViewModel()

        lifecycle.addObserver(viewModel)
        //注册 UI事件
        registerDefUIChange()
        initView(dataBinding.root)
        initData(dataBinding.root)
        initListener()
        // 为 dialog 设置按键监听器
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (event?.action == KeyEvent.ACTION_DOWN) {
               return@setOnKeyListener GlobalKeyDispatcher.handleKeyEvent(keyCode, requireActivity())
            }
            false
        }

    }

    open fun useEventBus(): Boolean = false
    private fun initWindow() {
        val window = dialog?.window
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val params = window?.attributes
        params?.gravity = Gravity.CENTER
        window?.attributes = params

    }

    /**
     * 获取布局ID
     *
     * @return
     */
    protected abstract fun getLayoutId(): Int

    /**
     * 初始化view操作
     */
    protected abstract fun initView(root: View)

    /**
     * 数据初始化操作
     */
    protected abstract fun initData(root: View)

    /**
     * 设置监听
     */
    protected abstract fun initListener()

    /**
     * 注册 UI 事件
     */
    private fun registerDefUIChange() {
        viewModel.defUI.showDialog.observe(this) {
        }
        viewModel.defUI.dismissDialog.observe(this) {
        }
        viewModel.defUI.toastEvent.observe(this) {
            ToastUtil.showShort(it)
        }
    }


    /**
     * 是否和 Activity 共享 ViewModel,默认不共享
     * Fragment 要和宿主 Activity 的泛型是同一个 ViewModel
     */
    open fun isShareVM(): Boolean = false

    /**
     * 创建 ViewModel
     */
    @Suppress("UNCHECKED_CAST")
    private fun createViewModel() {
        val type = javaClass.genericSuperclass
        if (type is ParameterizedType) {
            val tp = type.actualTypeArguments[0]
            val tClass = tp as? Class<VM> ?: BaseViewModel::class.java
            val viewModelStore =
                if (isShareVM()) requireActivity().viewModelStore else this.viewModelStore
            viewModel = ViewModelProvider(viewModelStore, ViewModelFactory()).get(tClass) as VM
        }
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    open fun onNetError(event: NetErrorEvent) {}
}