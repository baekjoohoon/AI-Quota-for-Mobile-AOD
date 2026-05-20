package com.aiusage.mobile.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class StandaloneArchitectureTest {
    @Test
    fun androidSourcesDoNotDependOnFirebasePairingOrWindowsSync() {
        val root = File(System.getProperty("user.dir"))
        val appDir = if (root.name == "app") root else File(root, "android/app")
        val files = listOf(
            File(appDir, "build.gradle.kts"),
            File(appDir, "src/main/AndroidManifest.xml"),
            File(appDir, "src/main/java/com/aiusage/mobile/MainActivity.kt")
        )
        val source = files.joinToString("\n") { it.readText() }

        listOf(
            "com.google.gms.google-services",
            "firebase-auth",
            "firebase-firestore",
            "play-services-auth",
            "AI_USAGE_FUNCTIONS_BASE_URL",
            ".sync.ForegroundRefreshService",
            "FirebaseAuth",
            "PairingCode",
            "SnapshotRepository"
        ).forEach { marker ->
            assertFalse("$marker must not remain in standalone mobile core", source.contains(marker))
        }
    }
}
