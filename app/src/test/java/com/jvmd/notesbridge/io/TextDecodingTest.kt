package com.jvmd.notesbridge.io

import com.jvmd.notesbridge.TestSupport.fixture
import org.junit.Assert.assertEquals
import org.junit.Test

class TextDecodingTest {
    @Test
    fun decodesUtf8WithAndWithoutBom() {
        assertEquals("Привет", TextDecoding.decode("Привет".toByteArray()))
        assertEquals("Hi", TextDecoding.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Hi".toByteArray()))
    }

    @Test
    fun decodesUtf16() {
        assertEquals("Hello", TextDecoding.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Hello".toByteArray(Charsets.UTF_16LE)))
        assertEquals("Hello world", TextDecoding.decode("Hello world".toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun recognizesWindows1251() {
        assertEquals("Привет, мир! Это текст в кодировке windows-1251.\n", TextDecoding.decode(fixture("cp1251.txt").readBytes()))
    }

    @Test
    fun fallsBackToWindows1252ForWesternText() {
        assertEquals("café crème", TextDecoding.decode("café crème".toByteArray(charset("windows-1252"))))
    }
}
