package com.aloneagle.tracky.data.local

import androidx.room.TypeConverter

class TrackyConverters {
    @TypeConverter
    fun fromStringList(values: List<String>?): String = values.orEmpty().joinToString("|")

    @TypeConverter
    fun toStringList(value: String?): List<String> = value
        ?.takeIf { it.isNotBlank() }
        ?.split("|")
        .orEmpty()
}
