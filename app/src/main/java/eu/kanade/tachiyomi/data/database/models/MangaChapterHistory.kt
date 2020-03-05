package eu.kanade.tachiyomi.data.database.models

import kotlinx.serialization.Serializable

/**
 * Object containing manga, chapter and history
 *
 * @param manga object containing manga
 * @param chapter object containing chater
 * @param history object containing history
 */
@Serializable
data class MangaChapterHistory(val manga: Manga, val chapter: Chapter, val history: History)
