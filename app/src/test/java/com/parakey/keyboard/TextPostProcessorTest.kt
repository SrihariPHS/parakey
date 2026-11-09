package com.parakey.keyboard

import com.parakey.keyboard.text.TextPostProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

class TextPostProcessorTest {

    @Test
    fun trimsAndCollapsesWhitespace() {
        assertEquals("Hello world.", TextPostProcessor.clean("  hello   world.  "))
    }

    @Test
    fun removesSpaceBeforePunctuation() {
        assertEquals("Hello, world!", TextPostProcessor.clean("Hello , world !"))
    }

    @Test
    fun capitalizesFirstLetter() {
        assertEquals("Parakey is ready.", TextPostProcessor.clean("parakey is ready."))
    }

    @Test
    fun keepsExistingCapitalization() {
        assertEquals("NVIDIA makes GPUs.", TextPostProcessor.clean("NVIDIA makes GPUs."))
    }

    @Test
    fun addsLeadingSpaceAfterWord() {
        assertEquals(" world", TextPostProcessor.prependSpaceIfNeeded("Hello", "world"))
    }

    @Test
    fun doesNotAddLeadingSpaceAtStart() {
        assertEquals("Hello", TextPostProcessor.prependSpaceIfNeeded("", "Hello"))
    }

    @Test
    fun doesNotDoubleSpace() {
        assertEquals("world", TextPostProcessor.prependSpaceIfNeeded("Hello ", "world"))
    }

    @Test
    fun countsWords() {
        assertEquals(3, TextPostProcessor.wordCount("one two three"))
        assertEquals(0, TextPostProcessor.wordCount("   "))
    }
}
