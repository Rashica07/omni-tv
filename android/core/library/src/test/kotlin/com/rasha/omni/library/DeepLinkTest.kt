package com.rasha.omni.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkTest {
    @Test fun validLinks() {
        assertEquals(Route.Home, DeepLink.parse("omni-cinema://home"))
        assertEquals(Route.Movie(MediaId.Movie(949)), DeepLink.parse("omni-cinema://movie/omni:movie:tmdb:949"))
        assertEquals(Route.Show(MediaId.Show(1396)), DeepLink.parse("omni-cinema://show/omni:show:tmdb:1396"))
        assertEquals(Route.Play(MediaId.Movie(603)), DeepLink.parse("omni-cinema://play/omni:movie:tmdb:603"))
        assertEquals(Route.Episode(MediaId.Episode(1, 2, 3)), DeepLink.parse("omni-cinema://episode/omni:ep:tmdb:1:2:3"))
        assertEquals(Route.Source(7), DeepLink.parse("omni-cinema://source/7"))
        assertTrue(DeepLink.parse("omni-cinema://movie/omni:file:0123456789abcdef") is Route.Movie)
    }

    @Test fun everythingElseIsInvalidNeverACrashOrAGuess() {
        val bad = listOf(null, "", "http://example.com", "omni-cinema://", "omni-cinema://unknown/1", "omni-cinema://home/extra", "omni-cinema://movie", "omni-cinema://movie/",
            "omni-cinema://movie/omni:show:tmdb:5", "omni-cinema://show/omni:movie:tmdb:5", "omni-cinema://play/../../x", "omni-cinema://play/omni:movie:tmdb:5?pin=1", "omni-cinema://play/omni:movie:tmdb:5#x",
            "omni-cinema://source/0", "omni-cinema://source/-1", "omni-cinema://source/abc", "omni-cinema://source/99999999999", "omni-cinema://movie/omni:movie:tmdb:5 ", "omni-cinema://movie/omni:movie:tmdb:5\n",
            "omni-cinema://movie/" + "a".repeat(300), "omni-cinema://play/omni:movie:tmdb:5\\x", "omni-cinema://movie/omni:movie:tmdb:١٢")
        for (b in bad) assertTrue("should be invalid: '$b'", DeepLink.parse(b) is Route.Invalid)
    }

    @Test fun builtLinksParseBack() {
        val id = MediaId.Movie(949)
        assertEquals(Route.Movie(id), DeepLink.parse(DeepLink.movie(id)))
        assertEquals(Route.Play(id), DeepLink.parse(DeepLink.play(id)))
    }
}
