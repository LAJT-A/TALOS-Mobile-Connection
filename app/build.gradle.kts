plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lajt.talos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lajt.talos"
        minSdk = 28
        targetSdk = 34
        // Cada compilacion de GitHub sube el numero, para que la app se actualice encima de la anterior.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "1")
    }

    signingConfigs {
        // Firma fija: sin ella cada compilacion tendria otra firma y habria que desinstalar antes de actualizar.
        create("talos") {
            storeFile = file("talos.keystore")
            storePassword = "talos123"
            keyAlias = "talos"
            keyPassword = "talos123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("talos")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
    }
}
