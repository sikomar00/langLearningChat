plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.chatlearn.baselineprofile"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 연결된 에뮬레이터·기기에서 만든다(./gradlew :app:generateReleaseBaselineProfile).
baselineProfile { useConnectedDevices = true }

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.1")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}
