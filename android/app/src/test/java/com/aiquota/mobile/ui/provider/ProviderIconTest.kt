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
        assertEquals(R.drawable.ic_provider_glm, providerIconRes(ProviderId.GLM))
        assertEquals(R.drawable.ic_provider_opencode, providerIconRes(ProviderId.OPENCODE))
        assertEquals(R.drawable.ic_provider_cursor, providerIconRes(ProviderId.CURSOR))
        assertEquals(R.drawable.ic_provider_codex, providerIconRes("codex"))
        assertEquals(R.drawable.ic_provider_glm, providerIconRes("glm"))
        assertEquals(R.drawable.ic_provider_glm, providerIconRes("z-ai"))
        assertEquals(R.drawable.ic_provider_opencode, providerIconRes("opencode"))
        assertEquals(R.drawable.ic_provider_opencode, providerIconRes("open-code"))
        assertEquals(R.drawable.ic_provider_cursor, providerIconRes("cursor"))
    }

    @Test
    fun antigravityIconUsesAdjustedVisualScaleAfterCodexInset() {
        assertEquals(0.85f, providerIconVisualScale(ProviderId.CODEX), 0.001f)
        assertEquals(0.76f, providerIconVisualScale(ProviderId.OPENCODE), 0.001f)
        assertEquals(0.78f, providerIconVisualScale(ProviderId.ANTIGRAVITY), 0.001f)
    }

    @Test
    fun codexIconUsesRootDarkSvgAsWhiteCloudWithDarkCutouts() {
        val vector = File("src/main/res/drawable/ic_provider_codex.xml").readText()

        assertTrue(vector.contains("""android:fillColor="#FFFFFF""""))
        assertTrue(vector.contains("""android:fillColor="#111111""""))
        assertTrue(vector.contains("""android:fillType="evenOdd""""))
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

    @Test
    fun glmIconUsesOfficialZaiSvgPathData() {
        val vector = File("src/main/res/drawable/ic_provider_glm.xml").readText()
        val source = File("src/main/res/raw/z_ai_logo.svg").readText()

        assertTrue(source.contains("M24.51,28.51H5.49"))
        assertTrue(source.contains("M15.47,7.1l-1.3,1.85"))
        assertTrue(vector.contains("M24.51,28.51H5.49"))
        assertTrue(vector.contains("M15.47,7.1l-1.3,1.85"))
        assertTrue(vector.contains("""android:fillColor="#2D2D2D""""))
        assertTrue(vector.contains("""android:fillColor="#FFFFFF""""))
    }

    @Test
    fun opencodeIconUsesOfficialBrandSvgPathData() {
        val vector = File("src/main/res/drawable/ic_provider_opencode.xml").readText()
        val source = File("src/main/res/raw/opencode_logo.svg").readText()

        assertTrue(source.contains("M180 240H60V120H180V240Z"))
        assertTrue(source.contains("M180 60H60V240H180V60ZM240 300H0V0H240V300Z"))
        assertTrue(vector.contains("M210,240H90V120H210V240Z"))
        assertTrue(vector.contains("M210,60H90V240H210V60ZM270,300H30V0H270V300Z"))
        assertTrue(vector.contains("""android:fillColor="#CFCECD""""))
        assertTrue(vector.contains("""android:fillColor="#211E1E""""))
    }
}
