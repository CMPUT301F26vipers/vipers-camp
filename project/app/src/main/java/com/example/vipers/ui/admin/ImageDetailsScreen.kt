/*
 * ImageDetailsScreen.kt
 * Displays an enlarged view of an uploaded image for admins (US 03.06.01, mockup A07b).
 * Known issues: image deletion is added in US 03.03.01.
 */
package com.example.vipers.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.ui.common.FullPhoto
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.Shapes
import java.text.DateFormat
import java.util.Date

/** Displays an enlarged view of an uploaded image and its details. */
@Composable
fun ImageDetailsScreen(
    imageId: String,
    onBack: () -> Unit,
    controller: AdminController = viewModel(factory = AdminController.Factory)
) {
    LaunchedEffect(imageId) {
        if (controller.images.isEmpty()) {
            controller.loadImages()
        }
    }

    val image = controller.getImage(imageId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBar(title = "Image", onBack = onBack)

        when {
            controller.imagesLoading && image == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            controller.imagesLoadFailed && image == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = controller.imageError ?: "Unable to load image.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { controller.loadImages() }) {
                        Text("Retry")
                    }
                }
            }
            image == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Image not found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val isCampsite = image.ownerType == ImageAsset.OWNER_CAMPSITE
                val typeLabel = if (isCampsite) "Campsite" else "Profile"
                val typeBg = if (isCampsite) GreenFill else MaterialTheme.colorScheme.secondaryContainer
                val typeColor = if (isCampsite) Primary else MaterialTheme.colorScheme.onSecondaryContainer

                val displayLabel = controller.getImageOwnerName(image)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FullPhoto(
                        imageId = image.id,
                        contentDescription = displayLabel,
                        shape = Shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = Shapes.medium,
                        border = BorderStroke(1.dp, Border)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = displayLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                Text(
                                    text = typeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = typeColor,
                                    modifier = Modifier
                                        .background(typeBg, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (image.ownerId.isNotBlank()) {
                                ImageDetailField(
                                    label = if (isCampsite) "Campsite ID" else "Profile ID",
                                    value = image.ownerId
                                )
                            }

                            if (image.createdAt > 0L) {
                                ImageDetailField(
                                    label = "Uploaded",
                                    value = DateFormat.getDateInstance(DateFormat.MEDIUM)
                                        .format(Date(image.createdAt))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Displays a labelled field in the image details card. */
@Composable
private fun ImageDetailField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
