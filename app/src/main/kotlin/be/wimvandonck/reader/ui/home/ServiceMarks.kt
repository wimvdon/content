package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperReadingList
import be.wimvandonck.reader.raindrop.RaindropSaved

/** Small marks for the services that keep a saved article (#121), shown while both are connected. */
@Composable
internal fun ServiceMarks(
    services: Collection<String>,
    modifier: Modifier = Modifier,
) {
    if (services.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        for (id in services) {
            val (icon, label) =
                when (id) {
                    InstapaperReadingList.ID -> R.drawable.ic_service_instapaper to R.string.source_instapaper
                    RaindropSaved.ID -> R.drawable.ic_service_raindrop to R.string.source_raindrop
                    else -> continue
                }
            Icon(painterResource(icon), contentDescription = stringResource(label), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
