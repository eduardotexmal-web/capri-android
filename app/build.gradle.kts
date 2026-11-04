plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.emmanuelquintana.capri"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.emmanuelquintana.capri"
        minSdk = 26
        targetSdk = 35
        // follows capri-desktop's version (assets/android/upstream.json), plus the port's own build
        versionCode = 503
        versionName = "0.5.1-android.3"
    }

    buildFeatures { buildConfig = true }

    // CI signs with a key from secrets when there is one (updates then install over each other);
    // otherwise with the debug key, which is fine to sideload
    val releaseKey = System.getenv("CAPRI_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = releaseKey
                storePassword = System.getenv("CAPRI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CAPRI_KEY_ALIAS")
                keyPassword = System.getenv("CAPRI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKey != null) "release" else "debug")
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
        // the report is kept as a CI artifact; a warning doesn't stop the APK
        abortOnError = false
    }

    androidResources {
        // her pictures are already compressed (png, webp): stored as is, read straight from the APK
        noCompress += listOf("png", "webp", "woff2")
    }
}
