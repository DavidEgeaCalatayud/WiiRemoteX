import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    jvmToolchain(17)

    sourceSets {
        commonMain {
            kotlin.srcDirs(
                "../core/model/src/main/kotlin",
                "../core/protocol/src/main/kotlin",
                "../core/session/src/main/kotlin",
                "src/commonMain/kotlin",
            )
            dependencies {
                implementation("org.jetbrains.kotlinx:atomicfu:0.33.0")
            }
        }

        commonTest {
            kotlin.srcDirs(
                "../core/protocol/src/test/kotlin",
                "../core/session/src/test/kotlin",
                "src/commonTest/kotlin",
            )
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "WiiRemoteShared"
            isStatic = true
        }
    }
}
