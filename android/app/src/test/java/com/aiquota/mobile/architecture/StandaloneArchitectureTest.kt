package com.aiquota.mobile.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class StandaloneArchitectureTest {
    @Test
    fun androidSourcesDoNotDependOnFirebasePairingFirestoreOrWindowsSync() {
        val root = File(System.getProperty("user.dir"))
        val appDir = if (root.name == "app") root else File(root, "android/app")
        val files = listOf(
            File(appDir, "build.gradle.kts"),
            File(appDir, "src/main/AndroidManifest.xml"),
            File(appDir, "src/main/java/com/aiquota/mobile/MainActivity.kt")
        )
        val source = files.joinToString("\n") { it.readText() }

        listOf(
            "firebase-firestore",
            "AI_QUOTA_FUNCTIONS_BASE_URL",
            ".sync.ForegroundRefreshService",
            "PairingCode",
            "SnapshotRepository"
        ).forEach { marker ->
            assertFalse("$marker must not remain in standalone mobile core", source.contains(marker))
        }
    }
}
