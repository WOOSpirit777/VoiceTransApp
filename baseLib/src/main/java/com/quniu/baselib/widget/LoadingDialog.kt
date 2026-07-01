package com.quniu.baselib.widget

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import com.github.ybq.android.spinkit.SpinKitView
import com.quniu.baselib.R
import androidx.core.graphics.toColorInt


/**
 * @ClassName LoadingDialog
 * @Description 加载中弹窗
 * @Author Vvv
 */
class LoadingDialog(context: Context) : Dialog(context, R.style.CustomLoadingDialog) {

    //    private var ani: AnimationDrawable? = null
    var iv: ImageView? = null
    var progress: SpinKitView? = null

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_loading)


// 在Activity或Fragment中使用
        progress = findViewById(R.id.progress)
        progress?.visibility = View.VISIBLE
        progress?.setColor("#FF6D2E".toColorInt())
        setCanceledOnTouchOutside(true)
//        ani = AnimationDrawable()
//        ani?.addFrame(BaseApplication.mContext.resources.getDrawable(R.drawable.loading_on), 150)
//        ani?.addFrame(BaseApplication.mContext.resources.getDrawable(R.drawable.loading_off), 150)
//        iv.setImageDrawable(ani)

       /* iv?.let {
            PicUtil.loadGifImage(context, R.drawable.ic_loading, it)
        }*/
    }

//    override fun onWindowFocusChanged(hasFocus: Boolean) {
//        if (hasFocus) {
//            ani?.start()
//        } else {
//            ani?.stop()
//        }
//        super.onWindowFocusChanged(hasFocus)
//
//    }

    override fun onDetachedFromWindow() {
        progress?.let {
            it.visibility = View.GONE
        }
        super.onDetachedFromWindow()
    }
}