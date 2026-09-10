package com.mati.tradenotify.data.db

import androidx.room.TypeConverter

class Converters {

    /**
     * Keyword lists are stored newline-joined. Keywords are single-line by nature, so this avoids
     * pulling in a JSON dependency just to persist a handful of strings.
     */
    @TypeConverter
    fun stringListToDb(value: List<String>): String = value.joinToString("\n")

    @TypeConverter
    fun stringListFromDb(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\n").filter { it.isNotBlank() }

    @TypeConverter
    fun matchModeToDb(value: MatchMode): String = value.name

    @TypeConverter
    fun matchModeFromDb(value: String): MatchMode =
        runCatching { MatchMode.valueOf(value) }.getOrDefault(MatchMode.CONTAINS)

    @TypeConverter
    fun outcomeToDb(value: EventOutcome): String = value.name

    @TypeConverter
    fun outcomeFromDb(value: String): EventOutcome =
        runCatching { EventOutcome.valueOf(value) }.getOrDefault(EventOutcome.NO_MATCH)
}
