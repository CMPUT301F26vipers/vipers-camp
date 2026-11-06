/*
 * RulesAndPolicySection.kt
 * A campsite's rules and cancellation policy as "Rules: …" and "Cancellation: …" lines (T-11),
 * shared by every screen that shows them, starting with the owner's My Campsite (mockup O04).
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.vipers.domain.model.Campsite

/**
 * Shows [campsite]'s rules and cancellation policy, each as a line with its label in bold, and
 * leaves out either one the owner hasn't written (T-11).
 *
 * It adds the lines straight into the caller's Column, with no layout of its own, so they take
 * that Column's spacing; when the campsite has neither, it shows nothing.
 */
@Composable
fun RulesAndPolicySection(campsite: Campsite) {
    if (campsite.hasRules) {
        LabelledText("Rules", campsite.rules)
    }
    if (campsite.hasCancellationPolicy) {
        LabelledText("Cancellation", campsite.cancellationPolicy)
    }
}

/** Text like "Rules: quiet hours after 10 PM", with the [label] in bold. */
@Composable
private fun LabelledText(label: String, text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$label: ") }
            append(text)
        }
    )
}
