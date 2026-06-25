package com.aiquota.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderGaugeColorTest {
    @Test
    fun normalizesHexAndRgbInput() {
        assertEquals("#D1775E", ProviderGaugeColor.normalize("d1775e"))
        assertEquals("#D1775E", ProviderGaugeColor.normalize("#d1775e"))
        assertEquals("#D1775E", ProviderGaugeColor.normalize("209,119,94"))
        assertEquals("#D1775E", ProviderGaugeColor.normalize("rgb(209, 119, 94)"))
    }

    @Test
    fun rejectsInvalidColorInput() {
        assertNull(ProviderGaugeColor.normalize(""))
        assertNull(ProviderGaugeColor.normalize("#12345"))
        assertNull(ProviderGaugeColor.normalize("256,119,94"))
        assertNull(ProviderGaugeColor.normalize("not-a-color"))
    }
}
