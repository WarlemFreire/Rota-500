plugins {
    id("com.android.application")
}

android {
    namespace = "br.com.soswf.rota500"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.soswf.rota500"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-mvp"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
