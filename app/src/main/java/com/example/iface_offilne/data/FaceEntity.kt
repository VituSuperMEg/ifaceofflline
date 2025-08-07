package com.example.iface_offilne.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "faces")
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val funcionarioId: String,
    val embedding: String,
    val synced: Boolean = false
)