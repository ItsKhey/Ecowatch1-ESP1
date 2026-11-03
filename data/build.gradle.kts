plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.ecowatchpersonnel1.data"
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
    // Domain models and the shared utilities (session, preferences, server config).
    api(project(":core"))

    // Room: the on-device copy of every task this cleaner has been given, so the
    // history screen has something to show offline and immediately on open.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // LiveData returned by the DAOs and repositories.
    implementation(libs.lifecycle.livedata.ktx)

    // NotificationCompat / NotificationManagerCompat used by the notification repository.
    implementation(libs.androidx.core)
}
