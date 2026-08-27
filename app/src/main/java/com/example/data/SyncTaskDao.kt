package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncTaskDao {
    @Query("SELECT * FROM sync_tasks")
    fun getAllSyncTasks(): Flow<List<SyncTaskEntity>>

    @Query("SELECT * FROM sync_tasks")
    suspend fun getAllSyncTasksDirect(): List<SyncTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncTask(task: SyncTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncTasks(tasks: List<SyncTaskEntity>)

    @Query("SELECT * FROM sync_tasks WHERE taskId = :id")
    suspend fun getSyncTask(id: String): SyncTaskEntity?
}
