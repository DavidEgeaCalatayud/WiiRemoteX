plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.davidegeacalatayud.wiiremotex"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.davidegeacalatayud.wiiremotex"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0-alpha"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(project(":core:protocol"))
    implementation(project(":platform:bluetooth"))
    implementation(project(":platform:sensors"))
    implementation(project(":transports:esp32-ble"))
    implementation(project(":feature:controller"))

    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
