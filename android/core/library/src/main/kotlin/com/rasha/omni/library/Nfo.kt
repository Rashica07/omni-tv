package com.rasha.omni.library

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** What a Kodi-style .nfo can tell us. An id here beats any name matching, exactly as in Kodi. */
data class Nfo(val title: String?, val year: Int?, val tmdbId: Int?, val imdbId: String?, val plot: String?)

object NfoParser {
    /** Untrusted input: DTDs and external entities are switched off, size is bounded. Returns null for anything that is not a movie/episode nfo. */
    fun parse(xml: String): Nfo? {
        if (xml.length > 200_000) return null
        return try {
            val f = DocumentBuilderFactory.newInstance()
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            f.isXIncludeAware = false; f.isExpandEntityReferences = false
            val doc = f.newDocumentBuilder().parse(xml.trim().removePrefix("﻿").byteInputStream())
            val root = doc.documentElement
            if (root.tagName !in setOf("movie", "episodedetails", "tvshow")) return null
            fun text(tag: String) = (root.getElementsByTagName(tag).item(0) as? Element)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            val ids = root.getElementsByTagName("uniqueid")
            var tmdb: Int? = text("tmdbid")?.toIntOrNull(); var imdb: String? = text("imdbid")
            for (i in 0 until ids.length) {
                val e = ids.item(i) as Element
                when (e.getAttribute("type").lowercase()) { "tmdb" -> tmdb = tmdb ?: e.textContent.trim().toIntOrNull(); "imdb" -> imdb = imdb ?: e.textContent.trim().takeIf { it.matches(Regex("tt\\d+")) } }
            }
            Nfo(text("title"), text("year")?.toIntOrNull(), tmdb, imdb?.takeIf { it.matches(Regex("tt\\d+")) }, text("plot"))
        } catch (e: Exception) { null }
    }
}
