package com.edi.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.edi.hub.data.HubPrefs
import com.edi.hub.ui.HubApp
import com.edi.hub.ui.theme.HubTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: HubPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HubTheme(dynamicColor = prefs.dynamicColor) {
                HubApp()
            }
        }
    }
}
