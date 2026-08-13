package com.prajwal.utilities.tools.wealthtracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AssetSnapshotEntity::class, HoldingEntity::class, TransactionEntity::class, DividendEventEntity::class],
    version = 10,
    // FIX #6: exportSchema = true lets Room generate JSON schema files under app/schemas/.
    // These are required for MigrationTestHelper — without them, every DB schema change
    // is unverifiable and risks silent data loss in production.
    exportSchema = true
)
abstract class WealthDatabase : RoomDatabase() {

    abstract fun wealthDao(): WealthDao
    abstract fun holdingsDao(): HoldingsDao

    companion object {
        @Volatile
        private var INSTANCE: WealthDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `holdings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `assetClass` TEXT NOT NULL, 
                        `instrumentType` TEXT NOT NULL, 
                        `identifier` TEXT NOT NULL, 
                        `exchange` TEXT, 
                        `unitsHeld` REAL NOT NULL, 
                        `investedAmount` REAL NOT NULL, 
                        `latestPrice` REAL NOT NULL, 
                        `lastUpdatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `holdings` ADD COLUMN `name` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `holdings` ADD COLUMN `previousClosePrice` REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create transactions table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `holdingId` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `units` REAL NOT NULL,
                        `pricePerUnit` REAL NOT NULL,
                        `type` TEXT NOT NULL,
                        FOREIGN KEY(`holdingId`) REFERENCES `holdings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                
                // 2. Create index
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_holdingId` ON `transactions` (`holdingId`)")
                
                // 3. Day Zero insertion
                val currentTime = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT INTO `transactions` (`holdingId`, `timestamp`, `units`, `pricePerUnit`, `type`)
                    SELECT `id`, $currentTime, `unitsHeld`, 
                    CASE WHEN `unitsHeld` > 0 THEN `investedAmount` / `unitsHeld` ELSE 0 END, 
                    'BUY'
                    FROM `holdings`
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `holdings` ADD COLUMN `isManual` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `asset_snapshots` ADD COLUMN `retirementInvested` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `asset_snapshots` ADD COLUMN `retirementCurrent` REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `holdings` ADD COLUMN `expenseRatio` REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dividend_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `symbol` TEXT NOT NULL,
                        `exDate` INTEGER NOT NULL,
                        `amount` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dividend_events_symbol` ON `dividend_events` (`symbol`)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add a unique index on (symbol, exDate) to make upserts atomic per dividend event.
                // This prevents duplicate rows if a sync is interrupted and retried.
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_dividend_events_symbol_exDate` ON `dividend_events` (`symbol`, `exDate`)"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the isPassive column
                db.execSQL("ALTER TABLE `holdings` ADD COLUMN `isPassive` INTEGER NOT NULL DEFAULT 0")
                
                // Auto-categorize existing holdings based on name heuristics
                db.execSQL(
                    """
                    UPDATE `holdings`
                    SET `isPassive` = 1
                    WHERE `name` LIKE '%Index%'
                       OR `name` LIKE '%ETF%'
                       OR `name` LIKE '%Nifty%'
                       OR `name` LIKE '%Sensex%'
                       OR `name` LIKE '%Equal Weight%'
                       OR `name` LIKE '%Next 50%'
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): WealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WealthDatabase::class.java,
                    "wealth_tracker_database"
                )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, 
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
                )
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
