plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}

// baseLib 模块通过 rootProject.ext.lib / rootProject.ext.packageName 访问
// 注：minSdk 提到 26 —— 离线翻译 SDK（ONNX Runtime + sherpa-onnx）要求 26+。
// 实际交付设备 PDS01 为 API 27，不受影响。
extra["lib"] = mapOf(
    "compileSdkVersion" to 35,
    "minSdkVersion" to 26,
    "targetSdkVersion" to 35
)
extra["packageName"] = mapOf(
    "baseLib" to "com.quniu.baselib"
)

// 版本号变量（baseLib 的 build.gradle 通过 $varName 引用）
extra["autosizeVersion"] = "1.2.1"
extra["ktCoreVersion"] = "1.12.0"
extra["appcompatVersion"] = "1.6.1"
extra["materialVersion"] = "1.11.0"
extra["retrofitVersion"] = "2.9.0"
extra["loggingVersion"] = "4.12.0"
extra["gsonVersion"] = "2.10.1"
extra["coroutinesVersion"] = "1.7.3"
extra["livedataVersion"] = "2.7.0"
extra["viewmodelVersion"] = "2.7.0"
extra["eventBusVersion"] = "3.3.1"
extra["dbVersion"] = "2.6.1"
extra["mmkvVersion"] = "1.3.4"
extra["libpagVersion"] = "4.4.14"
