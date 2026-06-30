package com.aibabel.voicetrans.ui;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aibabel.voicetrans.R;

/**
 * 副屏显示Presentation
 * 用于在HDMI外接屏幕上显示翻译结果
 */
public class SecondaryPresentation extends Presentation {

    private ScrollView contentScrollView;
    private TextView contentTextView;
    private TextView tvLangLabel;

    public SecondaryPresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 获取HDMI屏幕实际分辨率
        DisplayMetrics metrics = new DisplayMetrics();
        getDisplay().getMetrics(metrics);
        int screenW = metrics.widthPixels;
        int screenH = metrics.heightPixels;

        setContentView(R.layout.display_secondary);

        // 向右旋转90°：将竖屏内容显示在横屏HDMI屏上
        View rootView = findViewById(R.id.secondary_screen);
        if (rootView != null) {
            ViewGroup.LayoutParams lp = rootView.getLayoutParams();
            if (lp == null) {
                lp = new FrameLayout.LayoutParams(screenH, screenW);
            } else {
                lp.width = screenH;
                lp.height = screenW;
            }
            rootView.setLayoutParams(lp);

            // 旋转90°并调整位置
            rootView.setPivotX(0);
            rootView.setPivotY(0);
            rootView.setRotation(90);
            rootView.setTranslationX(screenW);
        }

        contentScrollView = findViewById(R.id.contentScrollView);
        contentTextView = findViewById(R.id.contentTextView);
        tvLangLabel = findViewById(R.id.tvLangLabel);
    }

    /**
     * 更新副屏内容
     * @param targetText 目标语言文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     */
    public void updateContent(String targetText, String sourceLang, String targetLang) {
        if (contentTextView != null && targetText != null) {
            contentTextView.setText(targetText);
        }
        if (tvLangLabel != null && sourceLang != null && targetLang != null) {
            String langLabel = getLangLabel(sourceLang) + " → " + getLangLabel(targetLang);
            tvLangLabel.setText(langLabel);
        }
        if (contentScrollView != null) {
            contentScrollView.scrollTo(0, 0);
        }
    }

    /**
     * 清空内容
     */
    public void clearContent() {
        if (contentTextView != null) {
            contentTextView.setText("等待翻译...");
        }
        if (tvLangLabel != null) {
            tvLangLabel.setText("翻译结果");
        }
    }

    public ScrollView getContentScrollView() {
        return contentScrollView;
    }

    public TextView getContentTextView() {
        return contentTextView;
    }

    private String getLangLabel(String lang) {
        switch (lang) {
            case "zh":
                return "中文";
            case "en":
                return "英文";
            case "id":
                return "印尼文";
            default:
                return lang;
        }
    }
}