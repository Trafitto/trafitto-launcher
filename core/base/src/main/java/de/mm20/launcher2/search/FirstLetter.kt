package de.mm20.launcher2.search

import java.text.Normalizer

/**
 * Index letter of a label: A-Z without accents ("Écran" -> "E"), "#" for anything else
 * (digits, symbols, other scripts).
 */
fun firstLetter(label: String): String {
    val first = label.trim().take(1)
    val c = Normalizer.normalize(first, Normalizer.Form.NFD).firstOrNull()?.uppercaseChar()
    return if (c != null && c in 'A'..'Z') c.toString() else "#"
}
