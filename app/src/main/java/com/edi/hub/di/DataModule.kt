package com.edi.hub.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.edi.hub.data.HubDatabase
import com.edi.hub.data.dao.DeadlineDao
import com.edi.hub.data.dao.PantryDao
import com.edi.hub.data.dao.ProductDao
import com.edi.hub.data.dao.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HubDatabase =
        Room.databaseBuilder(context, HubDatabase::class.java, HubDatabase.NAME).build()

    @Provides fun productDao(db: HubDatabase): ProductDao = db.productDao()

    @Provides fun pantryDao(db: HubDatabase): PantryDao = db.pantryDao()

    @Provides fun tripDao(db: HubDatabase): TripDao = db.tripDao()

    @Provides fun deadlineDao(db: HubDatabase): DeadlineDao = db.deadlineDao()

    /** Backup folder URI, currency and last-backup time. Three values; SharedPreferences is enough. */
    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("hub", Context.MODE_PRIVATE)
}
