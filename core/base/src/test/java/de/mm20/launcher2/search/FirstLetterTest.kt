package de.mm20.launcher2.search

import org.junit.Assert
import org.junit.Test

class FirstLetterTest {
    @Test
    fun lettersAccentsAndOthers() {
        Assert.assertEquals("W", firstLetter("WhatsApp"))
        Assert.assertEquals("B", firstLetter("bitchat"))
        Assert.assertEquals("E", firstLetter("Écran"))
        Assert.assertEquals("#", firstLetter("2048"))
        Assert.assertEquals("#", firstLetter("@Home"))
        Assert.assertEquals("#", firstLetter("Яндекс"))
        Assert.assertEquals("#", firstLetter(""))
    }
}
