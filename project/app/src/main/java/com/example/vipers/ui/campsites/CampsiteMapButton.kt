/*
 * CampsiteMapButton.kt
 * Opens the owner's saved campsite location in a maps app from E05 and O04 (T-04).
 * Known issues: viewing the pin requires an app that handles geo links.
 */
package com.example.vipers.ui.campsites

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.vipers.domain.model.Campsite
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary

/** Shows a map action when [campsite] has both coordinates. */
@Composable
internal fun CampsiteMapButton(campsite: Campsite, modifier: Modifier = Modifier) {
    if (!campsite.hasLocation()) return
    val context = LocalContext.current
    TextButton(
        onClick = {
            val point = "${campsite.lat},${campsite.lng}"
            val uri = Uri.parse("geo:$point?q=$point")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No maps app is available.", Toast.LENGTH_LONG).show()
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(containerColor = GreenFill, contentColor = Primary),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Open in Maps")
            Text(campsite.coordinatesText().orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
    }
}
