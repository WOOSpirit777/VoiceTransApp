package com.quniu.baselib.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import com.quniu.baselib.R
import com.quniu.baselib.databinding.LayoutEmptyBinding
import com.quniu.baselib.event.CustomEvent
import com.quniu.baselib.event.CustomEventKey
import com.quniu.baselib.util.changeToDp
import commonutil.CommonUtils
import org.greenrobot.eventbus.EventBus

/**
 * @ClassName EmptyView
 * @Description 默认无数据页面
 * @Author Vvv
 */
enum class EmptyType {
    /**
     * 默认无数据
     */
    DEFAULT,
    /**
     * 网络错误
     */
    NETWORK_ERROR
}
class EmptyView : LinearLayout {
    private lateinit var mBinding: LayoutEmptyBinding

    constructor(context: Context?,type: EmptyType? = EmptyType.DEFAULT) : super(context) {
        initView(context,type)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initView(context)
    }

    fun initView(context: Context?, type: EmptyType? = EmptyType.DEFAULT) {
        mBinding = DataBindingUtil.inflate(
            context!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater,
            R.layout.layout_empty,
            this@EmptyView, false
        )
        mBinding.root.tag = "layout/view_empty_0"
        addView(mBinding.root)
        initListener()
        setEmptyType(type)
    }


    fun initListener() {
        mBinding.tvMall.setOnClickListener { mOnEmptyClickListener?.onReload() }
        mBinding.tvSettingNet.setOnClickListener {
            if (CommonUtils.getHostAppPackageNameJvm(context) == "com.aibabel.scenicareaguide") {
                EventBus.getDefault().post(CustomEvent(CustomEventKey.EVENT_SKIP_VOICE))
            }
            CommonUtils.gotoSettings(CommonUtils.ROUTE_WIFI_SETTINGS, context, true)
        }
    }

    fun setTopMargin(topMargin: Int) {
        val layoutParams = mBinding.ivNoDate.layoutParams
        if (layoutParams is ConstraintLayout.LayoutParams) {
            layoutParams.topMargin = topMargin.toFloat().changeToDp.toInt()
            mBinding.ivNoDate.layoutParams = layoutParams
        }
    }

    /*
      * 设置空页面图标
      * */
    fun setEmptyIcon(resId: Int?) {
        resId?.let { mBinding.ivNoDate.setImageResource(it) }
    }

    /*
     * 设置空页面文案
     * */
    fun setEmptyText(emptyStr: String?) {
        mBinding.tvNoData.text = emptyStr
    }

    fun setEmptyType(type: EmptyType?= EmptyType.DEFAULT) {
        when (type) {
            EmptyType.DEFAULT -> {
                setEmptyIcon(R.drawable.bg_no_data)
                setEmptyText(context.getString(R.string.no_data))
                mBinding.llRetry.visibility = GONE
            }
            EmptyType.NETWORK_ERROR -> {
                setEmptyIcon(R.drawable.bg_no_network)
                setEmptyText(context.getString(R.string.nothing_here))
                mBinding.llRetry.visibility = VISIBLE
            }

            null ->{
                setEmptyIcon(R.drawable.bg_no_data)
                setEmptyText(context.getString(R.string.no_data))
                mBinding.llRetry.visibility = GONE
        }
        }
    }

    /*
     * 设置页面背景
     * */
    fun setEmptyBg(resId: Int?) {
        resId?.let { mBinding.bgNoData.setBackgroundColor(it) }
    }


    var mOnEmptyClickListener: OnEmptyClickListener? = null

    interface OnEmptyClickListener {
        fun onReload()
    }

}