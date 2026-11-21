plugins {
    id("com.android.application")
}

// Versão visível da próxima estável. O CI acrescenta "-teste.N" nas versões de teste (Q26).
val baseVersionName = "0.9.0"
// versionCode = número da execução do build no GitHub, sempre crescente (Q26).
val rotaVersionCode = providers.gradleProperty("rotaVersionCode").orNull?.toInt() ?: 9
val rotaVersionName = providers.gradleProperty("rotaVersionName").orNull ?: baseVersionName
// Senha da chave fixa (Q20, Q30). Só existe no CI, como Secret do GitHub.
val keystorePassword: String? = System.getenv("ROTA500_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }

android {
    namespace = "br.com.soswf.rota500"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.soswf.rota500"
        minSdk = 30
        targetSdk = 36
        versionCode = rotaVersionCode
        versionName = rotaVersionName
        ndk {
            // O MVP será instalado no Moto G56 5G, que usa ARM de 64 bits.
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (keystorePassword != null) {
            create("rota500") {
                storeFile = rootProject.file("signing/rota500.p12")
                storeType = "pkcs12"
                storePassword = keystorePassword
                keyAlias = "rota500"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePassword != null) signingConfig = signingConfigs.getByName("rota500")
        }
    }

    lint {
        // O build do celular não pode falhar por avisos de lint; a verificação roda à parte.
        checkReleaseBuilds = false
        abortOnError = false
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
