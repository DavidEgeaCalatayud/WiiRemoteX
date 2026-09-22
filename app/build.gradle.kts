plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.davidegeacalatayud.wiiremotex"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.davidegeacalatayud.wiiremotex"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-poc"
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":feature:controller"))
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
