package com.parakey.keyboard

import com.parakey.keyboard.text.SuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    @Test
    fun commonTamposAndExpansionsCorrect() {
        assertEquals("the", SuggestionEngine.correct("teh"))
        assertEquals("receive", SuggestionEngine.correct("recieve"))
        assertEquals("by the way", SuggestionEngine.correct("btw"))
        assertEquals("I don't know", SuggestionEngine.correct("idk"))
    }

    @Test
    fun validWordsAndAcronymsAreLeftAlone() {
        assertEquals(null, SuggestionEngine.correct("keyboard"))
        assertEquals(null, SuggestionEngine.correct("NASA"))
        assertEquals(null, SuggestionEngine.correct("user@example.com"))
    }

    @Test
    fun commonNextWordsArePredicted() {
        assertEquals("you", SuggestionEngine.nextWords("thank").firstOrNull())
        assertEquals("there", SuggestionEngine.nextWords("hello").firstOrNull())
        assertTrue(SuggestionEngine.nextWords("good").contains("morning"))
    }

    @Test
    fun textExpansionsAreConvenienceNotAggressiveRewrites() {
        assertEquals("by the way", SuggestionEngine.correct("btw"))
        assertEquals("on my way", SuggestionEngine.correct("omw"))
        assertEquals(null, SuggestionEngine.correct("ok"))
        assertEquals(null, SuggestionEngine.correct("lol"))
    }
}
