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
import com.aibabel.voicetrans.utils.LanguageUtils

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

        val displayLang = if (mode == 1) "zh" else "id"
        val langLabel = getLangLabel(sourceLang, displayLang) + " → " + getLangLabel(targetLang, displayLang)
        tvLangLabel!!.text = langLabel

        // 根据mode和语言标识决定显示哪个语言的文本
        // mode=0: 显示印尼语
        // mode=1: 显示中文
        val waitingResId = if (mode == 1) R.string.waiting_for_chinese else R.string.waiting_for_indonesian
        val textToShow = if (mode == 1) {
            if (sourceLang == "zh") sourceText ?: LanguageUtils.getStringByLang(context, displayLang, waitingResId)
            else if (targetLang == "zh") targetText ?: LanguageUtils.getStringByLang(context, displayLang, waitingResId)
            else LanguageUtils.getStringByLang(context, displayLang, waitingResId)
        } else {
            if (sourceLang == "id") sourceText ?: LanguageUtils.getStringByLang(context, displayLang, waitingResId)
            else if (targetLang == "id") targetText ?: LanguageUtils.getStringByLang(context, displayLang, waitingResId)
            else LanguageUtils.getStringByLang(context, displayLang, waitingResId)
        }

        contentTextView!!.text = textToShow

        contentScrollView?.post {
            contentScrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    fun clearContent(mode: Int = 0) {
        val displayLang = if (mode == 1) "zh" else "id"
        contentTextView?.text = LanguageUtils.getStringByLang(context, displayLang, R.string.waiting_for_translation)
        tvLangLabel?.text = LanguageUtils.getStringByLang(context, displayLang, R.string.translation_result)
    }

    fun getContentScrollView(): ScrollView? = contentScrollView

    fun getContentTextView(): TextView? = contentTextView

    private fun getLangLabel(lang: String?, displayLang: String): String {
        return when (lang) {
            "zh" -> if (displayLang == "zh") "中文" else "Mandarin"
            "en" -> if (displayLang == "zh") "英文" else "English"
            "id" -> if (displayLang == "zh") "印尼文" else "Indonesia"
            else -> lang ?: ""
        }
    }
}
