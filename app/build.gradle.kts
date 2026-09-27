plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "io.github.hyh1627723044.shortvideokws"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.hyh1627723044.shortvideokws"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "onnx" }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // android.jar's org.json is a stub that throws in JVM unit tests.
    testImplementation("org.json:json:20240303")
}
