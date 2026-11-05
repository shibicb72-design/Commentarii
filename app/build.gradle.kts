plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.shibi.commentarii"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shibi.notetodo"
        minSdk = 29
        targetSdk = 36
        // Fixed; bump by hand for each Play upload.
        versionCode = 3
        versionName = "1.0.0"
    }

    // One APK per ABI, same artifact names as Theatrum CI: arm64 for the phones, x86_64 for emulators.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    // Debug-only, NOT secret, generated for this repo only, never used for release.
    // Committed so every CI APK has the same signature and updates in place.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Release signing with the Play upload key, only when all four NOTETODO_UPLOAD_* Gradle
    // properties are set and non-blank (CI secrets via ORG_GRADLE_PROJECT_*, -P, or
    // ~/.gradle/gradle.properties; never committed). Otherwise release stays unsigned.
    fun uploadProperty(name: String) = providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
    val uploadStoreFile = uploadProperty("NOTETODO_UPLOAD_STORE_FILE")
    val uploadKeyAlias = uploadProperty("NOTETODO_UPLOAD_KEY_ALIAS")
    val uploadStorePassword = uploadProperty("NOTETODO_UPLOAD_STORE_PASSWORD")
    val uploadKeyPassword = uploadProperty("NOTETODO_UPLOAD_KEY_PASSWORD")
    if (uploadStoreFile != null && uploadKeyAlias != null &&
        uploadStorePassword != null && uploadKeyPassword != null
    ) {
        signingConfigs.create("release") {
            storeFile = file(uploadStoreFile)
            storePassword = uploadStorePassword
            keyAlias = uploadKeyAlias
            keyPassword = uploadKeyPassword
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Only present when the upload key properties are set (see above).
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}