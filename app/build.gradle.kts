plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// The checked-in project builds without Firebase credentials. Production CI or
// a developer workstation supplies app/google-services.json; the plugin is only
// applied when that real configuration file exists.

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
 * The environment variable is what makes the Run button in Android Studio work
 * without typing anything: set it once in the shell, and every build from the
 * IDE picks it up.
 *
 *   gradlew installDebug -PecowatchApiBaseUrl=http://192.168.1.20:5000/api/
 *
 * The machine's address is printed by `ipconfig` and the Windows firewall has to
 * allow inbound TCP 5000.
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

/**
 * How a new resident account proves it owns the email address.
 *
 *   OTP        (default) the backend emails a 4-digit code, the resident types
 *              it in, and the account is created. No Firebase involvement at
 *              any point, which is what a local-server setup needs.
 *   EMAIL_LINK the backend stages a Firebase identity and the Firebase SDK
 *              emails a verification link. Kept working as a fallback.
 *
 * The Firebase path is selected per build rather than deleted, so switching back
 * is a build flag and not a code change:
 *
 *   gradlew installDebug -PecowatchRegistrationMethod=EMAIL_LINK
 */
val ecowatchRegistrationMethod: String =
    (project.findProperty("ecowatchRegistrationMethod") as String?)
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.isNotEmpty() }
        ?: "OTP"

if (ecowatchRegistrationMethod !in setOf("OTP", "EMAIL_LINK")) {
    throw GradleException(
        "ecowatchRegistrationMethod must be OTP or EMAIL_LINK, got \"$ecowatchRegistrationMethod\"."
    )
}

android {
    namespace = "com.example.myecowatch1"
    compileSdk {
        version = release(37)
    }

    // BuildConfig is what carries the API target above into the app.
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.myecowatch1"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "API_BASE_URL", "\"$ecowatchApiBaseUrlNormalized\"")
        buildConfigField("String", "SERVER_ORIGIN", "\"$ecowatchServerOrigin\"")
        buildConfigField("String", "REGISTRATION_METHOD", "\"$ecowatchRegistrationMethod\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (!file("google-services.json").exists()) {
                throw GradleException(
                    "Release builds require app/google-services.json for Firebase Cloud Messaging."
                )
            }
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

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)

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

    implementation(libs.play.services.location)
    implementation(libs.swiperefreshlayout)
    implementation(libs.ucrop)
    implementation(libs.biometric)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Email verification and password reset only.
    //
    // The app never signs in with Firebase. It never calls signInWith*, never
    // reads an ID token and never treats a Firebase session as a login, so
    // adding this dependency cannot make Firebase an authentication authority.
    // It is used for exactly one optional call: applyActionCode, which retires a
    // reset code once the backend has already applied the new password.
    implementation("com.google.firebase:firebase-auth")
}
