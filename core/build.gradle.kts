// Pure Kotlin: everything that can be tested without a phone, TV or Android SDK.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation("junit:junit:4.13.2")
}
