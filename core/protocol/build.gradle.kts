plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:model"))
    implementation("org.jetbrains.kotlinx:atomicfu:0.33.0")
    testImplementation(kotlin("test-junit"))
}
