package com.aibabel.voicetrans.ui

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aibabel.voicetrans.R

class SecondaryPresentation(outerContext: Context, display: Display) : Presentation(outerContext, display) {

    private var contentScrollView: ScrollView? = null
    private var contentTextView: TextView? = null
    private var tvLangLabel: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val metrics = DisplayMetrics()
        display.getMetrics(metrics)
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        setContentView(R.layout.display_secondary)

        val rootView = findViewById<View>(R.id.secondary_screen)
        if (rootView != null) {
            var lp = rootView.layoutParams
            if (lp == null) {
                lp = FrameLayout.LayoutParams(screenH, screenW)
            } else {
                lp.width = screenH
                lp.height = screenW
            }
            rootView.layoutParams = lp

            rootView.pivotX = 0f
            rootView.pivotY = 0f
            rootView.rotation = 90f
            rootView.translationX = screenW.toFloat()
        }

        contentScrollView = findViewById(R.id.contentScrollView)
        contentTextView = findViewById(R.id.contentTextView)
        tvLangLabel = findViewById(R.id.tvLangLabel)
    }

    fun updateContent(targetText: String?, sourceLang: String?, targetLang: String?) {
        if (contentTextView != null && targetText != null) {
            contentTextView!!.text = targetText
        }
        if (tvLangLabel != null && sourceLang != null && targetLang != null) {
            val langLabel = getLangLabel(sourceLang) + " → " + getLangLabel(targetLang)
            tvLangLabel!!.text = langLabel
        }
        contentScrollView?.scrollTo(0, 0)
    }

    fun clearContent() {
        contentTextView?.text = "等待翻译..."
        tvLangLabel?.text = "翻译结果"
    }

    fun getContentScrollView(): ScrollView? = contentScrollView

    fun getContentTextView(): TextView? = contentTextView

    private fun getLangLabel(lang: String?): String {
        return when (lang) {
            "zh" -> "中文"
            "en" -> "英文"
            "id" -> "印尼文"
            else -> lang ?: ""
        }
    }
}
