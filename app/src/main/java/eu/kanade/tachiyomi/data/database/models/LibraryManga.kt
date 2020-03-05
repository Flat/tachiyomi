package eu.kanade.tachiyomi.data.database.models

import kotlinx.serialization.Serializable

@Serializable
class LibraryManga : MangaImpl() {

    var unread: Int = 0

    var category: Int = 0
}
