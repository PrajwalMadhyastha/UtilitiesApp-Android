package com.prajwal.utilities.tools.challantracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VehicleEntity::class, ChallanEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ChallanDatabase : RoomDatabase() {
    abstract fun challanDao(): ChallanDao

    companion object {
        @Volatile
        private var INSTANCE: ChallanDatabase? = null

        fun getDatabase(context: Context): ChallanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChallanDatabase::class.java,
                    "challan_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
