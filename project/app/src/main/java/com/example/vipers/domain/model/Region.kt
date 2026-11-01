/*
 * Region.kt
 * The fixed list of regions a campsite can be located in.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/** A region of Alberta a campsite belongs to. [displayName] is the label shown in the UI. */
enum class Region(val displayName: String) {
    BANFF("Banff"),
    JASPER("Jasper"),
    KANANASKIS("Kananaskis"),
    HINTON("Hinton")
}
