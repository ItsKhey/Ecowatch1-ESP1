plugins {
    alias(libs.plugins.android.library)
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
    namespace = "com.example.ecowatchpersonnel1.core"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 24

        buildConfigField("String", "API_BASE_URL", "\"$ecowatchApiBaseUrlNormalized\"")
        buildConfigField("String", "SERVER_ORIGIN", "\"$ecowatchServerOrigin\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Models carry Gson's @SerializedName annotations.
    implementation(libs.gson)

    // AndroidX annotations and the small Android helpers used by the utilities.
    implementation(libs.androidx.core)
}
