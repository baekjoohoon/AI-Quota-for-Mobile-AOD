plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

import java.util.Properties

val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        releaseKeystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = releaseKeystorePropertiesFile.exists()
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val debugStoreFilePath = localProperties.getProperty("aiquota.debugStoreFile")?.takeIf { it.isNotBlank() }
val debugStorePassword = localProperties.getProperty("aiquota.debugStorePassword")?.takeIf { it.isNotBlank() }
val debugKeyAlias = localProperties.getProperty("aiquota.debugKeyAlias")?.takeIf { it.isNotBlank() }
val debugKeyPassword = localProperties.getProperty("aiquota.debugKeyPassword")?.takeIf { it.isNotBlank() }
val googleAndroidOAuthClientId = localProperties.getProperty("aiquota.googleAndroidOAuthClientId")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: ""
val geminiCliOAuthClientId = "681255809395-oo8ft2oprdrnp9e3aqf6av3hmdib135j.apps.googleusercontent.com"
val antigravityOAuthClientId = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"
val googleAndroidOAuthRedirectScheme = googleAndroidOAuthClientId
    .takeIf { it.endsWith(".apps.googleusercontent.com") }
    ?.removeSuffix(".apps.googleusercontent.com")
    ?.let { "com.googleusercontent.apps.$it" }
    ?: "com.aiquota"
val hasLocalDebugKeystore = listOf(
    debugStoreFilePath,
    debugStorePassword,
    debugKeyAlias,
    debugKeyPassword
).all { it != null } && rootProject.file(debugStoreFilePath!!).exists()
val expectedFirebaseProjectNumber = "550123003638"
val expectedFirebaseProjectId = "com-aiquota-mobile"
val expectedFirebaseAppId = "1:550123003638:android:b77771790177d817eb56d7"
val placeholderFirebaseValues = listOf(
    "123456789012",
    "0000000000000000000000"
)

val verifyReleaseFirebaseResources = tasks.register("verifyReleaseFirebaseResources") {
    val googleServicesFile = project.file("google-services.json")

    inputs.file(googleServicesFile)

    doLast {
        require(googleServicesFile.isFile) {
            "Missing android/app/google-services.json; release builds must include the real Firebase app config."
        }

        val googleServices = googleServicesFile.readText()
        require(expectedFirebaseProjectNumber in googleServices) {
            "google-services.json does not target Firebase project number $expectedFirebaseProjectNumber."
        }
        require(expectedFirebaseProjectId in googleServices) {
            "google-services.json does not target Firebase project id $expectedFirebaseProjectId."
        }
        require(expectedFirebaseAppId in googleServices) {
            "google-services.json does not target Firebase app id $expectedFirebaseAppId."
        }
        require(Regex("\"current_key\"\\s*:\\s*\"AIza[0-9A-Za-z_-]+\"").containsMatchIn(googleServices)) {
            "google-services.json does not contain a Firebase API key."
        }
        require(placeholderFirebaseValues.none { it in googleServices }) {
            "google-services.json contains placeholder Firebase values."
        }
    }
}

android {
    namespace = "com.aiquota.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aiquota.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "1.1.0"
        buildConfigField("String", "GOOGLE_ANDROID_OAUTH_CLIENT_ID", "\"$googleAndroidOAuthClientId\"")
        buildConfigField("String", "GOOGLE_ANDROID_OAUTH_REDIRECT_SCHEME", "\"$googleAndroidOAuthRedirectScheme\"")
        buildConfigField("String", "GEMINI_CLI_OAUTH_CLIENT_ID", "\"$geminiCliOAuthClientId\"")
        buildConfigField("String", "ANTIGRAVITY_OAUTH_CLIENT_ID", "\"$antigravityOAuthClientId\"")
        manifestPlaceholders["appAuthRedirectScheme"] = googleAndroidOAuthRedirectScheme
    }

    signingConfigs {
        if (hasLocalDebugKeystore) {
            create("localDebug") {
                storeFile = rootProject.file(debugStoreFilePath!!)
                storePassword = debugStorePassword
                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
            }
        }
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreProperties["storeFile"] as String)
                storePassword = releaseKeystoreProperties["storePassword"] as String
                keyAlias = releaseKeystoreProperties["keyAlias"] as String
                keyPassword = releaseKeystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            if (hasLocalDebugKeystore) {
                signingConfig = signingConfigs.getByName("localDebug")
            }
        }
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.matching { task -> task.name == "processReleaseGoogleServices" }.configureEach {
    dependsOn(verifyReleaseFirebaseResources)
}

tasks.matching { task -> task.name in setOf("bundleRelease", "assembleRelease") }.configureEach {
    dependsOn(verifyReleaseFirebaseResources)
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("net.openid:appauth:0.11.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
