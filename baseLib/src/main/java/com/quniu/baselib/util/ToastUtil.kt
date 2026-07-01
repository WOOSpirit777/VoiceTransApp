package com.quniu.baselib.util

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.quniu.baselib.BaseApplication
import com.quniu.baselib.R
import com.quniu.baselib.databinding.LayoutToastBinding


object ToastUtil {
    private var isShow = true //默认显示
    private var mToast: Toast = Toast(BaseApplication.mContext) //全局唯一的Toast
    private lateinit var mDataBinding: LayoutToastBinding

    init {
        val customView = View.inflate(
            BaseApplication.mContext,
            R.layout.layout_toast,
            null
        )
        customView?.let {
            mDataBinding = DataBindingUtil.bind(it)!!
            mToast.view = it
            mToast.setGravity(Gravity.BOTTOM, 0, 50)
        }
    }

    /**
     * 全局控制是否显示Toast
     * @param isShowToast
     */
    fun controlShow(isShowToast: Boolean) {
        isShow = isShowToast
    }

    /**
     * 取消Toast显示
     */
    fun cancelToast() {
        if (isShow) {
            mToast.cancel()
        }
    }

    /**
     * 短时间显示Toast
     *
     * @param context
     * @param message
     */
    @SuppressLint("ShowToast")
    fun showShort(message: String, shortToast: Boolean = true) {
        try {
            message.let {
                val toast = Toast.makeText(BaseApplication.mContext, it, Toast.LENGTH_SHORT)
//                toast.setGravity(Gravity.CENTER, 0, 0)
                toast.show()
            }
//            EventBus.getDefault()
//                .post(CustomEvent(CustomEventKey.EVENT_TOAST, ToastBean(message, shortToast)))
        } catch (e: Exception) {

        }
    }

    /**
     * 短时间显示Toast
     *
     * @param context
     * @param resId 资源ID:getResources().getString(R.string.xxxxxx);
     */
    @SuppressLint("ShowToast")
    fun showShort(resId: Int) {
        if (isShow) {
            mToast.duration = Toast.LENGTH_SHORT
            mDataBinding.customToastTv.text = BaseApplication.mContext.getString(resId)
            mToast.show()
        }
    }

    /**
     * 长时间显示Toast
     *
     * @param context
     * @param message
     */
    fun showLong(message: CharSequence?) {
        if (isShow) {
            mToast.duration = Toast.LENGTH_LONG

            mDataBinding.customToastTv.text = message
            mToast.show()
        }
    }

    /**
     * 长时间显示Toast
     *
     * @param context
     * @param resId 资源ID:getResources().getString(R.string.xxxxxx);
     */
    fun showLong(resId: Int) {
        if (isShow) {
            mToast.duration = Toast.LENGTH_LONG
            mDataBinding.customToastTv.text = BaseApplication.mContext.getString(resId)
            mToast.show()
        }
    }

    /**
     * 自定义显示Toast时间
     *
     * @param context
     * @param message
     * @param duration 单位:毫秒
     */
    fun show(message: CharSequence?, duration: Int) {
        if (isShow) {
            mToast.duration = duration
            mDataBinding.customToastTv.text = message
            mToast.show()
        }
    }

    /**
     * 自定义显示Toast时间
     *
     * @param context
     * @param resId 资源ID:getResources().getString(R.string.xxxxxx);
     * @param duration 单位:毫秒
     */
    fun show(resId: Int, duration: Int) {
        if (isShow) {
            mToast.duration = duration
            mDataBinding.customToastTv.text = BaseApplication.mContext.getString(resId)
            mToast.show()
        }
    }

    /**
     * 自定义Toast的View
     * @param context
     * @param message
     * @param duration 单位:毫秒
     * @param view 显示自己的View
     */
    @SuppressLint("ShowToast")
    fun customToastView(
        context: Context?,
        message: CharSequence?,
        duration: Int,
        view: View?
    ) {
        if (isShow) {
            mToast.setText(message)
            if (view != null) {
                mToast.view = view
            }
            mToast.show()
        }
    }

    /**
     * 自定义Toast的位置
     * @param context
     * @param message
     * @param duration 单位:毫秒
     * @param gravity
     * @param xOffset
     * @param yOffset
     */
    @SuppressLint("ShowToast")
    fun customToastGravity(
        context: Context?,
        message: CharSequence?,
        duration: Int,
        gravity: Int,
        xOffset: Int,
        yOffset: Int
    ) {
        if (isShow) {
            mToast.setText(message)
            mToast.setGravity(gravity, xOffset, yOffset)
            mToast.show()
        }
    }

    /**
     * 自定义带图片和文字的Toast，最终的效果就是上面是图片，下面是文字
     * @param context
     * @param message
     * @param iconResId 图片的资源id,如:R.drawable.icon
     * @param duration
     * @param gravity
     * @param xOffset
     * @param yOffset
     */
    @SuppressLint("ShowToast")
    fun showToastWithImageAndText(
        context: Context?,
        message: CharSequence?,
        iconResId: Int,
        duration: Int,
        gravity: Int,
        xOffset: Int,
        yOffset: Int
    ) {
        if (isShow) {
            mToast.setText(message)
            mToast.setGravity(gravity, xOffset, yOffset)
            val toastView = mToast.view as LinearLayout
            val imageView = ImageView(context)
            imageView.setImageResource(iconResId)
            toastView.addView(imageView, 0)
            mToast.show()
        }
    }

    /**
     * 自定义Toast,针对类型CharSequence
     * @param context
     * @param message
     * @param duration
     * @param view
     * @param isGravity true,表示后面的三个布局参数生效,false,表示不生效
     * @param gravity
     * @param xOffset
     * @param yOffset
     * @param isMargin true,表示后面的两个参数生效，false,表示不生效
     * @param horizontalMargin
     * @param verticalMargin
     */
    @SuppressLint("ShowToast")
    fun customToastAll(
        context: Context?,
        message: CharSequence?,
        duration: Int,
        view: View?,
        isGravity: Boolean,
        gravity: Int,
        xOffset: Int,
        yOffset: Int,
        isMargin: Boolean,
        horizontalMargin: Float,
        verticalMargin: Float
    ) {
        if (isShow) {
            mToast.setText(message)
            if (view != null) {
                mToast.view = view
            }
            if (isMargin) {
                mToast.setMargin(horizontalMargin, verticalMargin)
            }
            if (isGravity) {
                mToast.setGravity(gravity, xOffset, yOffset)
            }
            mToast.show()
        }
    }

    /**
     * 自定义Toast,针对类型resId
     * @param context
     * @param resId
     * @param duration
     * @param view :应该是一个布局，布局中包含了自己设置好的内容
     * @param isGravity true,表示后面的三个布局参数生效,false,表示不生效
     * @param gravity
     * @param xOffset
     * @param yOffset
     * @param isMargin true,表示后面的两个参数生效，false,表示不生效
     * @param horizontalMargin
     * @param verticalMargin
     */
    @SuppressLint("ShowToast")
    fun customToastAll(
        context: Context?,
        resId: Int,
        duration: Int,
        view: View?,
        isGravity: Boolean,
        gravity: Int,
        xOffset: Int,
        yOffset: Int,
        isMargin: Boolean,
        horizontalMargin: Float,
        verticalMargin: Float
    ) {
        if (isShow) {
            mToast.setText(resId)
            if (view != null) {
                mToast.view = view
            }
            if (isMargin) {
                mToast.setMargin(horizontalMargin, verticalMargin)
            }
            if (isGravity) {
                mToast.setGravity(gravity, xOffset, yOffset)
            }
            mToast.show()
        }
    }
}