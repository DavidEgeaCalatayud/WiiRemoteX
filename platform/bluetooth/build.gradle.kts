plugins { id("com.android.library") }
android {
    namespace = "io.github.davidegeacalatayud.wiiremotex.platform.bluetooth"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
}
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:session"))
}
