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
        // Capri Android's own numbering (MAJOR.MINOR.PATCH), not capri-desktop's;
        // versionCode = MAJOR*10000 + MINOR*100 + PATCH, always above the old 5xx builds
        versionCode = 10602
        versionName = "1.6.2"
        // her service is only reachable from outside (adb, CI's screenshots) in debug builds
        manifestPlaceholders["serviceExported"] = "false"
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
        debug {
            manifestPlaceholders["serviceExported"] = "true"
        }
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

// CHANGELOG.md (the repo's root) inside the APK too: the app's What's new tab reads it
abstract class CopyChangelog : DefaultTask() {
    @get:InputFile abstract val source: RegularFileProperty
    @get:OutputDirectory abstract val out: DirectoryProperty

    @TaskAction
    fun run() {
        source.get().asFile.copyTo(out.get().file("CHANGELOG.md").asFile, overwrite = true)
    }
}

val copyChangelog = tasks.register<CopyChangelog>("copyChangelog") {
    source.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
}
androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(copyChangelog, CopyChangelog::out) }
}
