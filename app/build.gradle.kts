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
        minSdk = 24
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
}
