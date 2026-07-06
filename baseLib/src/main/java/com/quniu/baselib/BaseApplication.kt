package com.quniu.baselib

//import androidx.multidex.MultiDex
import android.app.Application
import com.quniu.baselib.util.MMKVUtils
import commonutil.ContentProviderUtil
import netlib.NetConst
import netlib.util.PasswordUtil

open class BaseApplication : Application() {

    companion object {
        lateinit var mContext: Application
    }
    override fun onCreate() {
        super.onCreate()
        mContext = this
//        MultiDex.install(this)
        PasswordUtil.init()
        MMKVUtils.initialize(this)
        // 注册全局按键监听
        initBaseUrl()
    }
     fun initBaseUrl() {
        NetConst.BASE_URL2 = if (ContentProviderUtil.isInCN()) NetConst.CN_BASE_URL else NetConst.GLOBAL_BASE_URL
//        NetConst.BASE_URL2 ="http://192.168.1.226:18050"
    }
}