package com.vibecoded.radioplayer.util

data class ParsedTitle(val artist: String?, val title: String)

object IcyTitleParser {
    private val separators = listOf(" - ", " – ", " — ")

    /** ICY StreamTitle metadata is usually formatted as "Artist - Title". This splits on the
     * first plausible separator; if none is found, the whole string is treated as the title. */
    fun parse(rawTitle: String): ParsedTitle {
        val clean = rawTitle.trim()
        for (sep in separators) {
            if (clean.contains(sep)) {
                val parts = clean.split(sep, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    return ParsedTitle(artist = parts[0].trim(), title = parts[1].trim())
                }
            }
        }
        return ParsedTitle(artist = null, title = clean)
    }
}
