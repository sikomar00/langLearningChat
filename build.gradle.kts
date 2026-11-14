plugins {
    id("com.android.application") version "9.5.1" apply false
    id("com.android.test") version "9.5.1" apply false
    id("androidx.baselineprofile") version "1.5.0" apply false
    // AGP 9는 Kotlin을 내장한다. 여기서는 Kotlin 버전만 고정하고 모듈에는 적용하지 않는다.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.14" apply false
}
