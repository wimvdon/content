import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// App identity: the ONE place to change the package / application ID. Once the app is
// released it must never change again (phones would treat it as a different app). It changed
// once, from be.wimvandonck.reader to be.wimvandonck.content, at the owner's request (see
// docs/decisions.md): that made Content a new app next to the old one.
// ---------------------------------------------------------------------------
val appId = "be.wimvandonck.content"

// ---------------------------------------------------------------------------
// Versioning
//  - Release builds: the release workflow passes -PappVersionName=X.Y.Z and
//    -PappVersionCode=N (N = major*10000 + minor*100 + patch).
//  - Every other build: version comes from `git describe --tags --long`, e.g.
//    1.2.0-3-gabc1234, so a dev build never looks like a release.
//    Falls back to 0.0.0-dev when there are no tags or no git.
// ---------------------------------------------------------------------------
val releaseVersionName: String? = providers.gradleProperty("appVersionName").orNull
val releaseVersionCode: String? = providers.gradleProperty("appVersionCode").orNull

fun gitDescribeVersion(): String {
    val fallback = "0.0.0-dev"
    return try {
        val result = providers.exec {
            commandLine("git", "describe", "--tags", "--long", "--dirty", "--match", "v[0-9]*")
            isIgnoreExitValue = true
        }
        if (result.result.get().exitValue != 0) {
            fallback
        } else {
            result.standardOutput.asText.get().trim().removePrefix("v").ifEmpty { fallback }
        }
    } catch (e: Exception) {
        // git is not installed or this is not a git checkout (e.g. a downloaded source zip).
        fallback
    }
}

data class AppVersion(val name: String, val code: Int)

val appVersion: AppVersion = if (releaseVersionName != null || releaseVersionCode != null) {
    require(releaseVersionName != null && releaseVersionCode != null) {
        "Pass both -PappVersionName and -PappVersionCode, or neither."
    }
    require(Regex("""\d+\.\d+\.\d+""").matches(releaseVersionName)) {
        "appVersionName must look like X.Y.Z, got '$releaseVersionName'."
    }
    val code = releaseVersionCode.toIntOrNull()?.takeIf { it > 0 }
        ?: error("appVersionCode must be a positive number, got '$releaseVersionCode'.")
    AppVersion(releaseVersionName, code)
} else {
    AppVersion(gitDescribeVersion(), 1)
}

// ---------------------------------------------------------------------------
// Release signing: only when keystore.properties exists. The release workflow
// writes it at build time and deletes it afterwards. Never commit it.
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

// ---------------------------------------------------------------------------
// Notifications (#235): the app's Firebase settings, read from app/google-services.json when it is
// there. CI and the release workflow write that file from the GOOGLE_SERVICES_JSON secret; it is
// never committed (it holds an API key). Without it the build has empty settings and the app offers
// no notifications. No google-services plugin: the app starts Firebase with these itself.
// ---------------------------------------------------------------------------
fun firebaseSettings(packageName: String): Map<String, String> {
    val file = file("google-services.json")
    if (!file.exists()) return emptyMap()
    val json = groovy.json.JsonSlurper().parse(file) as Map<*, *>
    val project = json["project_info"] as? Map<*, *> ?: return emptyMap()
    val client =
        (json["client"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull { c ->
            ((c["client_info"] as? Map<*, *>)?.get("android_client_info") as? Map<*, *>)?.get("package_name") == packageName
        } ?: return emptyMap()
    val apiKey = ((client["api_key"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key")
    return mapOf(
        "FIREBASE_APP_ID" to (client["client_info"] as Map<*, *>)["mobilesdk_app_id"],
        "FIREBASE_API_KEY" to apiKey,
        "FIREBASE_PROJECT_ID" to project["project_id"],
        "FIREBASE_SENDER_ID" to project["project_number"],
    ).mapValues { it.value?.toString().orEmpty() }
}

val firebaseNames = listOf("FIREBASE_APP_ID", "FIREBASE_API_KEY", "FIREBASE_PROJECT_ID", "FIREBASE_SENDER_ID")

android {
    namespace = appId
    compileSdk = 37

    defaultConfig {
        applicationId = appId
        // Android 12. See docs/decisions.md for why.
        minSdk = 31
        targetSdk = 37
        versionCode = appVersion.code
        versionName = appVersion.name
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a pull request's debug APK be installed next to the released app, as
            // "Content Dev" (src/debug/res).
            applicationIdSuffix = ".debug"
            val firebase = firebaseSettings("$appId.debug")
            firebaseNames.forEach { buildConfigField("String", it, "\"${firebase[it].orEmpty()}\"") }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            val firebase = firebaseSettings(appId)
            firebaseNames.forEach { buildConfigField("String", it, "\"${firebase[it].orEmpty()}\"") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // The Firebase settings above (#235).
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("en", "nl", "fr", "de", "es", "it")
        // Builds the per-app language list from the values-xx folders (see res/resources.properties).
        generateLocaleConfig = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        // Do not disable checks or add a baseline without recording why in docs/decisions.md.
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Room writes each database version's schema here (committed), so later versions can be migrated and checked.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    // The library (feeds, articles, reading list and bookmarks) in SQLite (#106, see docs/decisions.md).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Article pictures: hero images, list thumbnails, inline images (see docs/decisions.md).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    // Notifications when something is shared (#235, docs/decisions.md): Firebase Cloud Messaging only.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // Reader view for full articles: Firefox Reader View's algorithm, on a current jsoup (see docs/decisions.md).
    implementation(libs.readability4j)
    implementation(libs.jsoup)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

// Used by the workflows to name the APK <app>-<version>.apk: ./gradlew -q :app:printApkBaseName
tasks.register("printApkBaseName") {
    val baseName = "${rootProject.name}-${appVersion.name}"
    doLast { println(baseName) }
}
