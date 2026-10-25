package com.rasha.omni.cinema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.rasha.omni.library.DeepLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The only door into Cinema from other apps (omni-cinema://...). Protected by the signature permission in the manifest, so only OMNI-signed apps can start it.
 * It has no screen of its own: it validates the link, decides where to go and finishes. The destination becomes the root of Cinema's task, so Back returns to the launcher.
 */
class DeepLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val route = DeepLink.parse(intent?.dataString)
        val lib = (application as CinemaApp).lib
        lifecycleScope.launch {
            val target = withContext(Dispatchers.IO) { Router.resolve(this@DeepLinkActivity, lib, route) }
            startActivity(target)
            finish()
        }
    }
}
