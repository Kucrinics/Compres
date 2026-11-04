package com.kompres.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfMergeCoreTest {

    @Test
    fun fitSquareOnA4() {
        val rect = PdfMergeCore.fitRect(1000, 1000)
        assertEquals(24f, rect.left, 0.01f)
        assertEquals(147.5f, rect.top, 0.01f)
        assertEquals(571f, rect.right, 0.01f)
        assertEquals(694.5f, rect.bottom, 0.01f)
    }

    @Test
    fun fitLandscapeOnA4() {
        val rect = PdfMergeCore.fitRect(2000, 1000)
        assertEquals(547f, rect.width, 0.01f)
        assertEquals(273.5f, rect.height, 0.01f)
        assertEquals(24f, rect.left, 0.01f)
        assertEquals(284.25f, rect.top, 0.01f)
    }

    @Test
    fun fitPortraitOnA4() {
        val rect = PdfMergeCore.fitRect(500, 2000)
        assertEquals(198.5f, rect.width, 0.01f)
        assertEquals(794f, rect.height, 0.01f)
        assertEquals(198.25f, rect.left, 0.01f)
        assertEquals(24f, rect.top, 0.01f)
    }

    @Test
    fun fitRejectsInvalidSize() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfMergeCore.fitRect(0, 100)
        }
    }
}
