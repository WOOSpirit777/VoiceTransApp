package com.quniu.baselib.widget

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.quniu.baselib.R
import commonutil.PicUtil


/**
 * @ClassName CommDialog
 * @Description TODO
 * @Author max
 * @Date 2021/1/12 10:05
 * @Version 1.0.0
 */
class CommonDialog(context: Context) : Dialog(context, R.style.CustomDialog) {
    /**
     * 显示的图片
     */
    private var imageIv: ImageView? = null
    private var imageIv2: ImageView? = null

    /**
     * 显示的标题
     */
    private var titleTv: TextView? = null

    /**
     * 显示的消息
     */
    private var messageTv: TextView? = null

    /**
     * 取消按钮
     */
    private var negativeBtn: TextView? = null

    private var llBtn: LinearLayout? = null

    /**
     * 确认按钮
     */
    private var positiveBtn: TextView? = null

    /**
     * 输入框
     */
    private var contentEt: EditText? = null

    /**
     * 居左的title
     */
    private var leftTitleTv: TextView? = null

    /**
     *背景
     */
    private var parentView: LinearLayout? = null
    private var bottomTextView: TextView? = null

    /**
     * 按钮之间的分割线
     */
//    private var columnLineView: View? = null

    /**
     * 都是内容数据
     */
    var message: String? = null
    var leftTitle: String? = null
    var title: String? = null
    var editHint: String? = null
    var mPositive: String? = null
    var mPositiveBackground: Int = 0
    var mNegativeBackground: Int = 0
    var mGravity: Int = 0
    var mNegative: String? = null
    var imageResId = -1
    var imageUrl = ""

    /**
     * 底部是否只有一个按钮
     */
    var isSingle = false
    /**
     * 底部是否带按钮
     */
    var isNoBtn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_common_dialog)
        //按空白处不能取消动画
        setCanceledOnTouchOutside(true)
        //初始化界面控件
        initView()
        //初始化界面数据
        refreshView()
        //初始化界面控件的事件
        initEvent()
    }

    /**
     * 初始化界面的确定和取消监听器
     */
    private fun initEvent() {
        //设置确定按钮被点击后，向外界提供监听
        positiveBtn?.setOnClickListener {
            if (onClickBottomListener != null) {
                dismiss()
                onClickBottomListener!!.onPositiveClick(this, contentEt?.text.toString())
            }
        }
        //设置取消按钮被点击后，向外界提供监听
        negativeBtn?.setOnClickListener {
            if (onClickBottomListener != null) {
                dismiss()
                onClickBottomListener!!.onNegativeClick(this)
            }
        }
    }

    /**
     * 初始化界面控件的显示数据
     */
    private fun refreshView() {
        //如果用户自定了title和message
        if (!TextUtils.isEmpty(title)) {
            titleTv?.text = title
        }

        //居左title
        if (!TextUtils.isEmpty(leftTitle)) {
            leftTitleTv?.text = leftTitle
            leftTitleTv?.visibility = View.VISIBLE
        } else {
            leftTitleTv?.visibility = View.GONE
        }

        if (!TextUtils.isEmpty(message)) {
            messageTv?.text = message
            messageTv?.visibility = View.VISIBLE
        } else {
            messageTv?.visibility = View.GONE
        }

        if (!TextUtils.isEmpty(editHint)) {
            contentEt?.hint = editHint
            contentEt?.visibility = View.VISIBLE
        } else {
            contentEt?.visibility = View.GONE
        }
        //如果设置按钮的文字
        if (!TextUtils.isEmpty(mPositive)) {
            positiveBtn?.text = mPositive
        } else {
            positiveBtn?.text = context.getString(R.string.done)
        }
        if (mPositiveBackground != 0) {
            positiveBtn?.setBackgroundResource(mPositiveBackground)
            positiveBtn?.setTextColor(Color.WHITE)
        }

        if (mGravity != 0) {
            messageTv?.gravity = Gravity.LEFT
        }
        if (!TextUtils.isEmpty(mNegative)) {
            negativeBtn?.text = mNegative
        } else {
            negativeBtn?.text = context.getString(R.string.cancel)
        }
        if (mNegativeBackground != 0) {
            negativeBtn?.setBackgroundResource(mPositiveBackground)
            positiveBtn?.setTypeface(null, Typeface.BOLD)
        }
        if (imageResId != -1) {
            imageIv2?.setImageResource(imageResId)
            imageIv2?.visibility = View.VISIBLE
        } else {
            imageIv2?.visibility = View.GONE
        }
        if (imageUrl.isNotEmpty()) {
            imageIv?.let { PicUtil.loadImage(context, imageUrl, it) }
            imageIv?.visibility = View.VISIBLE
        }else{
            imageIv?.visibility = View.GONE
        }

        if (isNoBtn){
            /**
             * 不显示按钮
             */
            llBtn?.visibility = View.GONE
        }else{
            /**
             * 只显示一个按钮的时候隐藏取消按钮，回掉只执行确定的事件
             */
            llBtn?.visibility = View.VISIBLE
            if (isSingle) {
//            columnLineView!!.visibility = View.GONE
                negativeBtn?.visibility = View.GONE
            } else {
                negativeBtn?.visibility = View.VISIBLE
//            columnLineView!!.visibility = View.VISIBLE
            }
        }
        val layoutParams: WindowManager.LayoutParams? = window?.attributes
        layoutParams?.width = LinearLayout.LayoutParams.MATCH_PARENT
        layoutParams?.height = LinearLayout.LayoutParams.WRAP_CONTENT
        window?.attributes = layoutParams
        setRootBackground(R.drawable.bg_white_c16)
    }

    override fun show() {
        super.show()
        refreshView()
    }

    /**
     * 初始化界面控件
     */
    private fun initView() {
        negativeBtn = findViewById<View>(R.id.common_dialog_negative_tv) as TextView
        llBtn = findViewById<View>(R.id.ll_btn) as LinearLayout
        positiveBtn = findViewById<View>(R.id.common_dialog_positive_tv) as TextView
        messageTv = findViewById<View>(R.id.common_dialog_message_tv) as TextView
        leftTitleTv = findViewById<View>(R.id.common_dialog_left_title_tv) as TextView
        parentView = findViewById<View>(R.id.parentView) as LinearLayout
        bottomTextView = findViewById<View>(R.id.tv_bottom_hint) as TextView
//        columnLineView = findViewById(R.id.column_line)
    }

    /**
     * 设置确定取消按钮的回调
     */
    var onClickBottomListener: OnClickBottomListener? = null
    fun setOnClickBottomListener(onClickBottomListener: OnClickBottomListener?): CommonDialog? {
        this.onClickBottomListener = onClickBottomListener
        return this
    }

    interface OnClickBottomListener {
        /**
         * 点击确定按钮事件
         */
        fun onPositiveClick(dialog: CommonDialog, editStr: String)

        /**
         * 点击取消按钮事件
         */
        fun onNegativeClick(dialog: CommonDialog)
    }





    fun setRootBackground(res: Int) {
        parentView?.setBackgroundResource(res)
    }

    fun setTitleColor(color: String) {
        titleTv?.setTextColor(Color.parseColor(color))
    }

    fun setMessageColor(color: String) {
        messageTv?.setTextColor(Color.parseColor(color))
    }

    fun setNegativebg(res: Int) {
        negativeBtn?.setBackgroundResource(res)

    }

    fun setNegativeTextColor(color: String) {
        negativeBtn?.setTextColor(Color.parseColor(color))
    }

    fun setPositiveBg(res: Int) {
        positiveBtn?.setBackgroundResource(res)

    }

    fun setPositiveTextColor(color: String) {
        positiveBtn?.setTextColor(Color.parseColor(color))
    }

    fun setBottomVis(boolean: Boolean){
        if (boolean) bottomTextView?.visibility = View.VISIBLE else bottomTextView?.visibility = View.GONE

    }
}