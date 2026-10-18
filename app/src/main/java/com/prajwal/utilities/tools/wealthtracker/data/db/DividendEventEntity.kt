package com.prajwal.utilities.tools.wealthtracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dividend_events",
    indices = [
        Index(value = ["symbol"]),
        Index(value = ["symbol", "exDate"], unique = true)
    ]
)
data class DividendEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val exDate: Long,
    val amount: Double
)
