package com.juan.snapmusic.data.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.QueueStatus

@Database(
    entities = [QueueEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(DatabaseConverters::class)
abstract class SnapMusicDatabase : RoomDatabase() {
    abstract fun dao(): SnapMusicDao
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
}
