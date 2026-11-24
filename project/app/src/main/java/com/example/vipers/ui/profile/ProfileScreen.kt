/*
 * ProfileScreen.kt
 * The Profile tab (mockup E18). Settings and Policy open from here. The photo row at the top
 * changes or removes the profile photo, with a default avatar when there's none (US 01.08.02).
 * "Delete Profile" at the bottom deletes the profile after a dialog, then the app goes back to
 * Welcome (US 01.08.01).
 * Known issues: requires a profile created by the first-launch flow (T-01).
 */
package com.example.vipers.ui.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.ui.common.MenuRow
import com.example.vipers.ui.common.SavedPhoto
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Primary

/** Displays the user's profile and allows editing personal information. */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenPolicy: () -> Unit,
    onProfileDeleted: () -> Unit = {},
    controller: ProfileController = viewModel(factory = ProfileController.Factory),
    deleteController: DeleteProfileController =
        viewModel(factory = DeleteProfileController.Factory),
) {
    val context = LocalContext.current

    LaunchedEffect(deleteController.deleted) {
        if (deleteController.deleted) onProfileDeleted()
    }

    LaunchedEffect(controller.saved) {
        if (controller.saved) {
            Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
            controller.clearSaved()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "Profile")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (controller.isLoading) {
                CircularProgressIndicator()
            } else if (controller.loadFailed) {
                Text("Could not load profile.")
                Button(onClick = { controller.loadProfile() }) {
                    Text("Retry")
                }
            } else {
                PhotoRow(controller)

                ProfileField(
                    label = "Name",
                    value = controller.name,
                    onValueChange = controller::onNameChange,
                    error = controller.errors["name"],
                )

                ProfileField(
                    label = "Email",
                    value = controller.email,
                    onValueChange = controller::onEmailChange,
                    error = controller.errors["email"],
                    keyboardType = KeyboardType.Email,
                )

                ProfileField(
                    label = "Phone",
                    value = controller.phone,
                    onValueChange = controller::onPhoneChange,
                    error = controller.errors["phone"],
                    keyboardType = KeyboardType.Phone,
                )

                Button(
                    onClick = { controller.saveProfile() },
                    modifier = Modifier.fillMaxWidth(),
                    // a photo change saves the profile too, so Save waits for it
                    enabled = !controller.isSaving && !controller.isSavingPhoto,
                ) {
                    Text(if (controller.isSaving) "Saving…" else "Save")
                }

                if (controller.saveFailed) {
                    Text(
                        "Could not save profile. Please try again.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            MenuRow("Settings", onClick = onOpenSettings)
            MenuRow("Policy", onClick = onOpenPolicy)

            if (!controller.isLoading && !controller.loadFailed) {
                DeleteProfileRow(deleteController)
            }
        }
    }
}

/**
 * "Delete Profile" at the bottom of Profile (US 01.08.01, mockup E18), and the dialog that asks
 * first. The dialog says it can't be undone, and warns when the user's campsites will be
 * removed too.
 */
@Composable
private fun DeleteProfileRow(controller: DeleteProfileController) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (controller.isDeleting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Deleting profile…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            TextButton(
                onClick = controller::askToDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.testTag("button_delete_profile"),
            ) {
                Text("Delete Profile")
            }
        }
        controller.deleteError?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .testTag("delete_error")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    if (controller.isConfirming) {
        AlertDialog(
            onDismissRequest = controller::cancel,
            title = { Text("Delete your profile?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This can't be undone. Your name, email, phone and photo will be " +
                            "deleted, you'll be taken off every waiting list, and your " +
                            "reservations will be cancelled.",
                    )
                    if (controller.ownsCampsites != false) {
                        Text(
                            "Your campsites will be removed too, and their reservations " +
                                "cancelled.",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("delete_campsites_warning"),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = controller::confirm,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.testTag("button_confirm_delete"),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = controller::cancel) { Text("Cancel") }
            },
        )
    }
}

/**
 * The profile photo row (US 01.08.02, mockup E18): the photo, or a default avatar when there's
 * none, then "Change photo", which opens the phone's photo picker, and "Remove" once there's a
 * photo. The photo is saved as soon as it's picked; errors show underneath.
 */
@Composable
private fun PhotoRow(controller: ProfileController) {
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) controller.choosePhoto(uri.toString())
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SavedPhoto(
                imageId = controller.imageId,
                contentDescription = "Profile photo",
                shape = CircleShape,
                modifier = Modifier.size(56.dp).testTag("profile_photo"),
                placeholderIcon = R.drawable.ic_person,
            )
            if (controller.isSavingPhoto) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Saving photo…", style = MaterialTheme.typography.bodyMedium)
            } else {
                OutlinedButton(
                    onClick = {
                        // so an old error doesn't stay if the picker is closed without a photo
                        controller.clearPhotoError()
                        pickPhoto.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    enabled = controller.canChangePhoto,
                    border = BorderStroke(1.dp, Primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                    modifier = Modifier.testTag("button_change_photo"),
                ) {
                    Text("Change photo")
                }
                if (controller.imageId != null) {
                    TextButton(
                        onClick = controller::removePhoto,
                        enabled = controller.canChangePhoto,
                        modifier = Modifier.testTag("button_remove_photo"),
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
        controller.photoError?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .testTag("photo_error")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
