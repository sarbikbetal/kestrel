import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.sarbikbetal.kestrel"
    compileSdk = 37
    compileSdkMinor = 0
    buildToolsVersion = "37.0.0"

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.sarbikbetal.kestrel"
        minSdk = 26
        targetSdk = 37
        // CI passes -PVERSION_NAME=<tag without v>; local builds use the latest git tag.
        val name = resolveVersionName()
        versionName = name
        versionCode = versionCodeFrom(name)
    }

    androidResources {
        localeFilters += "en"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release keystore when keystore.properties exists; otherwise debug
            // so local/CI release APKs remain installable for testing.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.osmdroid.android)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.datastore.preferences)
}

/** Prefer -PVERSION_NAME (release workflow), else latest `v*` git tag, else 0.0.0. */
fun Project.resolveVersionName(): String {
    (findProperty("VERSION_NAME") as String?)
        ?.trim()
        ?.removePrefix("v")
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }

    return runCatching {
        ProcessBuilder("git", "describe", "--tags", "--match", "v*", "--abbrev=0")
            .directory(rootProject.projectDir)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .inputStream.bufferedReader().readText().trim()
            .removePrefix("v")
            .takeIf { it.isNotEmpty() }
    }.getOrNull() ?: "0.0.0"
}

/** Monotonic Play-store-safe code from semver: major*1e6 + minor*1e3 + patch. */
fun versionCodeFrom(versionName: String): Int {
    val parts = versionName.substringBefore('-').split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 1_000_000 + minor * 1_000 + patch
}
