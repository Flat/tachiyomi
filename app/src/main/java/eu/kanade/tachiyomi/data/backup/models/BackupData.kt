package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.data.backup.serializer.CategoryWrapper
import eu.kanade.tachiyomi.data.backup.serializer.ChapterWrapper
import eu.kanade.tachiyomi.data.backup.serializer.HistoryWrapper
import eu.kanade.tachiyomi.data.backup.serializer.MangaWrapper
import eu.kanade.tachiyomi.data.backup.serializer.TrackWrapper
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val categories: List<CategoryWrapper>?,
    val chapters: List<List<ChapterWrapper>>?,
    val history: List<List<HistoryWrapper>>?,
    val manga: List<MangaWrapper>,
    val tracking: List<List<TrackWrapper>>?
)
