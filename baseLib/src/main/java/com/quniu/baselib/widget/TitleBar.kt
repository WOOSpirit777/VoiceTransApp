package com.quniu.baselib.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import com.quniu.baselib.R
import com.quniu.baselib.databinding.LayoutTitleBarBinding

/**
 * @ClassName TitleBar
 * @Description 顶部导航
 * @Author Vvv
 */
class TitleBar : ConstraintLayout, View.OnClickListener {
    private lateinit var mBinding: LayoutTitleBarBinding

    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initView(context)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        initView(context)
    }

    private fun initView(context: Context?) {
        mBinding = DataBindingUtil.inflate(
            context!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater,
            R.layout.layout_title_bar,
            this@TitleBar, false
        )
        mBinding.root.tag = "layout/layout_status_title_bar_0"
        addView(mBinding.root)
        mBinding.onclick = this
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.title_bar_back_iv, R.id.title_bar_left_cancel_tv -> {
                mOnTitleBarClickListener?.onBack()
            }
            R.id.title_bar_confirm_left_iv -> {
                mOnTitleBarClickListener?.onLeftConfirm()
            }

            R.id.title_bar_right_confirm_tv -> {
                mOnTitleBarClickListener?.onRightConfirmTv()
            }
            R.id.title_bar_confirm_right_iv -> {
                mOnTitleBarClickListener?.onRightConfirm(mBinding.titleBarConfirmRightIv)
            }
            else -> {
            }
        }
    }


    /**
     * 设置返回显隐
     * */
    fun setBackVisibility(isVisible: Boolean) {
        if (isVisible) {
            mBinding.titleBarBackIv.visibility = View.VISIBLE
        } else {
            mBinding.titleBarBackIv.visibility = View.GONE
        }
    }

    /**
     * 设置返回显隐
     * */
    fun setBackTvVisibility(isVisible: Boolean) {
        if (isVisible) {
            mBinding.titleBarLeftCancelTv.visibility = View.VISIBLE
        } else {
            mBinding.titleBarLeftCancelTv.visibility = View.GONE
        }
    }

    /**
     * 设置返回按钮图片
     * */
    fun setBackImageRes(resId: Int) {
        mBinding.titleBarBackIv.setImageResource(resId)
    }

    /**
     * 设置返回文案
     * */
    fun setCancelText(title: String?) {
        mBinding.titleBarLeftCancelTv.text = title
    }

    /**
     * 设置返回文案
     * */
    fun setCancelTextSize(textSize: Float) {
        mBinding.titleBarLeftCancelTv.textSize = textSize
    }

    /**
     * 设置title文案
     * */
    fun setTitleText(title: String?) {
        mBinding.titleBarTitleTv.text = title
    }

    /**
     * 设置title文字大小
     * */
    fun setTitleSize(size: Int) {
        mBinding.titleBarTitleTv.textSize = size.toFloat()
    }

    /**
     * 设置title颜色
     * */
    fun setTitleColor(color: Int) {
        mBinding.titleBarTitleTv.setTextColor(color)
    }

    /**
     * 设置RightConfirm文案
     * */
    fun setRightConfirmText(title: String?) {
        mBinding.titleBarRightConfirmTv.visibility = View.VISIBLE
        mBinding.titleBarRightConfirmTv.text = title
    }

    fun getRightConfirmText() {
        mBinding.titleBarRightConfirmTv.text
    }

    /**
     * 设置RightConfirm可否点击
     * */
    fun setRightConfirmEnable(enable: Boolean) {
        mBinding.titleBarRightConfirmTv.isEnabled = enable
    }

    /**
     * 设置RightConfirm文字大小
     * */
    fun setRightConfirmSize(size: Int) {
        mBinding.titleBarRightConfirmTv.textSize = size.toFloat()
    }

    /**
     * 设置RightConfirm颜色
     * */
    fun setRightConfirmColor(color: Int) {
        mBinding.titleBarRightConfirmTv.setTextColor(color)
    }

    /**
     * 设置RightConfirm背景颜色
     * */
    fun setRightConfirmBg(resId: Int) {
        mBinding.titleBarRightConfirmTv.setBackgroundResource(resId)
    }

    /**
     * 设置leftConfirm背景图片
     * */
    fun setLeftConfirmSrc(resId: Int) {
        mBinding.titleBarConfirmLeftIv.visibility = View.VISIBLE
        mBinding.titleBarConfirmLeftIv.setImageResource(resId)
    }

    fun setLeftCancelText(text: String) {
        mBinding.titleBarLeftCancelTv.visibility = View.VISIBLE
        mBinding.titleBarLeftCancelTv.text = text
        mBinding.titleBarBackIv.visibility = View.GONE
    }

    /**
     * 设置RightConfirm背景图片
     * */
    fun setRightConfirmSrc(resId: Int) {
        mBinding.titleBarConfirmRightIv.visibility = View.VISIBLE
        mBinding.titleBarConfirmRightIv.setImageResource(resId)
    }
    /**
     * 设置RightConfirm显隐
     * */
    fun setRightConfirmVisibility(visible: Boolean) {
        if (visible) {
            mBinding.titleBarConfirmRightIv.visibility = View.VISIBLE
        } else {
            mBinding.titleBarConfirmRightIv.visibility = View.GONE
        }
    }

    /**
     * 设置LeftConfirm显隐
     * */
    fun setLeftConfirmVisibility(visible: Boolean) {
        if (visible) {
            mBinding.titleBarConfirmLeftIv.visibility = View.VISIBLE
        } else {
            mBinding.titleBarConfirmLeftIv.visibility = View.GONE
        }
    }

    /**
     * 设置背景颜色
     */
    fun setSelfBackColor(color: Int) {
        mBinding.layoutParent.setBackgroundColor(color)
    }

    interface OnTitleBarClickListener {
        fun onBack()
        fun onLeftConfirm()
        fun onRightConfirmTv()
        fun onRightConfirm(view: View)
    }

    var mOnTitleBarClickListener: OnTitleBarClickListener? = null

}