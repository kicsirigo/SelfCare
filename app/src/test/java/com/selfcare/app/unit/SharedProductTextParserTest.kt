package com.selfcare.app.unit

import com.selfcare.app.domain.inci.SharedProductTextParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedProductTextParserTest {

    @Test
    fun `strips storefront suffix and URL from Google Lens share text`() {
        val result = SharedProductTextParser.extractProductQuery(
            subject = "CeraVe Foaming Cleanser 236ml - Rossmann",
            sharedText = "https://www.rossmann.hu/termek/cerave-foaming-cleanser"
        )
        assertEquals("CeraVe Foaming Cleanser 236ml", result)
    }

    @Test
    fun `extracts clean product name when text includes title and URL`() {
        val result = SharedProductTextParser.extractProductQuery(
            subject = null,
            sharedText = "La Roche-Posay Effaclar Duo+M | dm.hu https://www.dm.hu/la-roche-posay"
        )
        assertEquals("La Roche-Posay Effaclar Duo+M", result)
    }

    @Test
    fun `falls back to URL slug when only a product URL is shared`() {
        val result = SharedProductTextParser.extractProductQuery(
            subject = null,
            sharedText = "https://www.notino.hu/cerave/foaming-cleanser-236ml/?ref=lens"
        )
        assertEquals("cerave foaming cleanser 236ml", result)
    }

    @Test
    fun `returns empty string for blank input`() {
        assertEquals("", SharedProductTextParser.extractProductQuery(null, "   "))
    }
}
