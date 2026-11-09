package com.parakey.keyboard

import com.parakey.keyboard.keyboard.KeyAction
import com.parakey.keyboard.keyboard.KeyboardLayouts
import com.parakey.keyboard.keyboard.KeyboardPage
import com.parakey.keyboard.keyboard.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    @Test
    fun lettersHaveFourRows() {
        assertEquals(4, KeyboardLayouts.specFor(KeyboardPage.LETTERS).rows.size)
    }

    @Test
    fun topLetterRowHasTenKeys() {
        assertEquals(10, KeyboardLayouts.specFor(KeyboardPage.LETTERS).rows[0].keys.size)
    }

    @Test
    fun bottomRowHasSpaceAndEnter() {
        val row = KeyboardLayouts.specFor(KeyboardPage.LETTERS).rows.last()
        assertTrue(row.keys.any { it.action == KeyAction.SPACE })
        assertTrue(row.keys.any { it.action == KeyAction.ENTER })
        assertTrue(row.keys.any { it.action == KeyAction.SYMBOLS })
    }

    @Test
    fun shiftLabelsAreUppercaseOnlyWhenShiftIsOn() {
        val key = KeyboardLayouts.specFor(KeyboardPage.LETTERS).rows[0].keys[0]
        assertEquals("q", KeyboardLayouts.displayLabel(key, KeyboardPage.LETTERS, ShiftState.OFF))
        assertEquals("Q", KeyboardLayouts.displayLabel(key, KeyboardPage.LETTERS, ShiftState.ON))
        assertEquals("Q", KeyboardLayouts.displayLabel(key, KeyboardPage.LETTERS, ShiftState.CAPS))
    }

    @Test
    fun symbolsPageStartsWithNumbers() {
        val spec = KeyboardLayouts.specFor(KeyboardPage.SYMBOLS)
        assertEquals("1", spec.rows[0].keys[0].label)
        assertEquals("0", spec.rows[0].keys[9].label)
    }

    @Test
    fun emojiPageHasThreeEmojiRowsPlusActions() {
        val spec = KeyboardLayouts.specFor(KeyboardPage.EMOJI)
        assertEquals(4, spec.rows.size)
        assertEquals(8, spec.rows[0].keys.size)
        assertEquals(24, spec.rows.take(3).sumOf { it.keys.size })
    }
}
