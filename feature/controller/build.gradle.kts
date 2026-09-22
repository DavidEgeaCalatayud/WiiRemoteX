plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.davidegeacalatayud.wiiremotex.feature.controller"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":core:model"))
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
