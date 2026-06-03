package com.aiquota.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderUpdatedAtDisplayTest {
    @Test
    fun formatsSnapshotUpdatedAtInKst() {
        assertEquals(
            "26-05-21 14:00",
            displaySnapshotUpdatedAt("2026-05-21T05:00:13.689919Z")
        )
    }

    @Test
    fun updatedAtLineDoesNotAddUpdatedPrefix() {
        assertEquals(
            "26-05-21 14:00",
            displaySnapshotUpdatedAtLine("2026-05-21T05:00:13.689919Z")
        )
    }

    @Test
    fun formatsSpaceSeparatedFractionalTimestampInKst() {
        assertEquals(
            "26-05-21 14:00",
            displaySnapshotUpdatedAt("2026-05-21T05:00:13 689919Z")
        )
    }
}
