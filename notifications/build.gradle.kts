plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.ecowatchpersonnel1.notifications"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))

    // Firebase Cloud Messaging delivery and the WorkManager retry that rebinds
    // this device's token after a cold start.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation(libs.work.runtime)
    implementation(libs.androidx.core)
}
