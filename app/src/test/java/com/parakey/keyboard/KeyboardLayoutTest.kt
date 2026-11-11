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
    fun numberRowIsOptionalAndAddsOneDigitRow() {
        val without = KeyboardLayouts.specFor(KeyboardPage.LETTERS)
        val with = KeyboardLayouts.specFor(KeyboardPage.LETTERS, numberRow = true)
        assertEquals(4, without.rows.size)
        assertEquals(5, with.rows.size)
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            with.rows[0].keys.map { it.label },
        )
        assertTrue(with.rows[0].heightFactor < 0.8f)
        assertTrue(with.rows[0].keys.all { it.numberRow })
        assertEquals("q", with.rows[1].keys[0].label)
    }

    @Test
    fun numberPadIsThreeByFourWithBackspaceAndEnter() {
        val spec = KeyboardLayouts.specFor(KeyboardPage.NUMBER)
        assertEquals(4, spec.rows.size)
        assertEquals(listOf("1", "2", "3"), spec.rows[0].keys.map { it.label })
        assertEquals(listOf("4", "5", "6"), spec.rows[1].keys.map { it.label })
        assertEquals(listOf("7", "8", "9"), spec.rows[2].keys.map { it.label })
        assertEquals("0", spec.rows[3].keys[1].label)
        assertTrue(spec.rows[3].keys.any { it.action == KeyAction.BACKSPACE })
        assertTrue(spec.rows[3].keys.any { it.action == KeyAction.ENTER })
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

    @Test
    fun recentEmojiMoveToTheFrontWithoutChangingPageSize() {
        val plain = KeyboardLayouts.specFor(KeyboardPage.EMOJI)
        val recent = KeyboardLayouts.specFor(KeyboardPage.EMOJI, emojiRecents = listOf("🚀"))
        assertEquals(4, recent.rows.size)
        assertEquals(24, recent.rows.take(3).sumOf { it.keys.size })
        assertEquals("🚀", recent.rows[0].keys[0].label)
        assertEquals(plain.rows[0].keys.size, recent.rows[0].keys.size)
    }
}
