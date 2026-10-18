package com.prajwal.utilities.tools.wealthtracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WealthDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: AssetSnapshotEntity)

    @Update
    suspend fun updateSnapshot(snapshot: AssetSnapshotEntity)

    @Delete
    suspend fun deleteSnapshot(snapshot: AssetSnapshotEntity)

    @Query("SELECT * FROM asset_snapshots ORDER BY recordedAt DESC")
    fun getAllSnapshots(): Flow<List<AssetSnapshotEntity>>

    @Query("SELECT * FROM asset_snapshots ORDER BY recordedAt DESC LIMIT 1")
    suspend fun getLatestSnapshot(): AssetSnapshotEntity?

    @Query("SELECT * FROM asset_snapshots ORDER BY recordedAt ASC")
    fun getAllSnapshotsAscending(): Flow<List<AssetSnapshotEntity>>
}
