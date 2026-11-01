/*
 * TaskAwait.kt
 * Lets suspend functions wait for a Firebase Task (adapter between Firebase callbacks and coroutines).
 * Known issues: none. Written by hand instead of adding the kotlinx-coroutines-play-services library.
 */
package com.example.vipers.data.firestore

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Waits for this Task to finish and returns its result, or throws its error. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
}
