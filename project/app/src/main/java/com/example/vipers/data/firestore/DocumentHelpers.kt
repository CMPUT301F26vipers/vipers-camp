/*
 * DocumentHelpers.kt
 * Small helpers the Firestore repositories share when reading documents.
 * Known issues: none.
 */
package com.example.vipers.data.firestore

import com.google.firebase.firestore.DocumentSnapshot

/** Reads a list of strings, or an empty list if the field is missing. */
fun DocumentSnapshot.getStringList(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

/** Reads a whole number as an Int, or null if the field is missing. */
fun DocumentSnapshot.getInt(field: String): Int? = getLong(field)?.toInt()
