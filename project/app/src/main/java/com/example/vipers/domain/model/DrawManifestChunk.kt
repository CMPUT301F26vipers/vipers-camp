/*
 * DrawManifestChunk.kt
 * An immutable chunk of a lottery's draw manifest, identifying all eligible WAITING participants
 * partitioned into winners and non-winners for that chunk.
 * Stored in the Firestore "lotteries/{lotteryId}/drawManifest" subcollection.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/**
 * A chunk of an authoritative draw manifest.
 *
 * @property drawId ID of the draw attempt, matching [Lottery.committedDrawId] when committed.
 * @property lotteryId The lottery this draw belongs to.
 * @property chunkIndex Zero-based index of this chunk.
 * @property winnerUserIds Entrant user IDs selected as winners in this chunk.
 * @property loserUserIds Entrant user IDs not selected as winners in this chunk.
 */
data class DrawManifestChunk(
    val drawId: String = "",
    val lotteryId: String = "",
    val chunkIndex: Int = 0,
    val winnerUserIds: List<String> = emptyList(),
    val loserUserIds: List<String> = emptyList(),
)
