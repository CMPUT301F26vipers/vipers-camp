/*
 * DuplicateLotteryEntryException.kt
 * Thrown when attempting to save a lottery entry that already exists for a user and lottery.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/**
 * Thrown by lottery repositories when attempting to save an entry with an ID that already exists.
 * Guarantees that an entrant cannot join the same lottery multiple times.
 */
class DuplicateLotteryEntryException(message: String) : IllegalStateException(message)
