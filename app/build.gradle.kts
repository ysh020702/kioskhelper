plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)           // ← KSP
    alias(libs.plugins.hilt)          // ← Hilt
}

android {
    namespace = "com.example.kioskhelper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kioskhelper"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}
hilt{
    enableAggregatingTask = false
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // CameraX
    implementation("androidx.camera:camera-core:1.3.3")
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")
    // 🔥 TensorFlow Lite Task Vision (정확한 라인: 0.4.x)
    // TFLite Task Vision (ObjectDetector)
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    // (선택) GPU delegate
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    // (선택) 기본 TFLite & GPU. 2.12~2.14 아무거나 호환됩니다.
    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
// 선택) Support (메타데이터/서명 도우미)
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // ML Kit Text Recognition (Korean)
    implementation("com.google.mlkit:text-recognition-korean:16.0.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // AndroidX
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    implementation(libs.hilt.core)
    ksp(libs.hilt.compiler)                // ← KSP
    implementation(libs.hilt.nav.compose)  // hiltViewModel()

}