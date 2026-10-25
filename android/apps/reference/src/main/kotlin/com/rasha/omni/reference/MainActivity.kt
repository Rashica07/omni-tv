package com.rasha.omni.reference

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.rasha.omni.design.OmniTheme

class MainActivity : ComponentActivity() {
    private val vm: ReferenceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmniTheme { ReferenceScreen(vm) } }
    }
}
