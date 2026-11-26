/*
 * RulesAndPolicySection.kt
 * A campsite's rules and cancellation policy as "Rules: …" and "Cancellation: …" lines (T-11),
 * shared by every screen that shows them: My Campsite (mockup O04), Campsite Details (mockup
 * E05), and Reserve (mockup E11, US 02.11.01). Also holds LabelledText, which CampsiteDetails
 * uses for "Region".
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
 * leaves out either one the owner hasn't written (T-11). If [emptyRulesText] is provided and
 * the campsite has no rules, displays [emptyRulesText] (US 02.11.01).
 *
 * It adds the lines straight into the caller's Column, with no layout of its own, so they take
 * that Column's spacing; when the campsite has neither and [emptyRulesText] is null, it shows
 * nothing.
 */
@Composable
fun RulesAndPolicySection(
    campsite: Campsite,
    emptyRulesText: String? = null,
) {
    if (campsite.hasRules) {
        LabelledText("Rules", campsite.rules)
    } else if (emptyRulesText != null) {
        LabelledText("Rules", emptyRulesText)
    }
    if (campsite.hasCancellationPolicy) {
        LabelledText("Cancellation", campsite.cancellationPolicy)
    }
}

/**
 * Text like "Rules: quiet hours after 10 PM", with the [label] in bold. Internal so
 * CampsiteDetails can use it for other labelled lines, like "Region" (US 01.01.05).
 */
@Composable
internal fun LabelledText(label: String, text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$label: ") }
            append(text)
        }
    )
}
