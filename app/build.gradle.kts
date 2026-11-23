plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.romanticreader.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.romanticreader.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("RELEASE_VERSION_CODE")?.toIntOrNull() ?: 3)
        versionName = System.getenv("RELEASE_VERSION_NAME") ?: "0.3.0"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("RELEASE_KEYSTORE_FILE")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (System.getenv("RELEASE_KEYSTORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.mozilla:rhino:1.7.15")
    implementation("androidx.core:core:1.15.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
