/*
 * FormMode.kt
 * Whether the campsite form is adding a new campsite or editing a saved one.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

/** What the campsite form is doing (US 02.01.01, 02.01.03). */
enum class FormMode {
    /** Adding a new campsite (US 02.01.01). */
    CREATE,

    /** Changing a campsite the owner already saved (US 02.01.03). */
    EDIT
}
