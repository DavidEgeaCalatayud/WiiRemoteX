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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
