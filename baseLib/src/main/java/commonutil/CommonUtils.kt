package commonutil

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.event.CustomEvent
import com.quniu.baselib.event.CustomEventKey
import org.greenrobot.eventbus.EventBus
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CommonUtils {

    // ======================== 公共路由常量 ========================
    /** gotoSettings 默认目标包名 */
    const val DEFAULT_SETTINGS_PACKAGE = "link.zhidou.translator"

    /** 路由路径常量（供所有模块复用） */
    const val ROUTE_HOME_ACTIVITY          = ".ui.activity.HomeActivity"
    const val ROUTE_WIFI_SETTINGS          = ".ui.activity.WifiSettings"
    const val ROUTE_DATA_CARD_SETTING      = ".ui.activity.DataCardSettingActivity"
    const val ROUTE_ONLINE_VOICE_TRANS     = ".ui.activity.OnlineVoiceTransActivity"
    const val ROUTE_OFFLINE_VOICE_TRANS    = ".ui.activity.OfflineVoiceTransActivity"
    const val ROUTE_RECORDING_ACTIVITY     = ".ui.activity.RecordingActivity"
    const val ROUTE_CAMERA_ACTIVITY        = ".ui.activity.CameraActivity"
    const val ROUTE_TRANS_ONLINE_ACTIVITY  = ".ui.activity.TransOnlineActivity"
    const val ROUTE_TRANS_OFFLINE_ACTIVITY = ".ui.activity.TransOfflineActivity"

    /** gotoChat 默认目标包名 */
    const val DEFAULT_CHAT_PACKAGE = "com.aibabel.voice"
    // =============================================================

    // Returns the package name of the host application that includes this library.
    // This is a simple runtime lookup and should be called with a valid Context (Application or Activity).
    // Example: val pkg = CommonUtils.getHostAppPackageName(context)
    @JvmStatic
    fun getHostAppPackageName(ctx: Context): String {
        return try {
            // Prefer applicationContext.packageName to avoid context-wrapped package names
            ctx.applicationContext?.packageName ?: ctx.packageName
        } catch (t: Throwable) {
            Log.w("CommonUtils", "getHostAppPackageName failed", t)
            ""
        }
    }
    fun killAppByPackageName(context: Context, packageName: String): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return try {
            // 只能杀死后台进程，无法杀死前台运行的应用
            activityManager.killBackgroundProcesses(packageName)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    // Java-friendly alias
    @JvmStatic
    fun getHostAppPackageNameJvm(ctx: Context): String = getHostAppPackageName(ctx)

    fun gotoChat(targetPackage: String = DEFAULT_CHAT_PACKAGE, context: Context) {
        if (targetPackage.isBlank()) {
            return
        }
        try {
            val pm = context.packageManager
            val launch = pm.getLaunchIntentForPackage(targetPackage)
            if (launch != null) {
                // 目标应用已安装，直接启动
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (getHostAppPackageNameJvm(context) == "com.aibabel.scenicareaguide"){
                    EventBus.getDefault().post(CustomEvent(CustomEventKey.EVENT_SKIP_VOICE))
                    launch.putExtra("isFromScenicSpot", true)
                }
                launch.putExtra("isLongPress", true)
                context.startActivity(launch)
                return
            }
        } catch (e: Exception) {
            // 全部失败时给出提示
            Log.e("AudioPlayerView", "跳转目标应用失败: $targetPackage", e)
        }
    }

    fun gotoSettings(
        targetUi: String,
        ctx: Context,
        needParams: Boolean = true,
        targetPackage: String = DEFAULT_SETTINGS_PACKAGE
    ) {
        val targetActivity = targetPackage + targetUi

        val targetIntent = Intent().apply {
            component = ComponentName(targetPackage, targetActivity)
            if (needParams) putExtra("isFromOut", true)
            if (!needParams) putExtra("fromApp", getHostAppPackageNameJvm(ctx))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(targetIntent)
        } catch (e: Exception) {
            LogUtils.e("启动Activity $targetActivity 失败 e：$e" )
        }
    }
    /**
     * 判断网络是否可用
     *
     * @return true: 可用 false: 不可用
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val info: NetworkInfo? = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
        return info != null && info.isAvailable
    }

    /**
     * 判断当时使用是否为wifi网络
     *
     * */
    fun isWifi(context: Context): Boolean {
        val info = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
        return info != null && info.isConnected && info.type == ConnectivityManager.TYPE_WIFI
    }

    //    判断是否是移动网络
    fun isMobile(context: Context): Boolean {
        val info = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
        return info != null && info.isConnected && info.type == ConnectivityManager.TYPE_MOBILE
    }

    @SuppressLint("MissingPermission")
    fun isUsingEsimForInternetOreo(context: Context): Boolean {
        try {
            // 检查网络是移动数据
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return false

            // 权限检查
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
                // 无权限时无法安全判断
                return false
            }

            // 获取默认数据订阅
            val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
            if (dataSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return false

            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager ?: return false
            val info: SubscriptionInfo = sm.getActiveSubscriptionInfo(dataSubId) ?: return false

            // API 29+ 使用官方方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return info.isEmbedded
            }

            // 1) 反射尝试（某些厂商在旧 API 上有隐藏实现）
            try {
                val m = info.javaClass.getMethod("isEmbedded")
                val res = m.invoke(info)
                if (res is Boolean) return res
            } catch (_: Exception) {
                // ignore
            }

            // 2) 启发式判断：部分设备 eSIM 的 simSlotIndex 可能为负值或 -1
            try {
                val slot = info.simSlotIndex
                if (slot < 0) return true
            } catch (_: Exception) { /* ignore */ }

            // 3) 启发式判断：检查 ICCID（不能保证可靠，但做为兜底）
            try {
                val iccid = info.iccId
                if (!iccid.isNullOrBlank()) {
                    // 某些 eSIM ICCID 会包含标识或长度差异，这里仅做简单长度/模式判断
                    if (iccid.length >= 19) {
                        // 只是一个弱启发：若同时设备只有一张 profile 且 slotIndex 异常，可认为可能是 eSIM
                        val activeList = sm.activeSubscriptionInfoList
                        if (activeList != null && activeList.size == 1 && info.simSlotIndex < 0) {
                            return true
                        }
                    }
                }
            } catch (_: Exception) { /* ignore */ }

            // 不能确定则返回 false
            return false
        } catch (t: Throwable) {
            Log.w("ESIM_CHECK", "isUsingEsimForInternetOreo failed", t)
            return false
        }
    }
    /**
     * 获取系统版本号
     *
     * @return
     */
    fun getDeviceInfo(): String {
        return Build.VERSION.RELEASE
    }
    /**
     * 获取设备SN号（简化版）
     *
     * @return 设备SN号
     */
    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    @SuppressLint("HardwareIds")
    fun getSN(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                Build.SERIAL
            }
        } catch (e: Exception) {
            "L6087YJ00000009"
        }
    }
    /**
     * 获取本机SN 设备识别码
     *
     * @return
     */
    fun getSN2(): String {
        var sn = "0000000000000000"
//        var sn = "ZETA304060085"
        try {
            val clz = Class.forName("android.os.SystemProperties")
            val method = clz.getMethod("get", String::class.java, String::class.java)
            sn = method.invoke(clz, "gsm.serial", "0000000000000000") as String
            sn.trim()
            if (sn.indexOf(" ") != -1) {
                sn = sn.substring(0, sn.indexOf(" "))
            }
            Log.e("CommonUtils", "sn=$sn")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CommonUtils", e.message ?: "")
        }
        return sn
    }
    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    fun getIMEIBySlot(ctx: Context, slotIndex: Int): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val telephonyManager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.getImei(slotIndex)
        } else {
            // Android O以下版本可能需要反射或无法获取特定槽位
            @Suppress("DEPRECATION")
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (slotIndex == 0) tm.deviceId else null
        }
    }


    /**
     * 获取手机IMEI(需要"android.permission.READ_PHONE_STATE"权限)
     *
     * @return 手机IMEI
     */
    @SuppressLint("MissingPermission")
    fun getIMEI(ctx: Context): String? {
        val tm = ctx.getSystemService(Activity.TELEPHONY_SERVICE) as TelephonyManager
        return tm.deviceId
    }

    @SuppressLint("MissingPermission")
    fun getAllSimInfo(ctx: Context): Map<Int, String?> {
        val simInfo = mutableMapOf<Int, String?>()

        val subscriptionManager = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList

        subscriptionInfoList?.forEach { info ->
            val slotIndex = info.simSlotIndex
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telephonyManager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                simInfo[slotIndex] = telephonyManager.getImei(slotIndex)
            }
        }

        return simInfo
    }

    /**
     * 判断是否黑屏
     *
     * @param c
     * @return
     */
    fun isScreenLocked(c: Context): Boolean {
        val powerManager = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        //true为打开，false为关闭
        val ifOpen = powerManager.isScreenOn
        return ifOpen
    }

    fun getCurrentWifiInfo(context: Context): WifiInfo? {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.connectionInfo
    }
    /**
     * 尝试获取设备当前的 MCC (Mobile Country Code)。优先使用 TelephonyManager 的 simOperator/networkOperator，
     * 若不可用再尝试 SubscriptionInfo.mcc，最后回退到 Resources.configuration.mcc。
     * 返回 String (例如 "460") 或 null 表示无法获取。
     */
    @SuppressLint("MissingPermission")
    fun getDeviceMcc(ctx: Context): String? {
        try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            var op: String? = null
            try {
                op = tm.simOperator
            } catch (_: Exception) {
            }
            if (op.isNullOrBlank()) {
                try { op = tm.networkOperator } catch (_: Exception) { }
            }
            if (!op.isNullOrBlank() && op.length >= 3) {
                val mcc = op.substring(0, 3)
                LogUtils.d("Detected MCC from operator string: $mcc")
                return mcc
            }

            // try SubscriptionInfo
            try {
                // Ensure we have READ_PHONE_STATE before accessing SubscriptionInfo
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                        val sub = sm.getActiveSubscriptionInfoForSimSlotIndex(1)
                        if (sub != null) {
                            @Suppress("DEPRECATION")
                            val mccInt = sub.mcc
                            if (mccInt > 0) {
                                val mcc = mccInt.toString()
                                LogUtils.d("Detected MCC from SubscriptionInfo: $mcc")
                                return mcc
                            }
                        }
                    } catch (se: SecurityException) {
                        LogUtils.e("No permission to read subscription info", se)
                    }
                }
            } catch (_: Exception) {
            }

            // fallback: resources configuration
            try {
                val cfgMcc = ctx.resources.configuration.mcc
                if (cfgMcc > 0) {
                    val mcc = cfgMcc.toString()
                    LogUtils.d("Detected MCC from resources.configuration: $mcc")
                    return mcc
                }
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            LogUtils.e("getDeviceMcc failed: ", e)
        }
        LogUtils.d("MCC not available")
        return null
    }

    /**
     * [获取应用程序版本名称信息]
     *
     * @param context
     * @return 当前应用的版本名称
     */
    fun getVersionName(context: Context): String? {
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(
                context.packageName, 0
            )
            packageInfo.versionName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun genHMAC(data: String, key: String): String? {
        var result: String? = null
        try {
            //根据给定的字节数组构造一个密钥,第二参数指定一个密钥算法的名称
            val signinKey = SecretKeySpec(key.toByteArray(), "HmacSHA1")
            //生成一个指定 Mac 算法 的 Mac 对象
            val mac: Mac = Mac.getInstance("HmacSHA1")
            //用给定密钥初始化 Mac 对象
            mac.init(signinKey)
            //完成 Mac 操作
            val rawHmac = mac.doFinal(data.toByteArray())
            result = Base64.encodeToString(rawHmac, Base64.DEFAULT)
        } catch (e: NoSuchAlgorithmException) {
            System.err.println(e.message)
        } catch (e: InvalidKeyException) {
            System.err.println(e.message)
        }
        return result
    }
    /**
     * 获取设备MAC地址
     * 注意：Android 6.0及以上版本由于隐私政策限制，此方法可能返回固定值
     * @param context 上下文
     * @return MAC地址字符串
     */
    @SuppressLint("HardwareIds")
    fun getMacAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo

            // Android 6.0及以上版本会返回固定值，这是系统隐私保护机制
            var macAddress = wifiInfo.macAddress

            // 如果获取到的是固定值，则尝试从系统属性获取（需要root权限或系统级应用）
            if (macAddress == null || macAddress == "02:00:00:00:00:00" || macAddress == "00:00:00:00:00:00:00:F8") {
                try {
                    val clz = Class.forName("android.os.SystemProperties")
                    val method = clz.getMethod("get", String::class.java, String::class.java)
                    macAddress = method.invoke(clz, "ro.boot.wifimacaddr", "") as String
                } catch (e: Exception) {
                    Log.e("CommonUtils", "获取MAC地址失败", e)
                }
            }

            // 如果仍然无法获取，返回默认值
            return macAddress ?: "00:00:00:00:00:00"
        } catch (e: Exception) {
            Log.e("CommonUtils", "获取MAC地址异常", e)
            return "00:00:00:00:00:00"
        }
    }
    /**
     * 获取真实的MAC地址（出厂MAC地址）
     * 通过反射调用WifiManager的隐藏方法getFactoryMacAddress()
     * @param context 上下文
     * @return 真实MAC地址或null
     */
    fun getRealMacAddress(context: Context): String? {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method = wifiManager.javaClass.getDeclaredMethod("getFactoryMacAddress")
            method.isAccessible = true
            val result = method.invoke(wifiManager)
            result as? String
        } catch (e: Exception) {
            e.printStackTrace()
            getMacAddress(context)
        }
    }

    /*
     * 获取系统版本号 去除 "-"
     */
    fun getDevice(): String {
        var result = ""
        val display = Build.DISPLAY
        result = display.replace("-", "")
        Log.e("result", result)
        return result
    }

    /**
     * 判断某个服务是否正在运行的方法
     *
     * @param mContext
     * @param serviceName 是包名（例如：net.loonggg.testbackstage）
     * @return true代表正在运行，false代表服务没有正在运行
     * <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
     */
    fun isServiceRunning(context: Context, serviceClassName: String): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1 * 60 * 1000L // 5分钟内

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )
        stats.forEach {
            Log.d("UsageStats","UsageStats: ${it.packageName}")
        }

        // 检查最近的使用记录中是否包含目标服务
        return stats.any {
            it.packageName == serviceClassName // 1分钟内使用过
        }
    }

    /** 开启或关闭 GPS，需要系统权限 */
    fun setGpsEnabled(context: Context, enable: Boolean) {
        try {
            Settings.Secure.setLocationProviderEnabled(
                context.contentResolver,
                LocationManager.GPS_PROVIDER,
                enable
            )
            LogUtils.i("GPS ${if (enable) "enabled" else "disabled"}")
        } catch (e: SecurityException) {
            LogUtils.e("No WRITE_SECURE_SETTINGS permission", e)
        }
    }

    /** 获取当前 GPS 状态 */
    fun isGpsEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
}
