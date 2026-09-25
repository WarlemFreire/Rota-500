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
        versionCode = 2
        versionName = "0.2.0"
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

dependencies {
    // Modelo latino incluído no APK: o OCR funciona sem internet e sem enviar imagens.
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
