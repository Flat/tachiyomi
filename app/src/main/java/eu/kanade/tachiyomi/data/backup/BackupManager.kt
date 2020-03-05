package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.registerTypeAdapter
import com.github.salomonbrys.kotson.registerTypeHierarchyAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CATEGORY
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CATEGORY_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CHAPTER
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_CHAPTER_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_HISTORY
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_HISTORY_MASK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_TRACK
import eu.kanade.tachiyomi.data.backup.BackupCreateService.Companion.BACKUP_TRACK_MASK
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.Backup.CURRENT_VERSION
import eu.kanade.tachiyomi.data.backup.models.BackupData
import eu.kanade.tachiyomi.data.backup.models.DHistory
import eu.kanade.tachiyomi.data.backup.serializer.CategoryTypeAdapter
import eu.kanade.tachiyomi.data.backup.serializer.CategoryWrapper
import eu.kanade.tachiyomi.data.backup.serializer.ChapterTypeAdapter
import eu.kanade.tachiyomi.data.backup.serializer.ChapterWrapper
import eu.kanade.tachiyomi.data.backup.serializer.HistoryTypeAdapter
import eu.kanade.tachiyomi.data.backup.serializer.HistoryWrapper
import eu.kanade.tachiyomi.data.backup.serializer.MangaTypeAdapter
import eu.kanade.tachiyomi.data.backup.serializer.MangaWrapper
import eu.kanade.tachiyomi.data.backup.serializer.TrackTypeAdapter
import eu.kanade.tachiyomi.data.backup.serializer.TrackWrapper
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.system.sendLocalBroadcast
import kotlin.math.max
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import rx.Observable
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

class BackupManager(val context: Context, version: Int = CURRENT_VERSION) {

    /**
     * Database.
     */
    internal val databaseHelper: DatabaseHelper by injectLazy()

    /**
     * Source manager.
     */
    internal val sourceManager: SourceManager by injectLazy()

    /**
     * Tracking manager
     */
    internal val trackManager: TrackManager by injectLazy()

    /**
     * Version of parser
     */
    var version: Int = version
        private set

    /**
     * Json Parser
     */
    var parser: Gson = initParser()

    /**
     * Preferences
     */
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Set version of parser
     *
     * @param version version of parser
     */
    internal fun setVersion(version: Int) {
        this.version = version
        parser = initParser()
    }

    private fun initParser(): Gson = when (version) {
        1 -> GsonBuilder().create()
        2 -> GsonBuilder()
            .registerTypeAdapter<MangaImpl>(MangaTypeAdapter.build())
            .registerTypeHierarchyAdapter<ChapterImpl>(ChapterTypeAdapter.build())
            .registerTypeAdapter<CategoryImpl>(CategoryTypeAdapter.build())
            .registerTypeAdapter<DHistory>(HistoryTypeAdapter.build())
            .registerTypeHierarchyAdapter<TrackImpl>(TrackTypeAdapter.build())
            .create()
        else -> throw Exception("Json version unknown")
    }

    /**
     * Create backup Json file from database
     *
     * @param uri path of Uri
     * @param isJob backup called from job
     */
    fun createBackup(uri: Uri, flags: Int, isJob: Boolean) {
        val json = Json(JsonConfiguration.Default)
        // Create Manga List
        var mangaEntries = mutableListOf<Manga>()

        // Create Category List
        var categoryEntries = mutableListOf<Category>()

        val chapters = mutableListOf<List<Chapter>>()
        val tracking = mutableListOf<List<Track>>()
        val history = mutableListOf<List<History>>()

        val prefs = preferences.getAll()

        databaseHelper.inTransaction {
            // Get manga from database
            mangaEntries = getFavoriteManga() as MutableList<Manga>

            mangaEntries.forEach { manga ->
                if (flags and BACKUP_CHAPTER_MASK == BACKUP_CHAPTER) {
                    // Backup all the chapters
                    val chapterList = databaseHelper.getChapters(manga).executeAsBlocking()
                    if (chapters.isNotEmpty()) {
                        chapters.add(chapterList)
                    }
                }
                if (flags and BACKUP_TRACK_MASK == BACKUP_TRACK) {
                    val tracks = databaseHelper.getTracks(manga).executeAsBlocking()
                    if (tracks.isNotEmpty()) {
                        tracking.add(tracks)
                    }
                }

                if (flags and BACKUP_HISTORY_MASK == BACKUP_HISTORY) {
                    val historyForManga =
                        databaseHelper.getHistoryByMangaId(manga.id!!).executeAsBlocking()
                    if (historyForManga.isNotEmpty()) {
                        history.add(historyForManga)
                    }
                }
            }

            // Backup categories
            if ((flags and BACKUP_CATEGORY_MASK) == BACKUP_CATEGORY) {
                categoryEntries = databaseHelper.getCategories().executeAsBlocking()
            }
        }

        val data = BackupData(manga = mangaEntries.map { manga -> MangaWrapper(manga) },
            categories = categoryEntries.map { category -> CategoryWrapper(category) },
            chapters = chapters.map { list -> list.map { chapter -> ChapterWrapper(chapter) } },
            history = history.map { list -> list.map { history -> HistoryWrapper(history) } },
            tracking = tracking.map { list -> list.map { track -> TrackWrapper(track) } })
        val jsonData = json.stringify(BackupData.serializer(), data)
        try {
            // When BackupCreatorJob
            if (isJob) {
                // Get dir of file and create
                var dir = UniFile.fromUri(context, uri)
                dir = dir.createDirectory("automatic")

                // Delete older backups
                val numberOfBackups = numberOfBackups()
                val backupRegex = Regex("""tachiyomi_\d+-\d+-\d+_\d+-\d+.json""")
                dir.listFiles { _, filename -> backupRegex.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(numberOfBackups - 1)
                    .forEach { it.delete() }

                // Create new file to place backup
                val newFile = dir.createFile(Backup.getDefaultFilename())
                    ?: throw Exception("Couldn't create backup file")

                newFile.openOutputStream().bufferedWriter().use {
                    json.stringify(BackupData.serializer(), data)
                }
            } else {
                val file = UniFile.fromUri(context, uri)
                    ?: throw Exception("Couldn't create backup file")
                file.openOutputStream().bufferedWriter().use {
                    json.stringify(BackupData.serializer(), data)
                }

                // Show completed dialog
                val intent = Intent(BackupConst.INTENT_FILTER).apply {
                    putExtra(BackupConst.ACTION, BackupConst.ACTION_BACKUP_COMPLETED_DIALOG)
                    putExtra(BackupConst.EXTRA_URI, file.uri.toString())
                }
                context.sendLocalBroadcast(intent)
            }
        } catch (e: Exception) {
            Timber.e(e)
            if (!isJob) {
                // Show error dialog
                val intent = Intent(BackupConst.INTENT_FILTER).apply {
                    putExtra(BackupConst.ACTION, BackupConst.ACTION_ERROR_BACKUP_DIALOG)
                    putExtra(BackupConst.EXTRA_ERROR_MESSAGE, e.message)
                }
                context.sendLocalBroadcast(intent)
            }
        }
    }

/*    *//**
     * Convert a manga to Json
     *
     * @param manga manga that gets converted
     * @return [JsonElement] containing manga information
     *//*
    internal fun backupMangaObject(manga: Manga, options: Int): Manga {
        // Entry for this manga
        val entry = JsonObject()

        // Backup manga fields
        entry[MANGA] = parser.toJsonTree(manga)

        // Check if user wants chapter information in backup
        if (options and BACKUP_CHAPTER_MASK == BACKUP_CHAPTER) {
            // Backup all the chapters
            val chapters = databaseHelper.getChapters(manga).executeAsBlocking()
            if (chapters.isNotEmpty()) {
                val chaptersJson = parser.toJsonTree(chapters)
                if (chaptersJson.asJsonArray.size() > 0) {
                    entry[CHAPTERS] = chaptersJson
                }
            }
        }

        // Check if user wants category information in backup
        if (options and BACKUP_CATEGORY_MASK == BACKUP_CATEGORY) {
            // Backup categories for this manga
            val categoriesForManga = databaseHelper.getCategoriesForManga(manga).executeAsBlocking()
            if (categoriesForManga.isNotEmpty()) {
                val categoriesNames = categoriesForManga.map { it.name }
                entry[CATEGORIES] = parser.toJsonTree(categoriesNames)
            }
        }

        // Check if user wants track information in backup
        if (options and BACKUP_TRACK_MASK == BACKUP_TRACK) {
            val tracks = databaseHelper.getTracks(manga).executeAsBlocking()
            if (tracks.isNotEmpty()) {
                entry[TRACK] = parser.toJsonTree(tracks)
            }
        }

        // Check if user wants history information in backup
        if (options and BACKUP_HISTORY_MASK == BACKUP_HISTORY) {
            val historyForManga = databaseHelper.getHistoryByMangaId(manga.id!!).executeAsBlocking()
            if (historyForManga.isNotEmpty()) {
                val historyData = historyForManga.mapNotNull { history ->
                    val url = databaseHelper.getChapter(history.chapter_id).executeAsBlocking()?.url
                    url?.let { DHistory(url, history.last_read) }
                }
                val historyJson = parser.toJsonTree(historyData)
                if (historyJson.asJsonArray.size() > 0) {
                    entry[HISTORY] = historyJson
                }
            }
        }

        return entry
    }*/

    fun restoreMangaNoFetch(manga: Manga, dbManga: Manga) {
        manga.id = dbManga.id
        manga.copyFrom(dbManga)
        manga.favorite = true
        insertManga(manga)
    }

    /**
     * [Observable] that fetches manga information
     *
     * @param source source of manga
     * @param manga manga that needs updating
     * @return [Observable] that contains manga
     */
    fun restoreMangaFetchObservable(source: Source, manga: Manga): Observable<Manga> {
        return source.fetchMangaDetails(manga)
            .map { networkManga ->
                manga.copyFrom(networkManga)
                manga.favorite = true
                manga.initialized = true
                manga.id = insertManga(manga)
                manga
            }
    }

    /**
     * [Observable] that fetches chapter information
     *
     * @param source source of manga
     * @param manga manga that needs updating
     * @return [Observable] that contains manga
     */
    fun restoreChapterFetchObservable(
        source: Source,
        manga: Manga,
        chapters: List<Chapter>
    ): Observable<Pair<List<Chapter>, List<Chapter>>> {
        return source.fetchChapterList(manga)
            .map { syncChaptersWithSource(databaseHelper, it, manga, source) }
            .doOnNext {
                if (it.first.isNotEmpty()) {
                    chapters.forEach { it.manga_id = manga.id }
                    insertChapters(chapters)
                }
            }
    }

    /**
     * Restore the categories from Json
     *
     * @param jsonCategories array containing categories
     */
    internal fun restoreCategories(jsonCategories: JsonArray) {
        // Get categories from file and from db
        val dbCategories = databaseHelper.getCategories().executeAsBlocking()
        val backupCategories = parser.fromJson<List<CategoryImpl>>(jsonCategories)

        // Iterate over them
        backupCategories.forEach { category ->
            // Used to know if the category is already in the db
            var found = false
            for (dbCategory in dbCategories) {
                // If the category is already in the db, assign the id to the file's category
                // and do nothing
                if (category.nameLower == dbCategory.nameLower) {
                    category.id = dbCategory.id
                    found = true
                    break
                }
            }
            // If the category isn't in the db, remove the id and insert a new category
            // Store the inserted id in the category
            if (!found) {
                // Let the db assign the id
                category.id = null
                val result = databaseHelper.insertCategory(category).executeAsBlocking()
                category.id = result.insertedId()?.toInt()
            }
        }
    }

    /**
     * Restores the categories a manga is in.
     *
     * @param manga the manga whose categories have to be restored.
     * @param categories the categories to restore.
     */
    internal fun restoreCategoriesForManga(manga: Manga, categories: List<String>) {
        val dbCategories = databaseHelper.getCategories().executeAsBlocking()
        val mangaCategoriesToUpdate = ArrayList<MangaCategory>()
        for (backupCategoryStr in categories) {
            for (dbCategory in dbCategories) {
                if (backupCategoryStr.toLowerCase() == dbCategory.nameLower) {
                    mangaCategoriesToUpdate.add(MangaCategory.create(manga, dbCategory))
                    break
                }
            }
        }

        // Update database
        if (mangaCategoriesToUpdate.isNotEmpty()) {
            val mangaAsList = ArrayList<Manga>()
            mangaAsList.add(manga)
            databaseHelper.deleteOldMangasCategories(mangaAsList).executeAsBlocking()
            databaseHelper.insertMangasCategories(mangaCategoriesToUpdate).executeAsBlocking()
        }
    }

    /**
     * Restore history from Json
     *
     * @param history list containing history to be restored
     */
    internal fun restoreHistoryForManga(history: List<DHistory>) {
        // List containing history to be updated
        val historyToBeUpdated = ArrayList<History>()
        for ((url, lastRead) in history) {
            val dbHistory = databaseHelper.getHistoryByChapterUrl(url).executeAsBlocking()
            // Check if history already in database and update
            if (dbHistory != null) {
                dbHistory.apply {
                    last_read = max(lastRead, dbHistory.last_read)
                }
                historyToBeUpdated.add(dbHistory)
            } else {
                // If not in database create
                databaseHelper.getChapter(url).executeAsBlocking()?.let {
                    val historyToAdd = History.create(it).apply {
                        last_read = lastRead
                    }
                    historyToBeUpdated.add(historyToAdd)
                }
            }
        }
        databaseHelper.updateHistoryLastRead(historyToBeUpdated).executeAsBlocking()
    }

    /**
     * Restores the sync of a manga.
     *
     * @param manga the manga whose sync have to be restored.
     * @param tracks the track list to restore.
     */
    internal fun restoreTrackForManga(manga: Manga, tracks: List<Track>) {
        // Fix foreign keys with the current manga id
        tracks.map { it.manga_id = manga.id!! }

        // Get tracks from database
        val dbTracks = databaseHelper.getTracks(manga).executeAsBlocking()
        val trackToUpdate = ArrayList<Track>()

        for (track in tracks) {
            val service = trackManager.getService(track.sync_id)
            if (service != null && service.isLogged) {
                var isInDatabase = false
                for (dbTrack in dbTracks) {
                    if (track.sync_id == dbTrack.sync_id) {
                        // The sync is already in the db, only update its fields
                        if (track.media_id != dbTrack.media_id) {
                            dbTrack.media_id = track.media_id
                        }
                        if (track.library_id != dbTrack.library_id) {
                            dbTrack.library_id = track.library_id
                        }
                        dbTrack.last_chapter_read =
                            max(dbTrack.last_chapter_read, track.last_chapter_read)
                        isInDatabase = true
                        trackToUpdate.add(dbTrack)
                        break
                    }
                }
                if (!isInDatabase) {
                    // Insert new sync. Let the db assign the id
                    track.id = null
                    trackToUpdate.add(track)
                }
            }
        }
        // Update database
        if (trackToUpdate.isNotEmpty()) {
            databaseHelper.insertTracks(trackToUpdate).executeAsBlocking()
        }
    }

    /**
     * Restore the chapters for manga if chapters already in database
     *
     * @param manga manga of chapters
     * @param chapters list containing chapters that get restored
     * @return boolean answering if chapter fetch is not needed
     */
    internal fun restoreChaptersForManga(manga: Manga, chapters: List<Chapter>): Boolean {
        val dbChapters = databaseHelper.getChapters(manga).executeAsBlocking()

        // Return if fetch is needed
        if (dbChapters.isEmpty() || dbChapters.size < chapters.size)
            return false

        for (chapter in chapters) {
            val pos = dbChapters.indexOf(chapter)
            if (pos != -1) {
                val dbChapter = dbChapters[pos]
                chapter.id = dbChapter.id
                chapter.copyFrom(dbChapter)
                break
            }
        }
        // Filter the chapters that couldn't be found.
        chapters.filter { it.id != null }
        chapters.map { it.manga_id = manga.id }

        insertChapters(chapters)
        return true
    }

    /**
     * Returns manga
     *
     * @return [Manga], null if not found
     */
    internal fun getMangaFromDatabase(manga: Manga): Manga? =
        databaseHelper.getManga(manga.url, manga.source).executeAsBlocking()

    /**
     * Returns list containing manga from library
     *
     * @return [Manga] from library
     */
    internal fun getFavoriteManga(): List<Manga> =
        databaseHelper.getFavoriteMangas().executeAsBlocking()

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    internal fun insertManga(manga: Manga): Long? =
        databaseHelper.insertManga(manga).executeAsBlocking().insertedId()

    /**
     * Inserts list of chapters
     */
    private fun insertChapters(chapters: List<Chapter>) {
        databaseHelper.updateChaptersBackup(chapters).executeAsBlocking()
    }

    /**
     * Return number of backups.
     *
     * @return number of backups selected by user
     */
    fun numberOfBackups(): Int = preferences.numberOfBackups().getOrDefault()
}
