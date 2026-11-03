plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

// Client SDK version (git tag on release; JitPack coordinates com.github.taehunkim.ondolsaem:sdk:<tag>).
// group/name must match the dependency Nunsori declares so a Gradle composite build substitutes this project.
group = "com.github.taehunkim.ondolsaem"
version = "0.2.0"

android {
    namespace = "io.github.taehunkim.ondolsaem.sdk"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"$version\"")
    }
    buildFeatures { aidl = true; buildConfig = true }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "sdk"
            afterEvaluate { from(components["release"]) }
        }
    }
}
