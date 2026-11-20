/*
 * PolicyScreen.kt
 * The Policy screen (mockup E21), opened from Settings or Profile. Shows the app policy and
 * guidelines: how the lottery works, reserving and fair use (US 01.11.01).
 * Known issues: none. The text is fixed in the app, so changing it needs a new release.
 */
package com.example.vipers.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vipers.ui.common.TopBar

/**
 * One part of the policy: a [title] like "Reserving" and its [paragraphs].
 */
data class PolicySection(val title: String, val paragraphs: List<String>)

/** The app policy and guidelines shown on the Policy screen, in order (US 01.11.01). */
val POLICY_SECTIONS = listOf(
    PolicySection(
        title = "How the lottery works",
        paragraphs = listOf(
            "Popular campsites give out their nights by lottery instead of first come, first " +
                "served. Each lottery has a registration period. Join the waiting list any time " +
                "while it is open; joining early doesn't improve your chances.",
            "You can join each lottery once, with the size of your group. Some lotteries have a " +
                "limit on how many people can wait, and you can't join once it is full. You " +
                "can leave the waiting list any time before the draw.",
            "After registration closes, winners are picked at random from everyone on the " +
                "waiting list. Everyone gets a notification saying whether they were picked.",
        ),
    ),
    PolicySection(
        title = "Reserving",
        paragraphs = listOf(
            "If you win, pick your nights and reserve before the booking period ends. A win " +
                "that isn't used by then expires. If you decline, your spot goes to someone " +
                "else on the waiting list.",
            "Before reserving you must accept the campsite's rules. The price is shown before " +
                "you reserve and doesn't change after you book. Your group can't be bigger than " +
                "the campsite allows.",
            "You can cancel a reservation from My Reservations any time before check-in. Your " +
                "nights are released to other winners and the campsite's cancellation policy " +
                "applies. A campsite owner can also reject a reservation and must give a reason.",
        ),
    ),
    PolicySection(
        title = "Fair use",
        paragraphs = listOf(
            "Your profile is tied to this device, so there is no password. Use one profile " +
                "and keep your name, email and phone number accurate, since owners use them to " +
                "contact you about your stay.",
            "Be respectful in messages with campsite owners. Don't post misleading campsite " +
                "listings, photos or reviews.",
            "Admins can remove campsites, profiles, images and reviews that break these " +
                "guidelines. You can turn notifications off at any time in Settings.",
        ),
    ),
)

/**
 * The app policy and guidelines (mockup E21, US 01.11.01). The text scrolls so it can all be
 * read on small screens, and it looks the same for every user.
 *
 * @param onBack Called when the back arrow is tapped.
 */
@Composable
fun PolicyScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = "Policy", onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("policy_text"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            POLICY_SECTIONS.forEachIndexed { index, section ->
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = if (index == 0) 0.dp else 12.dp)
                        .semantics { heading() },
                )
                section.paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
