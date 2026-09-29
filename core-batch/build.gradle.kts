plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.helios.frame.batch"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core-image-engine"))
    api(project(":core-data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.workmanager)
    testImplementation(libs.junit)
}
