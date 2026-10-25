package com.rasha.omni.cinema

import android.content.Context
import android.content.Intent
import com.rasha.omni.library.Availability
import com.rasha.omni.library.Library
import com.rasha.omni.library.MediaId
import com.rasha.omni.library.Route
import com.rasha.omni.library.Title

/** Turns a validated route into the activity to open. Never returns a blank player: when something cannot be played, the title page opens and says why. */
object Router {
    const val R = "r"; const val ID = "id"; const val NOTICE = "notice"

    private fun app(ctx: Context, route: String, id: String? = null, notice: String? = null) =
        Intent(ctx, CinemaActivity::class.java).putExtra(R, route).putExtra(ID, id).putExtra(NOTICE, notice).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /** Blocking (probes source health briefly); call off the main thread. */
    fun resolve(ctx: Context, lib: Library, route: Route): Intent = when (route) {
        Route.Home -> app(ctx, "home")
        is Route.Invalid -> app(ctx, "title", "", route.why)
        is Route.Source -> app(ctx, "sources")
        is Route.Movie -> app(ctx, "title", route.id.text)
        is Route.Show -> app(ctx, "title", route.id.text)
        is Route.Episode -> app(ctx, "title", route.id.text)
        is Route.Play -> play(ctx, lib, route.id)
    }

    private fun play(ctx: Context, lib: Library, id: MediaId): Intent {
        val t = lib.titles().firstOrNull { it.id == id } ?: return app(ctx, "title", id.text, "This title is not in your library.")
        if (t.kind == "show") return app(ctx, "title", id.text)
        return when (t.availability) {
            Availability.PLAYABLE -> {
                val av = lib.availabilities()
                val copy = t.copies.firstOrNull { av[it.sourceId] == Availability.PLAYABLE } ?: return app(ctx, "title", id.text, reason(t))
                Intent(ctx, PlayerActivity::class.java).putExtra("s", copy.sourceId).putExtra("p", copy.path).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            else -> app(ctx, "title", id.text, reason(t))
        }
    }

    fun reason(t: Title): String = when (t.availability) {
        Availability.OFFLINE -> "${t.sourceLabel} is not reachable right now, so this film cannot be played. Check the server, the network or the drive."
        Availability.ACCOUNT -> "${t.sourceLabel} needs you to sign in again (the PIN was rejected)."
        Availability.METADATA -> "This is a catalogue entry only: no playable file or authorised stream is linked to it."
        Availability.PLAYABLE -> ""
    }
}
