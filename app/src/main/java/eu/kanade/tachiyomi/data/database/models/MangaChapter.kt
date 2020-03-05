package eu.kanade.tachiyomi.data.database.models

import kotlinx.serialization.Serializable

@Serializable
class MangaChapter(val manga: Manga, val chapter: Chapter)
