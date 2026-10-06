package dev.jdtech.jellyfin.database

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.DeleteTable
import androidx.room3.RoomDatabase
import androidx.room3.migration.AutoMigrationSpec
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import dev.jdtech.jellyfin.models.FindroidEpisodeDto
import dev.jdtech.jellyfin.models.FindroidMediaStreamDto
import dev.jdtech.jellyfin.models.FindroidMovieDto
import dev.jdtech.jellyfin.models.FindroidSeasonDto
import dev.jdtech.jellyfin.models.FindroidSegmentDto
import dev.jdtech.jellyfin.models.FindroidShowDto
import dev.jdtech.jellyfin.models.FindroidSourceDto
import dev.jdtech.jellyfin.models.FindroidTrickplayInfoDto
import dev.jdtech.jellyfin.models.FindroidUserDataDto
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerAddress
import dev.jdtech.jellyfin.models.User

@Database(
    entities =
        [
            Server::class,
            ServerAddress::class,
            User::class,
            FindroidMovieDto::class,
            FindroidShowDto::class,
            FindroidSeasonDto::class,
            FindroidEpisodeDto::class,
            FindroidSourceDto::class,
            FindroidMediaStreamDto::class,
            FindroidUserDataDto::class,
            FindroidTrickplayInfoDto::class,
            FindroidSegmentDto::class,
        ],
    version = 9,
    autoMigrations =
        [
            AutoMigration(from = 2, to = 3),
            AutoMigration(from = 3, to = 4),
            AutoMigration(from = 4, to = 5, spec = ServerDatabase.TrickplayMigration::class),
            AutoMigration(from = 5, to = 6, spec = ServerDatabase.IntrosMigration::class),
            AutoMigration(from = 7, to = 8),
        ],
)
@ColumnTypeConverters(Converters::class)
abstract class ServerDatabase : RoomDatabase() {
    abstract fun getServerDatabaseDao(): ServerDatabaseDao

    @DeleteTable(tableName = "trickPlayManifests") class TrickplayMigration : AutoMigrationSpec

    @DeleteTable(tableName = "intros") class IntrosMigration : AutoMigrationSpec
}

val MIGRATION_6_7 =
    object : Migration(startVersion = 6, endVersion = 7) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE segments")
            connection.execSQL(
                "CREATE TABLE segments (`itemId` TEXT NOT NULL, `type` TEXT NOT NULL, `startTicks` INTEGER NOT NULL, `endTicks` INTEGER NOT NULL, PRIMARY KEY(`itemId`, `type`), FOREIGN KEY(`itemId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
        }
    }

/**
 * 把下载任务的关联键从系统 DownloadManager 的 Long id 换成 WorkManager 的 UUID 字符串。
 *
 * SQLite 无法直接修改列类型，只能重建两张表：已下载完成的记录需要保留（用户的数据），
 * 进行中的任务记录则置空——旧任务由系统 DownloadManager 托管，改造后已无法继续。
 */
val MIGRATION_8_9 =
    object : Migration(startVersion = 8, endVersion = 9) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `sources_new` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `path` TEXT NOT NULL, `downloadTaskId` TEXT, PRIMARY KEY(`id`))"
            )
            connection.execSQL(
                "INSERT INTO `sources_new` (`id`, `itemId`, `name`, `type`, `path`) SELECT `id`, `itemId`, `name`, `type`, `path` FROM `sources`"
            )
            connection.execSQL("DROP TABLE `sources`")
            connection.execSQL("ALTER TABLE `sources_new` RENAME TO `sources`")

            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `mediastreams_new` (`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `title` TEXT NOT NULL, `displayTitle` TEXT, `language` TEXT NOT NULL, `type` TEXT NOT NULL, `codec` TEXT NOT NULL, `isExternal` INTEGER NOT NULL, `path` TEXT NOT NULL, `channelLayout` TEXT, `videoRangeType` TEXT, `height` INTEGER, `width` INTEGER, `videoDoViTitle` TEXT, `downloadTaskId` TEXT, PRIMARY KEY(`id`))"
            )
            connection.execSQL(
                "INSERT INTO `mediastreams_new` (`id`, `sourceId`, `title`, `displayTitle`, `language`, `type`, `codec`, `isExternal`, `path`, `channelLayout`, `videoRangeType`, `height`, `width`, `videoDoViTitle`) SELECT `id`, `sourceId`, `title`, `displayTitle`, `language`, `type`, `codec`, `isExternal`, `path`, `channelLayout`, `videoRangeType`, `height`, `width`, `videoDoViTitle` FROM `mediastreams`"
            )
            connection.execSQL("DROP TABLE `mediastreams`")
            connection.execSQL("ALTER TABLE `mediastreams_new` RENAME TO `mediastreams`")
        }
    }
