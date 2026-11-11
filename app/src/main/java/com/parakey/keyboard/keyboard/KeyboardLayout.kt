package com.parakey.keyboard.keyboard

enum class KeyAction {
    CHARACTER,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    SYMBOLS,
    LETTERS,
    EMOJI,
    MIC,
    SETTINGS,
    NONE,
}

enum class KeyIcon {
    NONE,
    SHIFT,
    CAPS,
    BACKSPACE,
    ENTER,
    MIC,
    GEAR,
    EMOJI,
    CLIP,
}

data class Key(
    val action: KeyAction,
    val output: String = "",
    val label: String = "",
    val icon: KeyIcon = KeyIcon.NONE,
    val weight: Float = 1f,
    val special: Boolean = false,
    val accent: Boolean = false,
    val numberRow: Boolean = false,
    val id: String = "",
) {
    val stableId: String
        get() = id.ifEmpty { "${action.name}:${output}:${label}:${icon.name}" }
}

data class KeyRow(
    val keys: List<Key>,
    val sideInset: Float = 0f,
    /** Row height relative to a normal key row. The number row is shorter. */
    val heightFactor: Float = 1f,
)

data class KeyboardSpec(
    val rows: List<KeyRow>,
)

enum class KeyboardPage { LETTERS, SYMBOLS, MORE_SYMBOLS, EMOJI, NUMBER }

enum class ShiftState { OFF, ON, CAPS }

object KeyboardLayouts {

    fun specFor(
        page: KeyboardPage,
        numberRow: Boolean = false,
        emojiRecents: List<String> = emptyList(),
        numberDecimal: Boolean = false,
        numberSigned: Boolean = false,
    ): KeyboardSpec = when (page) {
        KeyboardPage.LETTERS -> letters(numberRow)
        KeyboardPage.SYMBOLS -> symbols()
        KeyboardPage.MORE_SYMBOLS -> moreSymbols()
        KeyboardPage.EMOJI -> emoji(emojiRecents)
        KeyboardPage.NUMBER -> numberPad(numberDecimal, numberSigned)
    }

    fun displayLabel(key: Key, page: KeyboardPage, shift: ShiftState): String {
        if (key.action == KeyAction.CHARACTER && page == KeyboardPage.LETTERS) {
            return if (shift == ShiftState.OFF) key.output else key.output.uppercase()
        }
        return key.label.ifEmpty { key.output }
    }

    private fun char(
        c: String,
        weight: Float = 1f,
        special: Boolean = false,
        numberRow: Boolean = false,
    ): Key = Key(
        KeyAction.CHARACTER,
        output = c,
        label = c,
        weight = weight,
        special = special,
        numberRow = numberRow,
    )

    private fun letters(numberRow: Boolean): KeyboardSpec {
        val rows = mutableListOf<KeyRow>()
        if (numberRow) {
            rows.add(
                KeyRow(
                    listOf(
                        char("1", numberRow = true), char("2", numberRow = true),
                        char("3", numberRow = true), char("4", numberRow = true),
                        char("5", numberRow = true), char("6", numberRow = true),
                        char("7", numberRow = true), char("8", numberRow = true),
                        char("9", numberRow = true), char("0", numberRow = true),
                    ),
                    heightFactor = 0.8f,
                ),
            )
        }
        rows.addAll(
            listOf(
                KeyRow(
                    listOf(
                        char("q"), char("w"), char("e"), char("r"), char("t"),
                        char("y"), char("u"), char("i"), char("o"), char("p"),
                    ),
                ),
                KeyRow(
                    listOf(
                        char("a"), char("s"), char("d"), char("f"), char("g"),
                        char("h"), char("j"), char("k"), char("l"),
                    ),
                    sideInset = 0.5f,
                ),
                KeyRow(
                    listOf(
                        Key(KeyAction.SHIFT, icon = KeyIcon.SHIFT, weight = 1.5f, special = true),
                        char("z"), char("x"), char("c"), char("v"), char("b"), char("n"), char("m"),
                        Key(KeyAction.BACKSPACE, icon = KeyIcon.BACKSPACE, weight = 1.5f, special = true),
                    ),
                ),
                KeyRow(
                    listOf(
                        Key(KeyAction.SYMBOLS, label = "?123", weight = 1.35f, special = true),
                        char(","),
                        Key(KeyAction.EMOJI, icon = KeyIcon.EMOJI, weight = 0.85f),
                        Key(KeyAction.SPACE, label = "space", weight = 4.0f),
                        char("."),
                        Key(KeyAction.ENTER, icon = KeyIcon.ENTER, weight = 1.35f, special = true, accent = true),
                    ),
                ),
            ),
        )
        return KeyboardSpec(rows)
    }

    private fun symbols() = KeyboardSpec(
        listOf(
            KeyRow(
                listOf(
                    char("1"), char("2"), char("3"), char("4"), char("5"),
                    char("6"), char("7"), char("8"), char("9"), char("0"),
                ),
            ),
            KeyRow(
                listOf(
                    char("@"), char("#"), char("$"), char("_"), char("&"),
                    char("-"), char("+"), char("("), char(")"), char("/"),
                ),
            ),
            KeyRow(
                listOf(
                    Key(KeyAction.SYMBOLS, label = "=\\<", weight = 1.5f, special = true),
                    char("*"), char("\""), char("'"), char(":"), char(";"), char("!"), char("?"),
                    Key(KeyAction.BACKSPACE, icon = KeyIcon.BACKSPACE, weight = 1.5f, special = true),
                ),
            ),
            KeyRow(
                listOf(
                    Key(KeyAction.LETTERS, label = "ABC", weight = 1.5f, special = true),
                    char(","),
                    Key(KeyAction.SPACE, label = "space", weight = 4.6f),
                    char("."),
                    Key(KeyAction.ENTER, icon = KeyIcon.ENTER, weight = 1.5f, special = true, accent = true),
                ),
            ),
        ),
    )

    private fun moreSymbols() = KeyboardSpec(
        listOf(
            KeyRow(
                listOf(
                    char("~"), char("`"), char("|"), char("•"), char("√"),
                    char("π"), char("÷"), char("×"), char("¶"), char("∆"),
                ),
            ),
            KeyRow(
                listOf(
                    char("£"), char("¥"), char("€"), char("©"), char("®"),
                    char("™"), char("✓"), char("["), char("]"), char("{"),
                ),
            ),
            KeyRow(
                listOf(
                    Key(KeyAction.SYMBOLS, label = "?123", weight = 1.5f, special = true),
                    char("}"), char("°"), char("^"), char("%"), char("="), char("<"), char(">"),
                    Key(KeyAction.BACKSPACE, icon = KeyIcon.BACKSPACE, weight = 1.5f, special = true),
                ),
            ),
            KeyRow(
                listOf(
                    Key(KeyAction.LETTERS, label = "ABC", weight = 1.5f, special = true),
                    char(","),
                    Key(KeyAction.SPACE, label = "space", weight = 4.6f),
                    char("."),
                    Key(KeyAction.ENTER, icon = KeyIcon.ENTER, weight = 1.5f, special = true, accent = true),
                ),
            ),
        ),
    )

    private fun emoji(recents: List<String>): KeyboardSpec {
        val defaults = listOf(
            "😀", "😂", "🥰", "😎", "🤔", "😭", "😡", "🎉",
            "👍", "👎", "❤️", "🔥", "✨", "🙏", "💯", "🚀",
            "☕", "🍕", "🌙", "☀️", "🎵", "📸", "✅", "❌",
        )
        val emojis = (recents.take(8) + defaults).distinct().take(24)
        val rows = emojis.chunked(8).map { row ->
            KeyRow(row.map { char(it) })
        }
        return KeyboardSpec(
            rows + KeyRow(
                listOf(
                    Key(KeyAction.LETTERS, label = "ABC", weight = 1.5f, special = true),
                    char(","),
                    Key(KeyAction.SPACE, label = "space", weight = 4.0f),
                    char("."),
                    Key(KeyAction.BACKSPACE, icon = KeyIcon.BACKSPACE, weight = 1.35f, special = true),
                    Key(KeyAction.ENTER, icon = KeyIcon.ENTER, weight = 1.35f, special = true, accent = true),
                ),
            ),
        )
    }

    private fun numberPad(decimal: Boolean, signed: Boolean): KeyboardSpec {
        val bottom = mutableListOf<Key>()
        bottom.add(Key(KeyAction.LETTERS, label = "ABC", weight = 1f, special = true))
        bottom.add(Key(KeyAction.BACKSPACE, icon = KeyIcon.BACKSPACE, weight = 1f, special = true))
        bottom.add(char("0"))
        if (decimal) bottom.add(char("."))
        if (signed) bottom.add(char("-"))
        bottom.add(Key(KeyAction.ENTER, icon = KeyIcon.ENTER, weight = 1f, special = true, accent = true))
        return KeyboardSpec(
            listOf(
                KeyRow(listOf(char("1"), char("2"), char("3"))),
                KeyRow(listOf(char("4"), char("5"), char("6"))),
                KeyRow(listOf(char("7"), char("8"), char("9"))),
                KeyRow(bottom),
            ),
        )
    }
}
