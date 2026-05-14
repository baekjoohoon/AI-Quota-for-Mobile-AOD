import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val debugAdMobApplicationId = "ca-app-pub-3940256099942544~3347511713"
val debugAdMobBannerAdUnitId = "ca-app-pub-3940256099942544/9214589741"
val releaseAdMobApplicationId = providers.gradleProperty("AI_USAGE_ADMOB_APP_ID").orNull.orEmpty()
val releaseAdMobBannerAdUnitId = providers.gradleProperty("AI_USAGE_ADMOB_BANNER_AD_UNIT_ID").orNull.orEmpty()

fun String.asBuildConfigString(): String {
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.aiusage.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aiusage.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField(
            "String",
            "AI_USAGE_FUNCTIONS_BASE_URL",
            "\"https://us-central1-ai-usage-for-mobile.cloudfunctions.net\""
        )
        manifestPlaceholders["adMobApplicationId"] = debugAdMobApplicationId
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["adMobApplicationId"] = debugAdMobApplicationId
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", debugAdMobBannerAdUnitId.asBuildConfigString())
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            manifestPlaceholders["adMobApplicationId"] = releaseAdMobApplicationId.ifBlank { debugAdMobApplicationId }
            buildConfigField("boolean", "ADS_ENABLED", (releaseAdMobApplicationId.isNotBlank() && releaseAdMobBannerAdUnitId.isNotBlank()).toString())
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", releaseAdMobBannerAdUnitId.asBuildConfigString())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
