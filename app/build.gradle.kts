plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystorePath = System.getenv("WIIREMOTEX_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("WIIREMOTEX_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("WIIREMOTEX_KEY_ALIAS")
val releaseKeyPassword = System.getenv("WIIREMOTEX_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.davidegeacalatayud.wiiremotex"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.davidegeacalatayud.wiiremotex"
        minSdk = 28
        targetSdk = 36
        versionCode = 7
        versionName = "0.7.0-alpha"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(project(":core:protocol"))
    implementation(project(":core:trace"))
    implementation(project(":transports:android-hid"))
    implementation(project(":transports:esp32-ble"))
    implementation(project(":platform:sensors"))
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
