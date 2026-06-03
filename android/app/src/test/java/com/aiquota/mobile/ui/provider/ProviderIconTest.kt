package com.aiquota.mobile.ui.provider

import com.aiquota.mobile.R
import com.aiquota.mobile.local.ProviderId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderIconTest {
    @Test
    fun providerIconsUseProviderSpecificResources() {
        assertEquals(R.drawable.ic_provider_codex, providerIconRes(ProviderId.CODEX))
        assertEquals(R.drawable.ic_provider_cursor, providerIconRes(ProviderId.CURSOR))
        assertEquals(R.drawable.ic_provider_codex, providerIconRes("codex"))
        assertEquals(R.drawable.ic_provider_cursor, providerIconRes("cursor"))
    }

    @Test
    fun antigravityIconUsesAdjustedVisualScaleAfterCodexInset() {
        assertEquals(0.85f, providerIconVisualScale(ProviderId.CODEX), 0.001f)
        assertEquals(0.78f, providerIconVisualScale(ProviderId.ANTIGRAVITY), 0.001f)
    }

    @Test
    fun codexIconUsesRootDarkSvgAsWhiteCloudWithDarkCutouts() {
        val vector = File("src/main/res/drawable/ic_provider_codex.xml").readText()
        val source = File("../../codex-dark.svg").readText()

        assertTrue(vector.contains("""android:fillColor="#FFFFFF""""))
        assertTrue(vector.contains("""android:fillColor="#111111""""))
        assertTrue(vector.contains("""android:fillType="evenOdd""""))
        assertTrue(source.contains("""fill="#fff""""))
        assertTrue(vector.contains("M8.086"))
        assertTrue(vector.contains("M7.282"))
        assertTrue(vector.contains("M12.728"))
        assertTrue(vector.contains("""android:strokeColor="#111111""""))
        assertTrue(vector.contains("""android:strokeWidth="1.6""""))
        assertTrue(vector.contains("""android:scaleX="0.88""""))
        assertTrue(vector.contains("""android:scaleY="0.88""""))
        assertTrue(vector.contains("""android:translateX="1.44""""))
        assertTrue(vector.contains("""android:translateY="1.44""""))
        assertTrue(vector.indexOf("""android:fillColor="#111111"""") < vector.indexOf("""android:fillColor="#FFFFFF""""))
    }
}
