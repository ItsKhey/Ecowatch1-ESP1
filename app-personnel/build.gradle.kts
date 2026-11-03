plugins {
    alias(libs.plugins.android.application)
    // Declared with `apply false` so the plugin is on this module's build
    // classpath without running. It is applied near the bottom of this file only
    // when a google-services.json that actually targets this app id exists.
    alias(libs.plugins.google.services) apply false
}

android {
    namespace = "com.example.ecowatchpersonnel1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.ecowatchpersonnel1"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

/**
 * Firebase Cloud Messaging needs a google-services.json that contains a client
 * for THIS applicationId.
 *
 * A checked-in project can carry a file for the resident app or the admin app
 * (the two sibling apps in this repository), which would make the Google
 * Services plugin fail because it cannot find a matching client. Rather than
 * fabricate credentials, the plugin is applied only when the real configuration
 * for this package is present. Until then the REST API, the in-app notification
 * list and its polling all work; only push delivery is unavailable.
 */
val googleServicesFile = file("google-services.json")
val hasPersonnelFirebaseClient =
    googleServicesFile.exists() &&
        googleServicesFile.readText().contains("\"com.example.ecowatchpersonnel1\"")

if (hasPersonnelFirebaseClient) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle(
        "app-personnel/google-services.json has no client for " +
            "com.example.ecowatchpersonnel1; building without Firebase Cloud " +
            "Messaging. Add the real file with this package registered in the " +
            "Firebase console to enable push."
    )
}

dependencies {
    // Layered modules: domain models/utilities, persistence + networking, and
    // Firebase messaging. The app module itself only holds the UI.
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":notifications"))

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Biometric prompt behind the Security & Privacy fingerprint toggle.
    implementation(libs.biometric)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    implementation(libs.play.services.location)
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    implementation(libs.swiperefreshlayout)

    testImplementation(libs.junit)
    // Runs the Room migration statements against a real SQLite engine. A migration
    // that creates a table or index the @Entity does not declare only fails at
    // database-open time on a device, which is exactly when it is most expensive.
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
