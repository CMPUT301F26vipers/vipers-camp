/*
 * ProfileScreen.kt
 * The Profile tab (mockup E18). Settings and Policy open from here.
 * Known issues: placeholder. Editing the profile is added in US 01.07.03.
 */
package com.example.vipers.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.ui.common.MenuRow
import com.example.vipers.ui.common.TopBar

/** Displays the user's profile and allows editing personal information. */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenPolicy: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "Profile")

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator()
            } else if (viewModel.loadFailed) {
                Text("Could not load profile.")
                Button(onClick = { viewModel.loadProfile() }) {
                    Text("Retry")
                }
            } else {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Name") },
                    isError = viewModel.errors.containsKey("name"),
                    supportingText = {
                        viewModel.errors["name"]?.let {
                            Text(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = { viewModel.saveProfile() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isSaving
                ) {
                    Text(if (viewModel.isSaving) "Saving..." else "Save Changes")
                }

                if (viewModel.saved) {
                    Text(
                        "Profile updated successfully.",
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (viewModel.saveFailed) {
                    Text(
                        "Could not save profile. Please try again.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            MenuRow("Settings", onClick = onOpenSettings)
            MenuRow("Policy", onClick = onOpenPolicy)
        }
    }
}