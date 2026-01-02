package com.example.calibreboxnew.utils

import java.text.Normalizer

// A regular expression to find and remove diacritical marks.
private val DIACRITICS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()

/**
 * Normalizes a string by converting it to lowercase and removing diacritical marks.
 * e.g., "José" becomes "jose".
 * This is useful for case-insensitive and accent-insensitive searching.
 */
fun String.normalizeForSearch(): String {
    // 1. Decompose characters into base letter and combining marks (e.g., "é" -> "e" + "´").
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    // 2. Remove the combining marks.
    val withoutDiacritics = DIACRITICS_REGEX.replace(normalized, "")
    // 3. Convert to lowercase for case-insensitive matching.
    return withoutDiacritics.lowercase()
}
