plugins { id("org.jetbrains.kotlin.multiplatform") }

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation("org.jetbrains.kotlinx:atomicfu:0.33.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
