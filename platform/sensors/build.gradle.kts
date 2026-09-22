plugins { id("com.android.library") }

android {
    namespace = "io.github.davidegeacalatayud.wiiremotex.platform.sensors"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    implementation(project(":core:model"))
}
