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
}


android {
    namespace = "com.example.mygemma3n"
    compileSdk = 36

    androidResources {
        noCompress += "tflite"
        noCompress += "task"
        noCompress += "mbundle"

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

        // Maps API Key - will be replaced at build time
        manifestPlaceholders["MAPS_API_KEY"] = project.findProperty("MAPS_API_KEY") ?: "PLACEHOLDER_MAPS_API_KEY"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["logLevel"] = "DEBUG"
            buildConfigField("Boolean", "ENABLE_PERFORMANCE_MONITORING", "true")
        }
        release {
            isMinifyEnabled = false  // Disabled to preserve ALL functionality including Google Cloud Speech
            isShrinkResources = false
            buildConfigField("Boolean", "ENABLE_PERFORMANCE_MONITORING", "false")
            
            // Keep ABI filtering for size optimization (most compatible optimization)
            ndk {
                abiFilters += listOf("arm64-v8a") // Only include 64-bit ARM (most devices)
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-XXLanguage:+PropertyParamAnnotationDefaultTargetMode"
        )
    }
    
    kotlin {
        jvmToolchain(21)
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
    
    bundle {
        language {
            enableSplit = false  // Disable language splits to reduce overhead
        }
        density {
            enableSplit = false  // Keep all densities in base for offline AI app
        }
        abi {
            enableSplit = true   // Enable ABI splits to reduce size
        }
        
        // Additional bundle optimizations
        texture {
            enableSplit = false  // Keep textures in base for AI models
        }
    }


    packagingOptions {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/io.netty.versions.properties")
            excludes.add("META-INF/*.kotlin_module")
            excludes.add("mozilla/public-suffix-list.txt")
            
            // Additional size reduction exclusions
            excludes.add("META-INF/*.version")
            excludes.add("META-INF/maven/**")
            excludes.add("META-INF/proguard/**")
            excludes.add("**/*.proto")
            excludes.add("google/protobuf/**")
            excludes.add("kotlin/**")
            excludes.add("META-INF/services/**")
        }
    }


    // Asset folders for models
    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets", "src/main/ml")
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

// Image Loading
    implementation(libs.coil.compose)

// Navigation
    implementation(libs.androidx.hilt.navigation.compose)

// Google AI Edge LiteRT (Unified ML Stack)
    implementation(libs.litert.support)
    implementation(libs.litert.gpu)
// Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

// WorkManager
    implementation(libs.androidx.work.runtime.ktx)

// Firebase & UI Testing
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.androidx.ui.test.android)

// Hilt Dependency Injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.common)

// Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

// Firebase
    // --- Firebase (BoM 34+) ------------------------------
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.perf)    // <- no -ktx

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

    // DOCX Processing
    implementation(libs.poi.ooxml)

// DataStore
    implementation(libs.androidx.datastore.preferences)

// Splash Screen
    implementation(libs.androidx.core.splashscreen)

// Media (for audio processing)
    implementation(libs.androidx.media3.common.ktx)
    // Removed ExoPlayer - large media library, using Android MediaPlayer
    // implementation(libs.androidx.media3.exoplayer)

// Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)

// Debug Tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // Removed LeakCanary - reduces debug build size

// Analytics & Services
    implementation(libs.play.services.measurement.api)
    // Removed localagents.rag - not used and reduces bundle size
    implementation(libs.asset.delivery.ktx)

// TensorFlow Lite (Legacy - for compatibility)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.metadata)
    // Note: Removed duplicate GPU delegates as LiteRT handles GPU acceleration

// MediaPipe
    implementation(libs.tasks.genai)
    implementation(libs.tasks.text)

// Networking
    implementation(libs.okhttp)

// Hilt work manager
    implementation(libs.androidx.hilt.work)

    // PDF processing uses Android native PdfRenderer + ML Kit text recognition
    implementation(libs.text.recognition)

    //Lifecycle
    implementation("androidx.lifecycle:lifecycle-process:2.9.2")

    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // Local broadcast manager for service communication
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")




    // Google Cloud Speech - with aggressive dependency exclusions to prevent conflicts
    implementation(libs.google.cloud.speech) {
        exclude(group = "com.google.firebase", module = "protolite-well-known-types")
        exclude(group = "com.google.api.grpc", module = "proto-google-common-protos")
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "commons-logging", module = "commons-logging")
    }




}

// Enable Gemma 3n model optimizations
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
}

configurations.all {
    resolutionStrategy {
        force(
            "com.google.protobuf:protobuf-java:3.21.12",
            "com.google.api.grpc:proto-google-common-protos:2.29.0",
            "com.google.firebase:protolite-well-known-types:18.0.1",
            "androidx.test.espresso:espresso-core:3.6.1"
        )
    }
}