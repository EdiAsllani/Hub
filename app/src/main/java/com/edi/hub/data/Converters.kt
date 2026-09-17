package com.edi.hub.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** Dates are epoch days, event timestamps are epoch milliseconds. See [com.edi.hub.data.model.PantryItem]. */
class Converters {
    @TypeConverter fun toLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter fun fromLocalDate(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter fun toInstant(epochMilli: Long?): Instant? = epochMilli?.let(Instant::ofEpochMilli)

    @TypeConverter fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()
}
