package com.rasha.omni.library

import org.json.JSONArray
import org.json.JSONObject

data class MovieMeta(val tmdbId: Int, val title: String, val year: Int?, val overview: String, val poster: String?, val backdrop: String?, val rating: Double, val runtimeMin: Int, val genres: List<String>)
data class ShowMeta(val tmdbId: Int, val title: String, val year: Int?, val overview: String, val poster: String?, val backdrop: String?, val genres: List<String>)
data class EpisodeMeta(val season: Int, val episode: Int, val title: String, val overview: String, val still: String?, val airDate: String?)
data class Candidate(val id: Int, val title: String, val original: String, val year: Int?, val popularity: Double)

/** A metadata source. TMDB is one; the library never depends on which. Every call may throw on network failure, and the caller keeps unmatched items usable. */
interface MetadataProvider {
    suspend fun searchMovie(title: String, year: Int?): List<Candidate>
    suspend fun movie(id: Int): MovieMeta?
    suspend fun findMovieByImdb(imdb: String): Int?
    suspend fun searchShow(title: String, year: Int?): List<Candidate>
    suspend fun show(id: Int): ShowMeta?
    suspend fun season(showId: Int, season: Int): List<EpisodeMeta>
}

object Matching {
    fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace("&", "and").replace(Regex("""\b(the|a|an)\b"""), " ").replace(Regex("[^a-z0-9]+"), " ").trim()

    /** The best candidate, or null when nothing is convincingly right: an unmatched item shown by its file name is better than a wrong poster. */
    fun best(cands: List<Candidate>, title: String, year: Int?): Candidate? {
        val t = norm(title)
        val scored = cands.map { c ->
            var sc = 0.0
            val names = listOf(norm(c.title), norm(c.original))
            if (t in names) sc += 10 else if (names.any { it.startsWith(t) || t.startsWith(it) && it.length >= 4 }) sc += 4
            if (year != null && c.year != null) { sc += when (Math.abs(year - c.year)) { 0 -> 6; 1 -> 3; else -> -6 } }
            sc += Math.min(c.popularity, 100.0) / 100.0
            c to sc
        }.sortedByDescending { it.second }
        val top = scored.firstOrNull() ?: return null
        return if (top.second >= 9.0) top.first else null
    }
}

/** JSON → models for TMDB's v3 responses (kept apart from the HTTP so it is tested on fixtures). */
object TmdbJson {
    private fun year(d: String?) = d?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
    private fun JSONObject.str(k: String) = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }
    private fun names(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.str("name") }

    fun candidates(json: String, tv: Boolean): List<Candidate> {
        val a = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
            Candidate(o.getInt("id"), o.str(if (tv) "name" else "title") ?: "", o.str(if (tv) "original_name" else "original_title") ?: "", year(o.str(if (tv) "first_air_date" else "release_date")), o.optDouble("popularity", 0.0))
        }
    }

    fun movie(json: String): MovieMeta { val o = JSONObject(json); return MovieMeta(o.getInt("id"), o.str("title") ?: "", year(o.str("release_date")), o.str("overview") ?: "", o.str("poster_path"), o.str("backdrop_path"), o.optDouble("vote_average", 0.0), o.optInt("runtime", 0), names(o.optJSONArray("genres"))) }
    fun show(json: String): ShowMeta { val o = JSONObject(json); return ShowMeta(o.getInt("id"), o.str("name") ?: "", year(o.str("first_air_date")), o.str("overview") ?: "", o.str("poster_path"), o.str("backdrop_path"), names(o.optJSONArray("genres"))) }
    fun season(json: String): List<EpisodeMeta> { val a = JSONObject(json).optJSONArray("episodes") ?: return emptyList(); return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { EpisodeMeta(it.optInt("season_number"), it.optInt("episode_number"), it.str("name") ?: "", it.str("overview") ?: "", it.str("still_path"), it.str("air_date")) } }
    fun findMovieId(json: String): Int? = JSONObject(json).optJSONArray("movie_results")?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
}
