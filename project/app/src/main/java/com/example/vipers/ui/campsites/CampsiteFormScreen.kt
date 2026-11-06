/*
 * CampsiteFormScreen.kt
 * The Add Campsite screen (mockup O02), opened from the "+" on My Campsites (US 02.01.01,
 * 02.01.02a), and the Edit Campsite screen (mockup O05), opened from Edit on My Campsite
 * (US 02.01.03). CampsiteController checks and saves what the owner types, including the nightly
 * price (T-09).
 * Known issues: photo (02.01.02b), Find location (02.01.02c), and rules and cancellation policy
 * (T-11) are added by those stories.
 */
package com.example.vipers.ui.campsites

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.Primary

/**
 * The form for a new campsite or, when [campsiteId] is set, for editing that campsite
 * (US 02.01.03). [onBack] leaves without saving (asking first if anything was typed or changed),
 * and [onPublished] is called once the campsite is saved.
 * Tests pass their own [controller] with a fake repository.
 */
@Composable
fun CampsiteFormScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit,
    campsiteId: String? = null,
    controller: CampsiteController = viewModel(factory = CampsiteController.Factory),
) {
    val context = LocalContext.current
    var showDiscardDialog by remember { mutableStateOf(false) }
    val editing = campsiteId != null

    // ask before throwing away a half-filled form, for both the back arrow and the phone's Back;
    // with no form showing there's nothing to throw away
    val askBeforeLeaving = controller.hasUnsavedChanges && !controller.notAllowed
    val leave = { if (askBeforeLeaving) showDiscardDialog = true else onBack() }
    BackHandler(enabled = askBeforeLeaving) { showDiscardDialog = true }

    LaunchedEffect(campsiteId) {
        // the controller outlives a screen rotation, so don't load over what the owner has typed
        if (campsiteId != null && controller.mode != FormMode.EDIT) {
            controller.loadForEdit(campsiteId)
        }
    }

    LaunchedEffect(controller.published) {
        if (controller.published) {
            val message = if (editing) "Changes saved" else "Campsite published"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onPublished()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(title = if (editing) "Edit Campsite" else "Add Campsite", onBack = leave)

        // when editing, the form only shows once it's filled in, so Publish can't add a new campsite
        when {
            editing && controller.notAllowed -> {
                Text("You can only edit your own campsites", Modifier.padding(16.dp))
                return@Column
            }
            editing && controller.loadFailed -> {
                Text(
                    "Couldn't load the campsite. Check your connection and try again.",
                    Modifier.padding(16.dp),
                )
                return@Column
            }
            editing && controller.mode != FormMode.EDIT -> {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PhotoPlaceholder()
            FormField(
                label = "Name",
                value = controller.name,
                onValueChange = controller::onNameChange,
                placeholder = "Campsite name",
                error = controller.errors["name"],
                tag = "field_name",
            )
            FormField(
                label = "Location",
                value = controller.address,
                onValueChange = controller::onAddressChange,
                placeholder = "Address or place",
                error = controller.errors["address"],
                tag = "field_address",
            )
            // Region, Max guests and Price / night share a row, as in mockups O02 and O05
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RegionDropdown(
                    selected = controller.region,
                    onSelect = controller::onRegionChange,
                    error = controller.errors["region"],
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    label = "Max guests",
                    value = controller.maxGuests,
                    onValueChange = controller::onMaxGuestsChange,
                    placeholder = "e.g. 6",
                    error = controller.errors["maxOccupancy"],
                    tag = "field_max_guests",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    label = "Price / night",
                    value = controller.price,
                    onValueChange = controller::onPriceChange,
                    placeholder = "$50",
                    error = controller.errors["pricePerNightCents"],
                    tag = "field_price",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            FormField(
                label = "Description",
                value = controller.description,
                onValueChange = controller::onDescriptionChange,
                placeholder = "Amenities, e.g. outdoor cookhouse",
                error = null,
                tag = "field_description",
                singleLine = false,
            )
            TagsField(controller)
        }

        if (controller.saveFailed) {
            Text(
                "Couldn't save the campsite. Check your connection and try again.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Button(
            onClick = controller::publish,
            enabled = !controller.isSaving,
            shape = ButtonShape,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .height(ButtonHeight)
                .testTag("button_publish"),
        ) {
            val label = when {
                editing -> if (controller.isSaving) "Saving…" else "Save changes"
                else -> if (controller.isSaving) "Publishing…" else "Publish"
            }
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(if (editing) "Discard your changes?" else "Discard this campsite?") },
            text = {
                Text(if (editing) "Your changes won't be saved." else "What you've filled in won't be saved.")
            },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            },
        )
    }
}

/** A label above a text field, with the field's error underneath if it has one. */
@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String?,
    tag: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier) {
        FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            shape = InputShape,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** The Region dropdown from the fixed list. Shows "Choose" until the owner picks one. */
@Composable
private fun RegionDropdown(
    selected: Region?,
    onSelect: (Region) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        FieldLabel("Region")
        Box {
            OutlinedTextField(
                value = selected?.displayName ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Choose") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true,
                shape = InputShape,
                modifier = Modifier.fillMaxWidth(),
            )
            // a read-only text field doesn't take taps, so this box opens the menu instead
            Box(Modifier.matchParentSize().clickable { expanded = true }.testTag("field_region"))
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (region in Region.entries) {
                    DropdownMenuItem(
                        text = { Text(region.displayName) },
                        onClick = { onSelect(region); expanded = false },
                    )
                }
            }
        }
    }
}

/** Type a tag and tap Add; tap the x on a tag to remove it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsField(controller: CampsiteController) {
    Column {
        FieldLabel("Tags")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = controller.tagInput,
                onValueChange = controller::onTagInputChange,
                placeholder = { Text("e.g. Lake access") },
                singleLine = true,
                shape = InputShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { controller.addTag() }),
                modifier = Modifier.weight(1f).testTag("field_tag"),
            )
            OutlinedButton(
                onClick = controller::addTag,
                shape = ButtonShape,
                border = BorderStroke(1.dp, Primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                modifier = Modifier.testTag("button_add_tag"),
            ) {
                Text("Add")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (tag in controller.tags) {
                InputChip(
                    selected = false,
                    onClick = { controller.removeTag(tag) },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove $tag")
                    },
                )
            }
        }
    }
}

/** Where the photo picker goes; photos are added in US 02.01.02b. */
@Composable
private fun PhotoPlaceholder() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, InputShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+ Add photo (coming in US 02.01.02b)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
