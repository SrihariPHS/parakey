package com.parakey.keyboard.text

/**
 * A deliberately small, offline English suggestion engine. It is not a full
 * language model; it gives Gboard-style prefix suggestions from a compact
 * common-word list and high-confidence corrections for known misspellings.
 * Keep it cheap enough to run on every keystroke.
 */
object SuggestionEngine {

    private val commonWords = listOf(
        "a", "about", "after", "again", "all", "also", "am", "an", "and", "any",
        "are", "as", "at", "back", "be", "because", "been", "before", "being", "best",
        "better", "between", "both", "but", "by", "can", "come", "could", "day", "did",
        "do", "does", "done", "down", "each", "even", "every", "first", "for", "from",
        "get", "give", "go", "good", "great", "had", "has", "have", "he", "hello",
        "help", "her", "here", "him", "his", "home", "how", "I", "if", "in",
        "into", "is", "it", "its", "just", "know", "last", "like", "look", "make",
        "many", "me", "might", "more", "most", "much", "my", "need", "new", "next",
        "no", "not", "now", "of", "on", "once", "one", "only", "or", "other",
        "our", "out", "over", "own", "people", "please", "put", "really", "right", "said",
        "same", "say", "see", "she", "should", "so", "some", "still", "such", "take",
        "than", "that", "the", "their", "them", "then", "there", "these", "they", "think",
        "this", "those", "through", "time", "to", "too", "two", "up", "us", "use",
        "very", "want", "was", "way", "we", "well", "were", "what", "when", "where",
        "which", "while", "who", "why", "will", "with", "work", "would", "you", "your",
        "hello", "thanks", "thank", "today", "tomorrow", "morning", "evening", "night",
        "phone", "keyboard", "message", "email", "name", "test", "voice", "speech",
    ).distinct()

    private val typos = mapOf(
        "teh" to "the",
        "recieve" to "receive",
        "recieved" to "received",
        "definately" to "definitely",
        "adress" to "address",
        "seperate" to "separate",
        "occured" to "occurred",
        "untill" to "until",
        "wierd" to "weird",
        "freind" to "friend",
        "beleive" to "believe",
        "grammer" to "grammar",
        "tomorow" to "tomorrow",
        "becuase" to "because",
        "thier" to "their",
        "alot" to "a lot",
        "cant" to "can't",
        "dont" to "don't",
        "youre" to "you're",
        "theyre" to "they're",
        "wont" to "won't",
        "didnt" to "didn't",
        "doesnt" to "doesn't",
        "isnt" to "isn't",
        "wasnt" to "wasn't",
        "couldnt" to "couldn't",
        "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't",
    )

    fun suggestions(prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val lower = prefix.lowercase()
        val correction = correct(prefix)
        val starts = commonWords
            .asSequence()
            .filter { it.startsWith(lower) && it.lowercase() != lower }
            .take(3)
            .map { matchCase(prefix, it) }
            .toList()
        return (listOfNotNull(correction) + starts).distinct().take(3)
    }

    fun correct(word: String): String? {
        if (word.length < 2) return null
        val lower = word.lowercase()
        val replacement = typos[lower] ?: return null
        return matchCase(word, replacement)
    }

    private fun matchCase(original: String, replacement: String): String {
        if (original.firstOrNull()?.isUpperCase() == true) {
            return replacement.replaceFirstChar { it.uppercaseChar() }
        }
        return replacement
    }

    fun isLikelyCompleteWord(word: String): Boolean =
        word.length >= 2 && commonWords.any { it.equals(word, ignoreCase = true) }
}
