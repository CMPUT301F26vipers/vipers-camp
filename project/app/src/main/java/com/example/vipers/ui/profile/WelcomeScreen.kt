/*
 * WelcomeScreen.kt
 * The Welcome screen (mockup E01), shown on first launch when this device has no profile yet
 * (T-01). There are no usernames or passwords; the user is identified by their device (US 01.09.01).
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.PrimaryDark
import com.example.vipers.ui.theme.TextSecondary

/**
 * Asks for the user's name (required), email and phone (optional). [onProfileCreated] is
 * called once the profile is saved, so the app can open Home.
 * Tests pass their own [controller] with a fake repository.
 */
@Composable
fun WelcomeScreen(
    onProfileCreated: () -> Unit,
    controller: ProfileController = viewModel(factory = ProfileController.Factory),
) {
    LaunchedEffect(controller.created) {
        if (controller.created) onProfileCreated()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // green behind the status bar like the other screens, so the white clock stays readable
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(PrimaryDark))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier.size(88.dp).background(GreenFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_campsite),
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text("Welcome", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tell us your name to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            WelcomeField("Name", controller.name, controller::onNameChange, "welcome_name")
            WelcomeField(
                "Email (optional)", controller.email, controller::onEmailChange, "welcome_email",
                KeyboardType.Email,
            )
            WelcomeField(
                "Phone (optional)", controller.phone, controller::onPhoneChange, "welcome_phone",
                KeyboardType.Phone,
            )
            if (controller.saveFailed) {
                Text(
                    "Couldn't save your profile. Check your connection and try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(
            onClick = controller::createProfile,
            enabled = controller.canContinue,
            shape = ButtonShape,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .height(ButtonHeight)
                .testTag("button_continue"),
        ) {
            Text(if (controller.isSaving) "Saving…" else "Continue", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WelcomeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = InputShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
