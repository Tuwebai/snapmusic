package com.juan.snapmusic.data.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.DownloadStrategy
import com.juan.snapmusic.core.model.MediaKind
import com.juan.snapmusic.core.model.QueueStatus
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QueueEntity::class, HistoryEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(DatabaseConverters::class)
abstract class SnapMusicDatabase : RoomDatabase() {
    abstract fun dao(): SnapMusicDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN selectionKind TEXT NOT NULL DEFAULT 'AUDIO'")
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN selectionTargetContainer TEXT NOT NULL DEFAULT 'M4A'")
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN selectionTargetBitrateKbps INTEGER")
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN selectionTargetResolution TEXT")
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN selectionStrategy TEXT NOT NULL DEFAULT 'DIRECT'")
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN laneIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE queue_entries
                    SET selectionKind = CASE WHEN container = 'MP4' THEN 'VIDEO' ELSE 'AUDIO' END,
                        selectionTargetContainer = container,
                        selectionTargetBitrateKbps = CASE
                            WHEN container IN ('MP3', 'M4A') THEN CAST(
                                NULLIF(
                                    REPLACE(
                                        REPLACE(
                                            REPLACE(
                                                lower(variantLabel),
                                                'mp3 ',
                                                ''
                                            ),
                                            'm4a ',
                                            ''
                                        ),
                                        'kbps',
                                        ''
                                    ),
                                    ''
                                ) AS INTEGER
                            )
                            ELSE NULL
                        END,
                        selectionTargetResolution = CASE
                            WHEN container = 'MP4' THEN NULLIF(
                                TRIM(
                                    REPLACE(
                                        REPLACE(lower(variantLabel), 'mp4 ', ''),
                                        'directo',
                                        ''
                                    )
                                ),
                                ''
                            )
                            ELSE NULL
                        END,
                        selectionStrategy = CASE
                            WHEN requiresMux = 1 THEN 'MUX_VIDEO_AUDIO'
                            WHEN requiresTranscode = 1 THEN 'TRANSCODE_AUDIO'
                            ELSE 'DIRECT'
                        END
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN preferredSourceId TEXT")
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN sourceContainerHint TEXT")
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN sourceBitrateKbps INTEGER")
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN sourceHeight INTEGER")
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN allowMuxFallback INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE queue_entries ADD COLUMN allowTranscodeFallback INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE queue_entries
                    SET sourceContainerHint = CASE
                            WHEN selectionTargetContainer = 'M4A' THEN 'M4A'
                            WHEN selectionTargetContainer = 'MP4' THEN 'MPEG_4'
                            ELSE NULL
                        END,
                        sourceBitrateKbps = selectionTargetBitrateKbps,
                        sourceHeight = CASE
                            WHEN selectionTargetResolution IS NOT NULL AND instr(lower(selectionTargetResolution), 'p') > 0 THEN CAST(
                                NULLIF(
                                    trim(substr(lower(selectionTargetResolution), 1, instr(lower(selectionTargetResolution), 'p') - 1)),
                                    ''
                                ) AS INTEGER
                            )
                            ELSE NULL
                        END,
                        allowMuxFallback = CASE
                            WHEN selectionKind = 'VIDEO' AND selectionTargetContainer = 'MP4' THEN 1
                            ELSE 0
                        END,
                        allowTranscodeFallback = CASE
                            WHEN selectionKind = 'AUDIO' THEN 1
                            ELSE 0
                        END
                    """.trimIndent(),
                )
            }
        }
    }
}

class DatabaseConverters {
    @TypeConverter
    fun fromContainer(value: ContainerFormat): String = value.name

    @TypeConverter
    fun toContainer(value: String): ContainerFormat = ContainerFormat.valueOf(value)

    @TypeConverter
    fun fromStatus(value: QueueStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): QueueStatus = QueueStatus.valueOf(value)

    @TypeConverter
    fun fromMediaKind(value: MediaKind): String = value.name

    @TypeConverter
    fun toMediaKind(value: String): MediaKind = MediaKind.valueOf(value)

    @TypeConverter
    fun fromDownloadStrategy(value: DownloadStrategy): String = value.name

    @TypeConverter
    fun toDownloadStrategy(value: String): DownloadStrategy = DownloadStrategy.valueOf(value)
}
