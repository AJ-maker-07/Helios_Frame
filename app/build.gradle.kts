import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Beta-release signing: reads ../keystore.properties if present (never committed).
// Copy keystore.properties.example -> keystore.properties and fill in your key.
// Without it, assembleRelease still runs but produces an unsigned APK.
val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

android {
    // OPEN DETAIL: change namespace/applicationId to your custom ID when you supply it.
    namespace = "com.helios.frame"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.helios.frame"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-beta1"
    }

    signingConfigs {
        create("release") {
            // Empty defaults: only used when keystore.properties exists (see below).
            storeFile = keystoreProps.getProperty("storeFile")?.let { file(it) }
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Beta: keep unshrunk/unobfuscated so a crash log maps to source.
            // Enable minify + shrinkResources only after the beta stress test passes.
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-image-engine"))
    implementation(project(":core-data"))
    implementation(project(":core-batch"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.workmanager)
    implementation(libs.coil.compose)
    implementation(libs.documentfile)
}
