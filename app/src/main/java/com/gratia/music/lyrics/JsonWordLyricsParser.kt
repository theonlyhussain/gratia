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
                root.has("content") -> root.getJSONArray("content")
                else -> throw IllegalArgumentException("No lyrics, lines, or content array found in JSON")
            }
        }

        for (i in 0 until jsonArray.length()) {
            val lineObj = jsonArray.getJSONObject(i)
            
            // Lyrically API uses 'timestamp' and 'endtime' on lines and words
            val lineStartMs = getMsValue(lineObj, "timestamp", "start_time", "start", "startMs", "start_ms") ?: 0L
            val lineEndMs = getMsValue(lineObj, "endtime", "end_time", "end", "endMs", "end_ms")

            // Lyrically uses 'text' array for words, others use 'words'
            val wordsArray = lineObj.optJSONArray("words") ?: lineObj.optJSONArray("text")
            val words = mutableListOf<LyricWord>()
            val builtLineText = StringBuilder()
            
            if (wordsArray != null) {
                for (j in 0 until wordsArray.length()) {
                    val wordObj = wordsArray.getJSONObject(j)
                    val wordText = wordObj.optString("word", wordObj.optString("text", ""))
                    val wordStartMs = getMsValue(wordObj, "timestamp", "start_time", "start", "startMs", "start_ms") ?: lineStartMs
                    val wordEndMs = getMsValue(wordObj, "endtime", "end_time", "end", "endMs", "end_ms") ?: (wordStartMs + 200L)
                    if (wordText.isNotEmpty()) {
                        words.add(LyricWord(text = wordText, startMs = wordStartMs, endMs = wordEndMs))
                        builtLineText.append(wordText)
                    }
                }
            }

            var lineText = lineObj.optString("line")
            if (lineText.isEmpty() && !lineObj.has("line") && !lineObj.has("text")) {
                lineText = builtLineText.toString().trim()
            } else if (lineText.isEmpty()) {
                lineText = lineObj.optString("text", builtLineText.toString().trim())
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
