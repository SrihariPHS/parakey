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
    private const val MAX_RANK_FOR_TWO_EDIT = 1_500
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
        "going", "want", "need", "today", "tomorrow",
    )

    // Common technical terms, product names and names that are not in the
    // AOSP list but should never be "corrected" into an unrelated word.
    private val protectedWords = setOf(
        "params", "nginx", "retries", "grafana", "biryani", "parakey",
        "priya", "jordan", "srihari", "kotlin", "gradle", "android",
        "github", "docker", "kubernetes", "postgres", "sqlite", "json",
        "api", "url", "uuid", "http", "https",
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
        "mightve" to "might've", "couldve" to "could've", "shouldve" to "should've",
        "wouldve" to "would've", "mustve" to "must've", "mightnt" to "mightn't",
        "youve" to "you've", "weve" to "we've", "theyve" to "they've",
        "ive" to "I've", "im" to "I'm", "youll" to "you'll",
        "theyll" to "they'll", "youd" to "you'd", "thats" to "that's",
        "whats" to "what's", "hows" to "how's", "wheres" to "where's",
        "heres" to "here's", "theres" to "there's", "arent" to "aren't",
        "werent" to "weren't", "hasnt" to "hasn't", "havent" to "haven't",
        "hadnt" to "hadn't", "mustnt" to "mustn't", "neednt" to "needn't",
        "liek" to "like", "adn" to "and", "taht" to "that", "jsut" to "just",
        "becasue" to "because", "whta" to "what", "yuo" to "you",
        "neccessary" to "necessary", "accomodate" to "accommodate",
        "embarass" to "embarrass", "occassion" to "occasion",
        "tommorow" to "tomorrow", "wich" to "which", "becouse" to "because",
        "thnak" to "thank", "pls" to "please", "plz" to "please",
        "yall" to "y'all", "aint" to "ain't",
        "btw" to "by the way", "idk" to "I don't know", "omw" to "on my way",
        "lmk" to "let me know", "imo" to "in my opinion", "tbh" to "to be honest",
        "brb" to "be right back", "gtg" to "got to go", "smh" to "shaking my head",
    )

    private val bigrams = mapOf(
        "i" to listOf("am", "think", "will", "have", "was"),
        "you" to listOf("can", "are", "have", "will", "should"),
        "we" to listOf("will", "can", "are", "have", "should"),
        "they" to listOf("are", "will", "have", "can", "were"),
        "thank" to listOf("you"),
        "thanks" to listOf("for", "again"),
        "how" to listOf("are", "is", "do"),
        "what" to listOf("is", "are", "do"),
        "where" to listOf("are", "is"),
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
        "hello" to listOf("there", "again", "everyone"),
        "hi" to listOf("there", "how", "everyone"),
        "hey" to listOf("there", "how", "you"),
        "sorry" to listOf("about", "for", "I"),
        "happy" to listOf("birthday", "new", "to"),
        "merry" to listOf("christmas"),
        "good" to listOf("morning", "night", "afternoon", "evening", "luck", "job"),
        "have" to listOf("a", "you", "to", "been"),
        "let" to listOf("me", "us", "them"),
        "make" to listOf("sure", "it", "a"),
        "need" to listOf("to", "a", "help"),
        "want" to listOf("to", "a", "some"),
        "going" to listOf("to", "out", "home"),
        "just" to listOf("got", "wanted", "need"),
        "not" to listOf("sure", "now", "yet"),
        "will" to listOf("be", "do", "call"),
        "be" to listOf("there", "back", "able"),
        "call" to listOf("me", "you", "back"),
        "text" to listOf("me", "you", "back"),
        "send" to listOf("me", "it", "the"),
        "love" to listOf("you", "it", "this"),
        "miss" to listOf("you", "it"),
        "see" to listOf("you", "it"),
        "meet" to listOf("you", "at", "up"),
        "on" to listOf("the", "my", "a", "your"),
        "at" to listOf("the", "home", "work", "least"),
        "in" to listOf("the", "a", "my"),
        "with" to listOf("you", "me", "the"),
        "about" to listOf("it", "the", "you"),
        "from" to listOf("the", "my", "you"),
        "up" to listOf("to", "the", "with"),
        "out" to listOf("of", "with", "for"),
        "no" to listOf("problem", "way", "thanks"),
        "okay" to listOf("thanks", "sure", "then"),
        "sure" to listOf("thing", "thanks", "I"),
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

    fun correct(
        word: String,
        personalWords: Collection<String> = emptyList(),
        allowTwoEdit: Boolean = false,
    ): String? {
        if (word.equals("i", ignoreCase = true) && word == "i") return "I"
        if (word.length < 2) return null
        if (word.any { it.isDigit() || it == '@' || it == '/' || it == ':' || it == '_' }) return null
        val lower = word.lowercase()
        // Names, acronyms and identifiers must never be rewritten, even if they
        // collide with an expansion entry.
        if (word.drop(1).any { it.isUpperCase() }) return null
        if (lower in protectedWords) return null
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
        if (best != null) return matchCase(word, best)
        // The two-edit pass is deliberately limited to the space/punctuation
        // commit path. Running it for every typed prefix would scan the whole
        // 43k dictionary on the UI thread for no visible benefit.
        if (!allowTwoEdit || lower.length < 5) return null
        // Never rewrite a capitalised token as a two-edit guess: names and
        // products are the usual victims (Priya -> Prior, Nginx -> Nine).
        if (word.firstOrNull()?.isUpperCase() == true) return null
        for (candidate in dictionary) {
            if (candidate.first() != first) continue
            if (kotlin.math.abs(candidate.length - lower.length) > 1) continue
            val candidateRank = rank[candidate] ?: Int.MAX_VALUE
            if (candidateRank > MAX_RANK_FOR_TWO_EDIT) continue
            if (!twoEditsAway(lower, candidate)) continue
            // Require a close length and a matching first letter, already true;
            // additionally prefer candidates that share the final letter so a
            // plural does not collapse into an unrelated word (params -> parts).
            if (candidate.last() != lower.last()) continue
            if (candidateRank < bestRank) {
                bestRank = candidateRank
                best = candidate
            }
        }
        return best?.let { matchCase(word, it) }
    }

    /** True for at most two insertions, deletions, substitutions or transpositions. */
    private fun twoEditsAway(a: String, b: String): Boolean {
        if (a == b) return false
        if (kotlin.math.abs(a.length - b.length) > 2) return false
        var prev2 = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    previous[j + 1] + 1,
                    current[j] + 1,
                    previous[j] + cost,
                )
                if (i > 0 && j > 0 && a[i] == b[j - 1] && a[i - 1] == b[j]) {
                    current[j + 1] = minOf(current[j + 1], prev2[j - 1] + 1)
                }
            }
            prev2 = previous
            previous = current
        }
        return previous[b.length] == 2
    }

    fun isKnown(word: String, personalWords: Collection<String> = emptyList()): Boolean {
        if (word.isBlank()) return true
        val lower = word.lowercase()
        return known.contains(lower) || personalWords.any { it.equals(word, ignoreCase = true) }
    }

    internal fun oneEditAwayForTest(a: String, b: String): Boolean = oneEditAway(a, b)

    internal fun twoEditsAwayForTest(a: String, b: String): Boolean = twoEditsAway(a, b)

    /** True for one substitution, insertion, deletion or adjacent transposition. */
    private fun oneEditAway(a: String, b: String): Boolean {
        if (a == b) return false
        if (a.length == b.length) {
            var i = 0
            while (i < a.length && a[i] == b[i]) i++
            if (i == a.length) return false
            // One substitution: everything after the mismatch must match.
            if (i + 1 >= a.length || a.regionMatches(i + 1, b, i + 1, a.length - i - 1)) return true
            // One adjacent transposition: swap the pair and compare the rest.
            return a[i] == b[i + 1] && a[i + 1] == b[i] &&
                a.regionMatches(i + 2, b, i + 2, a.length - i - 2)
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
