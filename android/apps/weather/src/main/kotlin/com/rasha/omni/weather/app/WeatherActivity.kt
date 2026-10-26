package com.rasha.omni.weather.app

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rasha.omni.design.OmniTheme

/** The weather screen inside OMNI. OMNI hands over the place it shows on Home ("place") and gets the new one back as the result when you pick another. */
class WeatherActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WeatherViewModel(application as Application, intent.getStringExtra("place")) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.onPicked = { name -> setResult(RESULT_OK, Intent().putExtra("place", name)) }
        // Developer preview only (`setprop log.tag.OmniDebugOpen DEBUG`, extras sim=rain|snow|storm|fog|clear|cloud and hour=0..23): renders the sky and layout for a condition that is not
        // happening right now. The screen says SIMULATED PREVIEW while it is on, and nothing is stored.
        val sim: SimPreview? = if (android.util.Log.isLoggable("OmniDebugOpen", android.util.Log.DEBUG)) intent.getStringExtra("sim")?.let { SimPreview(it, intent.getIntExtra("hour", 14), intent.getIntExtra("min", 20)) } else null
        setContent { OmniTheme { WeatherScreen(vm, sim) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh(force = false)          // a no-op while the stored forecast is still fresh
    }
}
