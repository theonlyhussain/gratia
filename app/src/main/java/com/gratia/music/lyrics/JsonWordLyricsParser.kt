package com.gratia.music.lyrics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for JSON formatted word-level synced lyrics.
 */
object JsonWordLyricsParser {

    /**
     * Parses JSON string into a list of LyricLine objects.
     */
    fun parse(input: String): List<LyricLine> {
        val list = mutableListOf<LyricLine>()
        val trimmed = input.trim()
        val jsonArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            when {
                root.has("lyrics") -> root.getJSONArray("lyrics")
                root.has("lines") -> root.getJSONArray("lines")
                else -> throw IllegalArgumentException("No lyrics or lines array found in JSON")
            }
        }

        for (i in 0 until jsonArray.length()) {
            val lineObj = jsonArray.getJSONObject(i)
            val lineText = lineObj.optString("line", lineObj.optString("text", ""))

            val lineStartMs = getMsValue(lineObj, "start_time", "start", "startMs", "start_ms") ?: 0L
            val lineEndMs = getMsValue(lineObj, "end_time", "end", "endMs", "end_ms")

            val wordsArray = lineObj.optJSONArray("words")
            val words = mutableListOf<LyricWord>()
            if (wordsArray != null) {
                for (j in 0 until wordsArray.length()) {
                    val wordObj = wordsArray.getJSONObject(j)
                    val wordText = wordObj.optString("word", wordObj.optString("text", ""))
                    val wordStartMs = getMsValue(wordObj, "start_time", "start", "startMs", "start_ms") ?: lineStartMs
                    val wordEndMs = getMsValue(wordObj, "end_time", "end", "endMs", "end_ms") ?: (wordStartMs + 200L)
                    if (wordText.isNotEmpty()) {
                        words.add(LyricWord(text = wordText, startMs = wordStartMs, endMs = wordEndMs))
                    }
                }
            }

            list.add(LyricLine(text = lineText, startMs = lineStartMs, endMs = lineEndMs, words = words))
        }

        return list.sortedBy { it.startMs }
    }

    private fun getMsValue(obj: JSONObject, primaryKey: String, vararg aliases: String): Long? {
        val keys = listOf(primaryKey) + aliases.toList()
        for (key in keys) {
            if (obj.has(key)) {
                val value = obj.get(key)
                if (value is Number) {
                    val doubleVal = value.toDouble()
                    return if (key.contains("ms", ignoreCase = true)) {
                        doubleVal.toLong()
                    } else {
                        // Check if it looks like seconds (usually smaller numbers)
                        if (doubleVal < 100000.0) {
                            (doubleVal * 1000.0).toLong()
                        } else {
                            doubleVal.toLong()
                        }
                    }
                }
            }
        }
        return null
    }
}
