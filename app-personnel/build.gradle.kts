plugins {
    alias(libs.plugins.android.application)
    // Declared with `apply false` so the plugin is on this module's build
    // classpath without running. It is applied near the bottom of this file only
    // when a google-services.json that actually targets this app id exists.
    alias(libs.plugins.google.services) apply false
}

/**
 * Where the app looks for the EcoWatch API.
 *
 * The target is compiled in by Gradle rather than hardcoded in Java, so the same
 * source tree builds against a local server or a hosted deployment without a code
 * edit.
 *
 * The default is the Android emulator's alias for the host machine's loopback,
 * which is exactly what `npm start` in the backend project serves. Nothing else
 * has to be configured to run the app in the emulator.
 *
 * A physical phone cannot use 10.0.2.2, so point a device build at this
 * machine's address on the same WiFi instead. The address is resolved in this
 * order, first match wins:
 *
 *   1. the -PecowatchApiBaseUrl flag
 *   2. the ECOWATCH_API_BASE_URL environment variable
 *   3. the emulator default above
 *
 *   gradlew installDebug -PecowatchApiBaseUrl=http://192.168.1.20:5000/api/
 *
 * The backend must also be reachable over the network: check `ipconfig` for the
 * address and allow inbound TCP 5000 through the Windows firewall.
 */
val ecowatchApiBaseUrl: String =
    (project.findProperty("ecowatchApiBaseUrl") as String?)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("ECOWATCH_API_BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }
        ?: "http://10.0.2.2:5000/api/"

/** Retrofit requires a base URL with a trailing slash, so normalise it once here. */
val ecowatchApiBaseUrlNormalized: String =
    if (ecowatchApiBaseUrl.endsWith("/")) ecowatchApiBaseUrl else "$ecowatchApiBaseUrl/"

/**
 * The same server without the `/api` path, used to expand the relative image
 * paths the database stores (`/uploads/...`) into something Glide can load.
 */
val ecowatchServerOrigin: String = run {
    val withoutTrailingSlash = ecowatchApiBaseUrlNormalized.trimEnd('/')
    val withoutApiPath =
        if (withoutTrailingSlash.endsWith("/api")) withoutTrailingSlash.removeSuffix("/api")
        else withoutTrailingSlash
    "$withoutApiPath/"
}

android {
    namespace = "com.example.ecowatchpersonnel1"
    compileSdk {
        version = release(37)
    }

    // BuildConfig is what carries the API target above into the app.
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.ecowatchpersonnel1"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "API_BASE_URL", "\"$ecowatchApiBaseUrlNormalized\"")
        buildConfigField("String", "SERVER_ORIGIN", "\"$ecowatchServerOrigin\"")

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
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Room: the on-device copy of every task this cleaner has been given, so the
    // history screen has something to show offline and immediately on open.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)

    // Biometric prompt behind the Security & Privacy fingerprint toggle.
    implementation(libs.biometric)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    implementation(libs.swiperefreshlayout)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    // Runs the Room migration statements against a real SQLite engine. A migration
    // that creates a table or index the @Entity does not declare only fails at
    // database-open time on a device, which is exactly when it is most expensive.
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
}
