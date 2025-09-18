plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.mywifiscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mywifiscanner"
        minSdk = 26
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.photoview)       // PhotoView 依赖
    implementation(libs.activity.ktx)    // Activity 扩展依赖
    implementation(libs.gson)            // Gson 依赖
}