plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.giack.teslawear"
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    defaultConfig {
        applicationId = "dev.giack.teslawear"
        minSdk = 33
        targetSdk = 36
        // CI passes the release tag's numbers; local builds keep these.
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // Public build: open source, nothing owned by Tesla. Personal build: the maintainer's own, with
    // Tesla-owned material in git-ignored src/personal (ADR-0002); declared only when present.
    flavorDimensions += "edition"
    productFlavors {
        create("public") {
            dimension = "edition"
            applicationId = "io.github.giack.wearfortesla"
        }
        if (file("src/personal").exists()) {
            create("personal") {
                dimension = "edition"
                applicationId = "dev.giack.teslawear"
            }
        }
    }

    // Release key from the environment (CI secrets, ticket 12); local builds fall back to the debug key.
    val releaseKeystore = System.getenv("WEAR_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("WEAR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WEAR_KEY_ALIAS")
                keyPassword = System.getenv("WEAR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // What gets installed on the watch: shrunk and compact (the debug APK is ~42 MB because of
        // incremental-build padding and kept failing over the watch's flaky Wi-Fi). Debuggable so
        // setup.sh push-watch can use run-as to hand Fleet credentials to the app.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    // Localisation (ticket 10): English default plus Italian. Kotlin-side literals are checked by
    // HardcodedStringsTest, since HardcodedText only sees XML.
    lint {
        error += listOf("HardcodedText", "MissingTranslation", "ExtraTranslation", "StringFormatMatches", "StringFormatInvalid")
        checkOnly += listOf("HardcodedText", "MissingTranslation", "ExtraTranslation", "StringFormatMatches", "StringFormatInvalid",
            "StringFormatCount", "PluralsCandidate", "MissingQuantity", "UnusedQuantity")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.wear.input)
    implementation(libs.wear.phone.interactions)
    implementation(libs.wear.remote.interactions)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.concurrent.futures.ktx)
    implementation(libs.wear.complications.data.source)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.coroutines.test)
}
