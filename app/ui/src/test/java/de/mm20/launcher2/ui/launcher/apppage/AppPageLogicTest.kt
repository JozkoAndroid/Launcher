package de.mm20.launcher2.ui.launcher.apppage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppPageLogicTest {

    private val sk = Locale.forLanguageTag("sk")
    private val apps = listOf(
        AppEntry("tatra", "Tatra banka"),
        AppEntry("cas", "Čas"),
        AppEntry("claude", "Claude"),
        AppEntry("insta", "Instagram", searchOnly = true),
        AppEntry("2fa", "2FA Authenticator"),
        AppEntry("gemini", "Gemini"),
        AppEntry("school", "Schoolhelp"),
        AppEntry("chrome", "Chrome"),
    )

    @Test
    fun pinnedFirstInPinnedOrderAndNotRepeatedBelow() {
        val m = AppPageLogic.build(apps, listOf("school", "gemini"), "", sk)
        assertEquals(listOf("school", "gemini"), m.pinned.map { it.key })
        assertEquals(false, m.list.any { it.key == "school" || it.key == "gemini" })
    }

    @Test
    fun searchOnlyAppsAreHiddenFromListButFoundBySearch() {
        assertEquals(false, AppPageLogic.build(apps, emptyList(), "", sk).list.any { it.key == "insta" })
        assertEquals(listOf("insta"), AppPageLogic.build(apps, emptyList(), "insta", sk).list.map { it.key })
    }

    @Test
    fun slovakAlphabeticalOrderPutsCAfterCAndCh() {
        val m = AppPageLogic.build(apps, emptyList(), "", sk)
        // Slovak collation: C < Č < D ... < H < Ch
        assertEquals(listOf("2FA Authenticator", "Claude", "Čas", "Gemini", "Chrome", "Schoolhelp", "Tatra banka"), m.list.map { it.label })
    }

    @Test
    fun searchIgnoresCaseAndDiacriticsAndFiltersPinnedToo() {
        val m = AppPageLogic.build(apps, listOf("cas", "claude"), "CAS", sk)
        assertEquals(listOf("cas"), m.pinned.map { it.key })
        assertEquals(emptyList<AppEntry>(), m.list)
        assertEquals(2, m.pinnedCount)
    }

    @Test
    fun atMostEightPinnedAndUnknownKeysIgnored() {
        val many = (1..10).map { AppEntry("a$it", "App $it") }
        val m = AppPageLogic.build(many, listOf("gone") + many.map { it.key }, "", sk)
        assertEquals(8, m.pinned.size)
        assertEquals(listOf("App 10", "App 9"), m.list.map { it.label })
    }

    @Test
    fun letterIndexPointsAtFirstRowOfEachLetter() {
        val m = AppPageLogic.build(apps, emptyList(), "", sk)
        assertEquals(listOf("#" to 0, "C" to 1, "G" to 3, "S" to 5, "T" to 6), m.letters)
    }
}
