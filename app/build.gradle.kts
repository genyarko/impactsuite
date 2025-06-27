plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.impactsuite"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.impactsuite.gemma3n"
        minSdk = 26
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
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
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
                srcDirs("src/main/assets", "src/main/ml")
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.hilt.common)
    implementation(libs.play.services.measurement.api)
    implementation(libs.localagents.rag)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.navigation.compose.jvmstubs)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.edge.litert)          // :contentReference[oaicite:0]{index=0}
    implementation(libs.edge.litert.gpu)      // :contentReference[oaicite:1]{index=1}
    implementation(libs.edge.litert.nnapi)    // :contentReference[oaicite:2]{index=2}
    implementation(libs.androidx.datastore.preferences) // :contentReference[oaicite:3]{index=3}
    implementation(libs.androidx.work.runtime.ktx)      // :contentReference[oaicite:4]{index=4}
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.accompanist.permissions)
    implementation(libs.supabase.kt)
    implementation(libs.hilt.android)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.timber)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.room.compiler)

    // Optional instrumentation-test helpers
    androidTestImplementation(libs.hilt.android.testing)
}