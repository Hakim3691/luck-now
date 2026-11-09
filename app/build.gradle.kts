import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

fun secret(propertyName: String, envName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = secret("storeFile", "LUCKNOW_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }
val releaseStorePassword = secret("storePassword", "LUCKNOW_KEYSTORE_PASSWORD")
val releaseKeyAlias = secret("keyAlias", "LUCKNOW_KEY_ALIAS")
val releaseKeyPassword = secret("keyPassword", "LUCKNOW_KEY_PASSWORD")

// Production APKs target 64-bit ARM only and contain exactly one ABI, so no
// multi-APK archive is ever produced. CI renames the single output per ABI
// (app-arm64-v8a-debug.apk / app-arm64-v8a-release.apk) and an emulator job
// rebuilds with -PabiFilters=x86_64. AGP forbids combining ndk.abiFilters with
// `splits`, and Chaquopy requires ndk.abiFilters, so splits are not used.
val targetAbis = (findProperty("abiFilters") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: listOf("arm64-v8a")

// Native llama.cpp inference is compiled only when the sources are vendored
// (scripts/fetch-llama-cpp.sh) and the flag is on. Without it the app ships the
// deterministic extractor and reports the LLM as unavailable.
val llamaSourcesPresent = rootProject.file("third_party/llama.cpp/CMakeLists.txt").exists()
// Native inference is compiled when llama.cpp is vendored. Pass
// -Plucknow.withLlama=false to skip it (no NDK required) on build machines
// that only need the deterministic parser, such as the BDD emulator job.
val withLlama = providers.gradleProperty("lucknow.withLlama").orNull?.toBoolean()
    ?: llamaSourcesPresent

android {
    namespace = "com.lucknow"
    compileSdk = 35
    if (withLlama && llamaSourcesPresent) {
        ndkVersion = "27.2.12479018"
    }

    defaultConfig {
        applicationId = "com.lucknow"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "io.cucumber.android.runner.CucumberAndroidJUnitRunner"

        ndk {
            abiFilters.addAll(targetAbis)
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    if (withLlama && llamaSourcesPresent) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
        defaultConfig {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DANDROID_STL=c++_shared")
                    cppFlags += listOf("-O3", "-fexceptions", "-frtti")
                }
            }
        }
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

chaquopy {
    defaultConfig {
        // Python 3.10 is the Chaquopy 17 default and the only interpreter with
        // official arm64 wheels for the full scipy + statsmodels stack.
        version = "3.10"
        pip {
            install("numpy==1.23.3")
            install("scipy==1.8.1")
            install("pandas==2.1.3")
            install("statsmodels==0.14.5")
        }
        // Bytecode pre-compilation would require a host Python 3.10 interpreter.
        // Wheels already ship .pyc files; sources are compiled on first import.
        pyc {
            src = false
            pip = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    // Real org.json implementation for JVM tests (the android.jar stub throws).
    testImplementation(libs.org.json)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.cucumber.android)
    androidTestImplementation(libs.cucumber.junit)
    androidTestImplementation(libs.cucumber.picocontainer)
}
