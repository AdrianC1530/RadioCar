plugins {
    id("com.android.application")
}

android {
    namespace = "com.radiocar.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.radiocar.launcher"
        // La Topway T3L corre Android 8.1 (API 27) aunque diga "10".
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Uso personal: se firma con la clave debug para poder instalarlo directo por ADB.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
