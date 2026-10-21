plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.edith.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.edith.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "0.3.0"
    }
    // Chave de assinatura fixa do projeto (pública, não é segredo).
    // Sem isso cada build do Actions sai com uma assinatura diferente e o
    // Android se recusa a instalar a atualização por cima da versão antiga.
    signingConfigs {
        create("edith") {
            storeFile = file("../keystore/edith.jks")
            storePassword = "edithdebug"
            keyAlias = "edith"
            keyPassword = "edithdebug"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("edith")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("edith")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
