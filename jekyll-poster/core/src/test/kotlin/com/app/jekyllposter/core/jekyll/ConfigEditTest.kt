package com.app.jekyllposter.core.jekyll

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigEditTest {
    @Test fun anExistingTimezoneIsReplacedAndTheRestKept() {
        val config = "title: Notes # mine\ntimezone: UTC\nbaseurl: /x\n"
        assertEquals("title: Notes # mine\ntimezone: America/Los_Angeles\nbaseurl: /x\n", ConfigEdit.withTimezone(config, "America/Los_Angeles"))
    }

    @Test fun withoutOneALineIsAddedAtTheEnd() {
        assertEquals("title: Notes\ntimezone: Europe/Paris\n", ConfigEdit.withTimezone("title: Notes", "Europe/Paris"))
        assertEquals("timezone: Europe/Paris\n", ConfigEdit.withTimezone(null, "Europe/Paris"))
    }

    @Test fun aNestedTimezoneIsNotTheSitesAndTheLastTopLevelOneWins() {
        val config = "plugin:\n  timezone: Asia/Tokyo\ntimezone: UTC\ntimezone: Etc/GMT+1\n"
        assertEquals("plugin:\n  timezone: Asia/Tokyo\ntimezone: UTC\ntimezone: Europe/Paris\n", ConfigEdit.withTimezone(config, "Europe/Paris"))
        // And the reader agrees about which one counts.
        assertEquals(java.time.ZoneId.of("Europe/Paris"), SiteConfig.parse(ConfigEdit.withTimezone(config, "Europe/Paris")).timezone)
    }
}
