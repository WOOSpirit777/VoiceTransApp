import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {

    namespace = "com.aibabel.voicetrans"
    compileSdk = 35

    signingConfigs {
        create("platform") {
            storeFile = file("../pds01.keystore")
            storePassword = "android"
            keyAlias = "platform"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.aibabel.voicetrans"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "v1.0.0"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("platform")
            buildConfigField("String", "TAG", "\"translate\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("platform")
            buildConfigField("String", "TAG", "\"translate\"")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        dataBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
            val currentDate = dateFormat.format(Date())
            output.outputFileName = "aibabel_translate_${variant.name}_${variant.versionName}_${currentDate}_pds.apk".replace(" ", "_")
        }
    }
}

dependencies {
    val dbVersion = "2.6.1"

    implementation(project(":baseLib"))
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.room:room-runtime:$dbVersion")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    kapt("androidx.room:room-compiler:$dbVersion")
    implementation("androidx.room:room-ktx:$dbVersion")
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // ===== 离线翻译 SDK（本地 AAR，独立分发）=====
    // 注意：settings.gradle.kts 是 FAIL_ON_PROJECT_REPOS，不能在这里加 repositories{}
    // 或 flatDir，因此用 files() 直接引用 AAR 文件。
    implementation(files("libs/asr_onnx_sdk-release-v1.6.0.aar"))

    // AAR 不传递依赖的 POM，以下为 SDK 运行期必需，必须由宿主补齐。
    // 缺任何一个都会在初始化阶段出现 NoClassDefFoundError。
    implementation("com.squareup.okio:okio:3.6.0")
    // okdownload：SDK 的断点续传下载器（RemoteDownloadInstaller）依赖。
    // 只需核心包：SDK 只用到 DownloadTask / DownloadListener3 / ResumeFailedCause；
    // sqlite（断点存储）与 okhttp（自定义网络栈）两个插件并未发布到 Maven Central，
    // 且本 SDK 不依赖它们，故不引入。
    implementation("com.liulishuo.okdownload:okdownload:1.0.7")
}


