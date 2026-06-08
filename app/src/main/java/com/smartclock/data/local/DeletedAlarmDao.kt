package com.smartclock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedAlarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeletedAlarmEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<DeletedAlarmEntity>): List<Long>

    @Query(
        """
        SELECT * FROM deleted_alarms
        WHERE userId = :userId
        ORDER BY deletedAt DESC
        """
    )
    fun observeByUser(userId: Long): Flow<List<DeletedAlarmEntity>>

    @Query("SELECT * FROM deleted_alarms WHERE trashId = :trashId LIMIT 1")
    suspend fun getById(trashId: Long): DeletedAlarmEntity?

    @Query("SELECT * FROM deleted_alarms WHERE trashId IN (:trashIds)")
    suspend fun getByIds(trashIds: List<Long>): List<DeletedAlarmEntity>

    @Query("DELETE FROM deleted_alarms WHERE trashId = :trashId")
    suspend fun deleteById(trashId: Long)

    @Query("DELETE FROM deleted_alarms WHERE trashId IN (:trashIds)")
    suspend fun deleteByIds(trashIds: List<Long>)

    @Query("DELETE FROM deleted_alarms WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("SELECT COUNT(*) FROM deleted_alarms WHERE userId = :userId")
    suspend fun countByUser(userId: Long): Int
}
