plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.kapt")
    id("com.android.asset-pack")
}


android {
    namespace = "com.example.mygemma3n"
    compileSdk = 36
    assetPacks += listOf(":gemma3n_assetpack")

    androidResources {        // AGP 8.0+
        noCompress += "tflite"
    }


    defaultConfig {
        applicationId = "com.example.mygemma3n"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Enable model binding
        buildConfigField("Boolean", "ENABLE_MODEL_BINDING", "true")
        buildConfigField("Integer", "KV_CACHE_SIZE", "4096")
        buildConfigField("Integer", "MAX_BATCH_SIZE", "1")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("Boolean", "ENABLE_PERFORMANCE_MONITORING", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_PERFORMANCE_MONITORING", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
        freeCompilerArgs = listOf("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
        mlModelBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            // Keep all native libraries for AI Edge
            pickFirsts += "**/*.so"
        }
    }

    // Asset folders for models
    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets", "src/main/ml", "src\\main\\assets", "src\\main\\assets",
                    "src\\main\\assets",
                    "src\\main\\assets"
                )
            }
        }
    }
}

dependencies {
// Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
// Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

// Navigation
    implementation(libs.androidx.hilt.navigation.compose)

// Google AI Edge LiteRT (Gemma 3n)
    implementation(libs.litert.support)
// Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.litert.gpu)
    ksp(libs.androidx.room.compiler)

// WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.ktx.v261)

// Hilt Dependency Injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.common)

// Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

// Firebase
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.perf.ktx)

// CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Gemini API - THIS IS THE KEY ADDITION
    implementation(libs.generativeai)

// Permissions
    implementation(libs.accompanist.permissions)

// Location Services
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

// Logging
    implementation(libs.timber)

// JSON Parsing
    implementation(libs.gson)

// DataStore
    implementation(libs.androidx.datastore.preferences)

// Splash Screen
    implementation(libs.androidx.core.splashscreen)

// Media (for audio processing)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.media3.exoplayer)

//    implementation(libs.litert)
//    implementation(libs.litert.gpu)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.play.services.measurement.api)
    implementation(libs.localagents.rag)

    // Tensorflow Lite
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.select.tf.ops)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.gpu.api)
    implementation(libs.tensorflow.lite.metadata)


    // MediaPipe for .task models (if you want to use .task files)
    implementation(libs.tasks.genai)

    implementation(libs.tasks.text)
// For downloading models
    implementation(libs.okhttp)

    // Hilt work manager
    implementation(libs.androidx.hilt.work)
    // Optional instrumentation-test helpers
    androidTestImplementation(libs.hilt.android.testing)

    // Leak Detection (debug only)
    debugImplementation(libs.leakcanary.android)

    implementation(libs.asset.delivery.ktx)

    implementation(libs.androidx.datastore.preferences.v110)



}

// Enable Gemma 3n model optimizations
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
}