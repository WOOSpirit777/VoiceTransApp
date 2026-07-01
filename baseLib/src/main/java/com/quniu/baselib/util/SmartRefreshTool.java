package com.quniu.baselib.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import com.quniu.baselib.widget.EmptyView;
import com.scwang.smart.refresh.footer.ClassicsFooter;
import com.scwang.smart.refresh.header.ClassicsHeader;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;

public final class SmartRefreshTool {
    @SuppressLint("WrongConstant")
    public static void initTable(Activity activity, SmartRefreshLayout refreshLayout, boolean isAutoRefresh) {
        // enable basic behaviors
        refreshLayout.autoRefresh();
        refreshLayout.setEnableLoadMore(true);
        refreshLayout.setDisableContentWhenRefresh( false);
        refreshLayout.setDisableContentWhenLoading( false);
        // set default header/footer
        refreshLayout.setRefreshHeader(new ClassicsHeader(activity));
        refreshLayout.setRefreshFooter(new ClassicsFooter(activity));


        // auto refresh if requested (deferred to ensure view attached)
    }

    private static EmptyView findExistingEmptyView(SmartRefreshLayout refreshLayout) {
        for (int i = 0; i < refreshLayout.getChildCount(); i++) {
            View v = refreshLayout.getChildAt(i);
            if (v instanceof EmptyView) return (EmptyView) v;
        }
        return null;
    }

    private static void addEmptyViewToRefreshLayout(SmartRefreshLayout refreshLayout, EmptyView emptyView) {
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        try {
            refreshLayout.addView(emptyView, lp);
        } catch (Throwable ignored) {
        }
    }

}
