package com.rasha.omni.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** TMDB v3. Accepts either kind of credential the site issues (a v4 read token or a v3 key). The credential is only ever sent to api.themoviedb.org over https. */
class TmdbClient(private val credential: String, private val language: String = "en-US") : MetadataProvider {
    private val bearer = credential.startsWith("eyJ")
    val available get() = credential.isNotBlank()

    private suspend fun get(path: String, vararg q: Pair<String, String>): String = withContext(Dispatchers.IO) {
        val qs = (q.toList() + ("language" to language) + (if (bearer) emptyList() else listOf("api_key" to credential))).joinToString("&") { it.first + "=" + URLEncoder.encode(it.second, "UTF-8") }
        val c = URL("https://api.themoviedb.org/3$path?$qs").openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 10000
        if (bearer) c.setRequestProperty("Authorization", "Bearer $credential")
        try { check(c.responseCode == 200) { "tmdb http ${c.responseCode}" }; c.inputStream.readBytes().toString(Charsets.UTF_8) } finally { c.disconnect() }
    }

    override suspend fun searchMovie(title: String, year: Int?) = TmdbJson.candidates(get("/search/movie", "query" to title, *(year?.let { arrayOf("year" to it.toString()) } ?: emptyArray())), false)
    override suspend fun movie(id: Int) = try { TmdbJson.movie(get("/movie/$id")) } catch (e: Exception) { null }
    override suspend fun findMovieByImdb(imdb: String) = TmdbJson.findMovieId(get("/find/$imdb", "external_source" to "imdb_id"))
    override suspend fun searchShow(title: String, year: Int?) = TmdbJson.candidates(get("/search/tv", "query" to title), true)
    override suspend fun show(id: Int) = try { TmdbJson.show(get("/tv/$id")) } catch (e: Exception) { null }
    override suspend fun season(showId: Int, season: Int) = TmdbJson.season(get("/tv/$showId/season/$season"))

    companion object { fun image(path: String?, size: String = "w342") = path?.let { "https://image.tmdb.org/t/p/$size$it" } }
}
