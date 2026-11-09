package com.parakey.keyboard.text

import android.content.Context
import com.parakey.keyboard.R

/**
 * Offline English typing assistant.
 *
 * Word frequencies come from the AOSP LatinIME en_US word list (Apache-2.0),
 * filtered to the common range and bundled as res/raw/words.txt. Suggestions
 * are prefix matches ordered by frequency. Corrections only fire for a
 * single-edit (including adjacent transposition) candidate in the common range,
 * so valid words, contractions, names and personal words are left alone. A
 * tiny bigram table supplies next-word suggestions.
 */
object SuggestionEngine {

    private const val MAX_RANK_FOR_CORRECTION = 6_000
    private const val MAX_SUGGESTIONS = 3

    private val fallbackWords = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "it",
        "for", "not", "on", "with", "he", "as", "you", "do", "at", "this",
        "but", "his", "by", "from", "they", "we", "say", "her", "she", "or",
        "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know",
        "take", "people", "into", "year", "your", "good", "some", "could",
        "them", "see", "other", "than", "then", "now", "look", "only", "come",
        "its", "think", "also", "back", "after", "use", "two", "how", "our",
        "work", "first", "well", "way", "even", "new", "want", "because",
        "any", "these", "give", "day", "most", "us", "hello", "help", "thanks",
        "please", "message", "keyboard", "phone", "name", "test", "voice",
    )

    private val typoMap = mapOf(
        "teh" to "the", "hte" to "the", "recieve" to "receive",
        "recieved" to "received", "definately" to "definitely",
        "adress" to "address", "seperate" to "separate", "occured" to "occurred",
        "untill" to "until", "wierd" to "weird", "freind" to "friend",
        "beleive" to "believe", "grammer" to "grammar", "tomorow" to "tomorrow",
        "becuase" to "because", "thier" to "their", "alot" to "a lot",
        "cant" to "can't", "dont" to "don't", "youre" to "you're",
        "theyre" to "they're", "wont" to "won't", "didnt" to "didn't",
        "doesnt" to "doesn't", "isnt" to "isn't", "wasnt" to "wasn't",
        "couldnt" to "couldn't", "shouldnt" to "shouldn't", "wouldnt" to "wouldn't",
    )

    private val bigrams = mapOf(
        "i" to listOf("am", "think", "will", "have", "was"),
        "you" to listOf("can", "are", "have", "will", "should"),
        "we" to listOf("will", "can", "are", "have", "should"),
        "they" to listOf("are", "will", "have", "can", "were"),
        "the" to listOf("best", "same", "first", "last", "next"),
        "thank" to listOf("you"),
        "thanks" to listOf("for", "again"),
        "good" to listOf("morning", "night", "luck", "job"),
        "how" to listOf("are", "is", "do"),
        "what" to listOf("is", "are", "do"),
        "where" to listOf("are", "is"),
        "see" to listOf("you"),
        "talk" to listOf("to", "soon"),
        "on" to listOf("the", "my", "a"),
        "at" to listOf("the", "home", "work"),
        "in" to listOf("the", "a", "my"),
        "to" to listOf("the", "be", "do", "see"),
        "for" to listOf("the", "you", "me"),
        "and" to listOf("the", "then", "i"),
        "please" to listOf("let", "send", "call"),
        "can" to listOf("you", "i", "we"),
        "do" to listOf("you", "not", "it"),
        "this" to listOf("is", "was", "will"),
        "that" to listOf("is", "was", "will"),
        "it" to listOf("is", "was", "will"),
        "my" to listOf("name", "phone", "friend"),
        "your" to listOf("name", "phone", "message"),
    )

    @Volatile
    private var dictionary: List<String> = fallbackWords
    private val rank = HashMap<String, Int>()
    private val known = HashSet<String>()

    @Synchronized
    fun initialize(context: Context) {
        if (dictionary.size > fallbackWords.size) return
        val words = ArrayList<String>(24_000)
        try {
            context.resources.openRawResource(R.raw.words).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val word = line.trim().lowercase()
                    if (word.length in 2..32 && word.all { it.isLetter() || it == '\'' }) {
                        words.add(word)
                    }
                }
            }
        } catch (_: Throwable) {
        }
        for (word in fallbackWords) {
            if (!words.contains(word)) words.add(word)
        }
        val unique = words.distinct()
        dictionary = unique
        rank.clear()
        known.clear()
        unique.forEachIndexed { index, word ->
            rank[word] = index
            known.add(word)
        }
    }

    fun dictionarySize(): Int = dictionary.size

    fun suggestions(
        prefix: String,
        previousWord: String = "",
        personalWords: Collection<String> = emptyList(),
    ): List<String> {
        if (prefix.isBlank()) return nextWords(previousWord, personalWords)
        val lower = prefix.lowercase()
        val results = ArrayList<String>(MAX_SUGGESTIONS + 2)
        val correction = correct(prefix, personalWords)
        if (correction != null && !correction.equals(prefix, ignoreCase = true)) {
            results.add(matchCase(prefix, correction))
        }
        val personal = personalWords
            .asSequence()
            .filter { it.lowercase().startsWith(lower) && it.lowercase() != lower }
            .map { matchCase(prefix, it) }
            .take(MAX_SUGGESTIONS)
            .toList()
        results.addAll(personal)
        var added = 0
        for (word in dictionary) {
            if (added >= MAX_SUGGESTIONS + 2) break
            if (word.startsWith(lower) && word != lower) {
                val display = matchCase(prefix, word)
                if (!results.contains(display)) {
                    results.add(display)
                    added++
                }
            }
        }
        return results.distinct().take(MAX_SUGGESTIONS)
    }

    fun nextWords(previousWord: String, personalWords: Collection<String> = emptyList()): List<String> {
        if (previousWord.isBlank()) return listOf("the", "I", "you")
        val lower = previousWord.lowercase()
        val next = bigrams[lower] ?: return emptyList()
        return next.map { if (it == "i") "I" else it }.take(MAX_SUGGESTIONS)
    }

    fun correct(word: String, personalWords: Collection<String> = emptyList()): String? {
        if (word.length < 2) return null
        if (word.any { it.isDigit() || it == '@' || it == '/' || it == ':' || it == '_' }) return null
        val lower = word.lowercase()
        // Common misspellings and contractions are checked before the known set.
        typoMap[lower]?.let { return matchCase(word, it) }
        if (known.contains(lower) || personalWords.any { it.equals(word, ignoreCase = true) }) {
            return null
        }
        // Never rewrite an apostrophe word; it is almost always a contraction.
        if (word.contains("'")) return null
        // Auto-replace only lowercase or title-case tokens, not names/URLs.
        if (word.drop(1).any { it.isUpperCase() }) return null
        if (lower.length < 3) return null
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        val first = lower.first()
        for (candidate in dictionary) {
            if (candidate.first() != first) continue
            if (kotlin.math.abs(candidate.length - lower.length) > 1) continue
            if (!oneEditAway(lower, candidate)) continue
            val candidateRank = rank[candidate] ?: Int.MAX_VALUE
            if (candidateRank > MAX_RANK_FOR_CORRECTION) continue
            if (candidateRank < bestRank) {
                bestRank = candidateRank
                best = candidate
            }
        }
        return best?.let { matchCase(word, it) }
    }

    fun isKnown(word: String, personalWords: Collection<String> = emptyList()): Boolean {
        if (word.isBlank()) return true
        val lower = word.lowercase()
        return known.contains(lower) || personalWords.any { it.equals(word, ignoreCase = true) }
    }

    /** True for one substitution, insertion, deletion or adjacent transposition. */
    private fun oneEditAway(a: String, b: String): Boolean {
        if (a == b) return false
        if (a.length == b.length) {
            var mismatch = -1
            for (i in a.indices) {
                if (a[i] != b[i]) {
                    if (mismatch >= 0) {
                        return i == mismatch + 1 && a[mismatch] == b[i] && a[i] == b[mismatch]
                    }
                    mismatch = i
                }
            }
            return mismatch >= 0
        }
        val shorter = if (a.length < b.length) a else b
        val longer = if (a.length < b.length) b else a
        if (longer.length - shorter.length != 1) return false
        var i = 0
        while (i < shorter.length && shorter[i] == longer[i]) i++
        return shorter.substring(i) == longer.substring(i + 1)
    }

    private fun matchCase(original: String, replacement: String): String {
        if (original.firstOrNull()?.isUpperCase() == true) {
            return replacement.replaceFirstChar { it.uppercaseChar() }
        }
        return replacement
    }

    fun currentWord(textBeforeCursor: String): String =
        Regex("[A-Za-z']+$").find(textBeforeCursor)?.value.orEmpty()

    fun previousWord(textBeforeCursor: String): String {
        val word = currentWord(textBeforeCursor)
        val prior = textBeforeCursor.dropLast(word.length).trimEnd()
        return Regex("[A-Za-z']+$").find(prior)?.value.orEmpty()
    }
}
