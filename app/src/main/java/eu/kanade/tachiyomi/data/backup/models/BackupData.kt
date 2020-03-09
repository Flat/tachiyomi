package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.HistoryImpl
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    var categories: List<CategoryImpl> = emptyList(),
    var chapters: List<ChapterImpl> = emptyList(),
    var history: List<HistoryImpl> = emptyList(),
    var manga: List<MangaImpl> = emptyList(),
    var tracking: List<TrackImpl> = emptyList()
)
