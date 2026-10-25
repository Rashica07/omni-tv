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
        setContent { OmniTheme { WeatherScreen(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh(force = false)          // a no-op while the stored forecast is still fresh
    }
}
