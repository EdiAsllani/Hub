package com.edi.hub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.edi.hub.data.model.Trip

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Query("SELECT * FROM trip WHERE id = :id")
    suspend fun find(id: Long): Trip?
}
