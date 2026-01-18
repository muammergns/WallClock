plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.gns.wallclock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gns.wallclock"
        minSdk = 14
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }

}

dependencies {
    //versiyonları güncellemeyin. minsdkVersion 14 olarak ayarlayabilmek için kütüphane sürümleri bu olmalı
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.adapter.rxjava2)

    implementation(libs.rxjava)
    implementation(libs.rxandroid)

    implementation(libs.play.services.basement)
    implementation(libs.play.services.auth)


    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}