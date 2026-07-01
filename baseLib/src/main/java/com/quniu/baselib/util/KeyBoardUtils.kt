package com.quniu.baselib.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.quniu.baselib.BaseApplication

/**
 * @ClassName KeyBoardUtils
 * @Description 软键盘工具类
 */
object KeyBoardUtils {
    /**
     * 打开软键盘
     *
     * @param view
     * @param mContext 上下文
     */
    fun openKeyboard(view: View?, mContext: Context) {
        val imm = mContext
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.RESULT_SHOWN)
        imm.toggleSoftInput(
            InputMethodManager.SHOW_FORCED,
            InputMethodManager.HIDE_IMPLICIT_ONLY
        )
    }

    /**
     * 关闭软键盘
     *
     * @param view
     * @param mContext 上下文
     */
    fun closeKeyboard(view: View, mContext: Context) {
        val imm = mContext
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }


    /**
     * 判断软键盘是否打开 若返回true，则表示输入法打开
     */
    fun isSoftKeyboardShowed(): Boolean {
        val app: Application = BaseApplication.mContext
        val imm =
            app.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.isActive
    }

    /**
     * 关闭软键盘
     * @param context
     */
    fun hintKeyBoard(context: Activity) {
        //拿到InputMethodManager
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        //如果window上view获取焦点 && view不为空
        if (imm.isActive && context.currentFocus != null) {
            //拿到view的token 不为空
            if (context.currentFocus!!.windowToken != null) {
                //表示软键盘窗口总是隐藏，除非开始时以SHOW_FORCED显示。
                imm.hideSoftInputFromWindow(
                    context.currentFocus!!.windowToken,
                    InputMethodManager.HIDE_NOT_ALWAYS
                )
            }
        }
    }

    fun showKeyBoard(v: View) {
        val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm != null) {
            v.requestFocus()
            imm.showSoftInput(v, InputMethodManager.SHOW_FORCED)
        }
    }

    fun showKeyBoardForce(v: View) {
        val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm != null) {
            v.requestFocus()
            imm.showSoftInput(v, 0)
        }
    }
}