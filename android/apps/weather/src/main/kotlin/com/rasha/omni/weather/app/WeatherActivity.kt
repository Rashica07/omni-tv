package com.rasha.omni.weather.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.rasha.omni.design.OmniTheme

class WeatherActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmniTheme { WeatherScreen(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh(force = false)          // a no-op while the stored forecast is still fresh
    }
}
