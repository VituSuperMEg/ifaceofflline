package com.example.iface_offilne.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.iface_offilne.models.FacesModel

@Dao
interface FaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(face: FaceEntity)

    @Query("SELECT * FROM faces WHERE funcionarioId = :funcionarioId LIMIT 1")
    suspend fun getByFuncionarioId(funcionarioId: String): FaceEntity?

    @Query("SELECT * FROM faces WHERE synced = 0")
    suspend fun getPendingSync(): List<FaceEntity>

    @Update
    suspend fun update(face: FaceEntity)
    
    @Query("DELETE FROM faces WHERE funcionarioId = :funcionarioId")
    suspend fun deleteByFuncionarioId(funcionarioId: String)
    
    @Delete
    suspend fun delete(face: FaceEntity)
}
