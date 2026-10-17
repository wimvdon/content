package be.wimvandonck.reader.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import be.wimvandonck.reader.ui.theme.RssReaderAppTheme

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
