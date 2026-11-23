package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class FrameWebTest {
    @Test fun ownerTextCannotBecomeMarkup() {
        val payload = "<script>alert('private')</script>"
        val page = FrameWeb.render(FrameWeb.State(mapOf("title" to payload, "albumId" to payload,
            "syncError" to payload, "diagnostics" to payload), emptySet(), listOf(FrameWeb.Album(payload,payload))))
        assertFalse(page.contains(payload))
        assertTrue(page.contains("&lt;script&gt;"))
        assertTrue(page.contains("https://github.com/letsteststuff/FrameRevive/releases"))
    }
    @Test fun diagnosticsClassifyErrorsWithoutExportingCredentials() {
        val secret = "https://private.example/?key=secret-cookie"
        assertEquals("HTTP 403", FrameDiagnostics.errorSummary("HTTP 403 $secret"))
        assertEquals("Error recorded (see private controls page)", FrameDiagnostics.errorSummary(secret))
        assertEquals("Timeout", FrameDiagnostics.errorSummary("Request timed out $secret"))
        assertEquals("None", FrameDiagnostics.errorSummary(null))
    }
    @Test fun newSetupOffersSharedLinksWithoutSessionImportControlsOrHandlers() {
        val page=FrameWeb.render(FrameWeb.State(emptyMap(),setOf("needsSetup")))
        assertTrue(page.contains("name=\"sharedLink\""))
        for(marker in listOf("sessionFile","importButton","Export All Cookies","value=\"loadAlbums\"","value='selectAlbum'","get-cookiestxt")) assertFalse(marker,page.contains(marker))
        assertFalse(page.contains("<h2>Switch to a shared album link</h2>"))
        assertTrue(page.contains("Not connected"))
    }
    @Test fun existingSessionGetsMigrationNoticeWhileSharedAlbumsDoNot() {
        val legacy=FrameWeb.render(FrameWeb.State(emptyMap(),setOf("legacySession")))
        assertTrue(legacy.contains("<h2>Switch to a shared album link</h2>"))
        assertTrue(legacy.contains("cannot be renewed here"))
        assertTrue(legacy.contains("Saved session (deprecated)"))
        val shared=FrameWeb.render(FrameWeb.State(emptyMap(),setOf("shared")))
        assertFalse(shared.contains("<h2>Switch to a shared album link</h2>"))
    }
    @Test fun pairToggleFollowsTheSavedSetting() {
        val on=FrameWeb.render(FrameWeb.State(emptyMap(),setOf("pairs")))
        val off=FrameWeb.render(FrameWeb.State(emptyMap(),emptySet()))
        assertTrue(on.contains("name=\"pairs\" checked"))
        assertFalse(off.contains("name=\"pairs\" checked"))
        assertTrue(on.contains("name=\"pairSettings\" value=\"1\""))
        assertTrue(on.contains("Show pairs"))
    }
    @Test fun activeAlbumLinkIsEscapedAndStaysOutsideCopiedDiagnostics() {
        val link="https://photos.app.goo.gl/example?test=<unsafe>"
        val page=FrameWeb.render(FrameWeb.State(mapOf("albumLink" to link,"diagnostics" to "Safe report"),setOf("shared")))
        assertTrue(page.contains("<dt>Album link</dt>"))
        assertTrue(page.contains("https://photos.app.goo.gl/example?test=&lt;unsafe&gt;"))
        assertFalse(page.contains(link))
        val report=page.substringAfter("id=\"diagnosticReport\"").substringBefore("</pre>")
        assertFalse(report.contains("photos.app.goo.gl"))
    }
    @Test fun theMixSettingsOfferTheRecencyChoiceWithTheStoredOneSelected() {
        val page=FrameWeb.render(FrameWeb.State(mapOf("recencyBias" to "even"),emptySet()))
        assertTrue(page.contains("Favor newer photos"))
        assertTrue(page.contains("<option value=\"even\" selected>"))
        assertFalse(page.contains("<option value=\"newest\" selected>"))
        assertTrue(page.contains("name=\"recencyBias\""))
    }
    @Test fun theMixSettingsOfferTheBlurChoiceWithTheStoredOneSelected() {
        val page=FrameWeb.render(FrameWeb.State(mapOf("skipBlurry" to "medium"),emptySet()))
        assertTrue(page.contains("Skip blurry photos"))
        assertTrue(page.contains("<option value=\"medium\" selected>Blurry photos"))
        assertFalse(page.contains("<option value=\"off\" selected>Off: show everything"))
        assertTrue(page.contains("name=\"skipBlurry\""))
    }
    @Test fun theAlbumCardSitsInTheMixTabBeforeTheSaveBarAndIsOptional() {
        val card="<article class=\"card\" id=\"albumStats\"><h2>Your album</h2></article>"
        val page=FrameWeb.render(FrameWeb.State(emptyMap(),emptySet(),albumHtml=card))
        assertEquals(1,Regex("id=\"albumStats\"").findAll(page).count())
        assertTrue(page.indexOf("id=\"albumStats\"") < page.indexOf("class=\"save-bar\""))
        assertFalse(FrameWeb.render(FrameWeb.State(emptyMap(),emptySet())).contains("albumStats"))
    }
    @Test fun theRoundSizeIsNotASettingAnyMore() {
        val page=FrameWeb.render(FrameWeb.State(mapOf("roundSize" to "300","memoryDays" to "14"),emptySet()))
        assertFalse(page.contains("Photos per round"))
        assertFalse(page.contains("name=\"roundSize\""))
        assertTrue("the memories window is still offered",page.contains("name=\"memoryDays\""))
        assertTrue(page.contains("Photos not yet shown come first; memories use"))
    }
    @Test fun theStarButtonSettingDefaultsToPauseAndShowsTheOwnersChoice() {
        val none = FrameWeb.render(FrameWeb.State(emptyMap(), emptySet()))
        assertTrue(none.contains("name=\"starButton\"")); assertTrue(none.contains("name=\"starSettings\""))
        assertTrue("pause is the default", none.contains("<option value=\"pause\" selected>"))
        val fav = FrameWeb.render(FrameWeb.State(mapOf("starButton" to "favorite"), emptySet()))
        assertTrue(fav.contains("<option value=\"favorite\" selected>")); assertFalse(fav.contains("<option value=\"pause\" selected>"))
    }
    @Test fun theFavoriteButtonReflectsWhetherThePhotoOnScreenIsStarred() {
        val off = FrameWeb.render(FrameWeb.State(mapOf("favoriteSummary" to "No favorites yet."), emptySet()))
        assertTrue(off.contains("Favorite this photo</button>")); assertTrue(off.contains("value=\"favorite\""))
        assertTrue(off.contains("No favorites yet."))
        val on = FrameWeb.render(FrameWeb.State(mapOf("favoriteSummary" to "3 favorites."), setOf("slideFavorite")))
        assertTrue(on.contains("Remove from favorites</button>")); assertFalse(on.contains("Favorite this photo</button>"))
    }
    @Test fun theEverydayPageShowsTheCurrentPictureAndItsCaptionEscaped() {
        val page=FrameWeb.render(FrameWeb.State(mapOf("shownStamp" to "1700","nowShowing" to "<b>March 4, 2021</b>"),emptySet()))
        assertTrue(page.contains("id=\"nowShowing\" data-shown=\"1700\"")); assertTrue(page.contains("<img alt=\"The photo on the frame now\""))
        assertTrue(page.contains("Now showing: &lt;b&gt;March 4, 2021&lt;/b&gt;")); assertFalse(page.contains("<b>March 4"))
        assertTrue("the image is fetched with the key from the page address",page.contains("image:'current'"))
        assertFalse(FrameWeb.render(FrameWeb.State(emptyMap(),emptySet())).contains("Now showing:"))
    }
    @Test fun theFavoritesBackupIsEscapedAndCanBeRestored() {
        val hostile = "<script>x</script>\t1\tframe\t0"
        val page = FrameWeb.render(FrameWeb.State(mapOf("favoritesBackup" to hostile), emptySet()))
        assertFalse(page.contains("<script>x</script>")); assertTrue(page.contains("&lt;script&gt;x"))
        assertTrue(page.contains("name=\"favoritesLink\"")); assertTrue(page.contains("value=\"favoritesAlbum\"")); assertTrue(page.contains("value=\"checkFavoritesAlbum\""))
        assertTrue(page.contains("name=\"favoritesImport\"")); assertTrue(page.contains("value=\"importFavorites\""))
    }
}
