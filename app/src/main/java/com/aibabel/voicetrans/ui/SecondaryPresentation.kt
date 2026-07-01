package com.aibabel.voicetrans.ui

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.View
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
            val lp = FrameLayout.LayoutParams(screenH, screenW)
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

    fun updateContent(sourceText: String?, targetText: String?, sourceLang: String?, targetLang: String?, mode: Int = 0) {
        if (contentTextView == null || tvLangLabel == null) {
            return
        }

        val langLabel = getLangLabel(sourceLang) + " → " + getLangLabel(targetLang)
        tvLangLabel!!.text = langLabel

        // 根据mode和语言标识决定显示哪个语言的文本
        // mode=0: 显示印尼语
        // mode=1: 显示中文
        val textToShow = if (mode == 1) {
            // 显示中文：如果sourceLang是中文就显示sourceText，否则显示targetText（如果targetLang是中文）
            if (sourceLang == "zh") sourceText ?: "等待识别..."
            else if (targetLang == "zh") targetText ?: "等待翻译..."
            else "等待中文内容..."
        } else {
            // 显示印尼语：如果sourceLang是印尼语就显示sourceText，否则显示targetText（如果targetLang是印尼语）
            if (sourceLang == "id") sourceText ?: "等待识别..."
            else if (targetLang == "id") targetText ?: "等待翻译..."
            else "等待印尼语内容..."
        }

        contentTextView!!.text = textToShow

        contentScrollView?.post {
            contentScrollView?.fullScroll(View.FOCUS_DOWN)
        }
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
