/*
 * ProfileScreen.kt
 * The Profile tab (mockup E18). Settings and Policy open from here.
 * Known issues: requires a profile created by the first-launch flow (T-01).
 */
package com.example.vipers.ui.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.ui.common.MenuRow
import com.example.vipers.ui.common.TopBar

/** Displays the user's profile and allows editing personal information. */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenPolicy: () -> Unit,
    controller: ProfileController = viewModel(factory = ProfileController.Factory),
) {
    val context = LocalContext.current

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
                    enabled = !controller.isSaving,
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
