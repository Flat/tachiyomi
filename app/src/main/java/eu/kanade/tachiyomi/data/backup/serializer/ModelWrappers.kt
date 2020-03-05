package eu.kanade.tachiyomi.data.backup.serializer

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import kotlinx.serialization.Serializable

@Serializable
class CategoryWrapper(val category: Category)

@Serializable
class ChapterWrapper(val chapter: Chapter)

@Serializable
class HistoryWrapper(val history: History)

@Serializable
class MangaWrapper(val manga: Manga)

@Serializable
class TrackWrapper(val track: Track)
