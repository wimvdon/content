package io.github.impca201.rssreader.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.impca201.rssreader.ui.theme.RssReaderAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RssReaderAppTheme {
                RssReaderNavigation()
            }
        }
    }
}
