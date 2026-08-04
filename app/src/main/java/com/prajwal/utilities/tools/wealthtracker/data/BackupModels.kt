package com.prajwal.utilities.tools.wealthtracker.data

import com.prajwal.utilities.tools.wealthtracker.data.db.AssetSnapshotEntity
import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WealthBackup(
    val holdings: List<HoldingEntity>,
    val snapshots: List<AssetSnapshotEntity>,
    val transactions: List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity> = emptyList()
)
