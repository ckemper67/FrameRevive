package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class FrameWebTest {
    @Test fun thePageSaysTheMixSettingsAlsoSteerCollagesButFavoritesDoNot() {
val page=FrameWeb.render(FrameWeb.State(emptyMap(),emptySet()))
assertTrue(page.contains("also decide which days collages come from"))
assertTrue(page.contains("Favorites are not used for collages"))
}
@Test fun everyCollageThemeHasAToggleOnThePage() {
val page=FrameWeb.render(FrameWeb.State(emptyMap(),setOf("collageTheme_day","collageTheme_outing","collageTheme_anniversary")))
for(theme in CollageThemes.all) {
assertTrue("toggle for ${theme.id}",page.contains("name=\"collageTheme_${theme.id}\""))
assertTrue("${theme.label} is named",page.contains("Collage theme: ${theme.label}"))
}
assertTrue(page.contains("name=\"collageTheme_anniversary\" checked"))
}
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
    @Test fun everySettingFieldIsCoveredByASectionMarkerSoASectionSavesOnlyItself() {
        val page=FrameWeb.render(FrameWeb.State(emptyMap(),emptySet()))
        val sections=mapOf(
            "playbackSettings" to listOf("seconds","crop","date"),
            "brightnessSettings" to listOf("brightness"),
            "sleepSettings" to listOf("schedule","sleepStart","sleepEnd"),
            "holidaySettings" to listOf("holidayTheme"),
        )
        sections.forEach { (marker, fields) ->
            assertTrue("missing $marker",page.contains("name=\"$marker\" value=\"1\""))
            fields.forEach { assertTrue("missing field $it",page.contains("name=\"$it\"")) }
        }
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
        assertTrue(off.contains("&#9734;</span> Favorite</button>")); assertTrue(off.contains("value=\"favorite\""))
        assertTrue(off.contains("No favorites yet."))
        val on = FrameWeb.render(FrameWeb.State(mapOf("favoriteSummary" to "3 favorites."), setOf("slideFavorite")))
        assertTrue(on.contains("&#9733;</span> Unfavorite</button>")); assertFalse(on.contains("&#9734;</span> Favorite</button>"))
    }
    @Test fun theEverydayPageShowsTheCurrentPictureAndItsCaptionEscaped() {
        val page=FrameWeb.render(FrameWeb.State(mapOf("shownStamp" to "1700","nowShowing" to "<b>March 4, 2021</b>"),emptySet()))
        assertTrue(page.contains("id=\"nowShowing\" data-shown=\"1700\"")); assertTrue(page.contains("<img alt=\"The photo on the frame now\""))
        assertTrue(page.contains("Now showing: &lt;b&gt;March 4, 2021&lt;/b&gt;")); assertFalse(page.contains("<b>March 4"))
        assertTrue("the image is fetched with the key from the page address",page.contains("image:'current'"))
        assertFalse(FrameWeb.render(FrameWeb.State(emptyMap(),emptySet())).contains("Now showing:"))
    }
    @Test fun thePictureControlsSitUnderThePictureInOneRowLikeAMediaPlayer() {
        val page=FrameWeb.render(FrameWeb.State(emptyMap(),emptySet()))
        val picture=page.indexOf("id=\"nowShowing\"")
        val order=listOf("value=\"previous\"","value=\"pause\"","value=\"next\"","value=\"favorite\"").map { page.indexOf(it,picture) }
        assertTrue(order.all { it > picture }); assertEquals(order.sorted(),order)
        assertTrue("one form holds the row",!page.substring(order.first(),order.last()).contains("</form>"))
        assertTrue(FrameWeb.render(FrameWeb.State(emptyMap(),setOf("paused"))).contains("value=\"resume\""))
    }
    @Test fun theFavoritesSettingOffersEveryShareAndKeepsTheChoice() {
        val page=FrameWeb.render(FrameWeb.State(mapOf("favoriteShare" to "lots"),emptySet()))
        for (id in FavoriteShare.entries.map { it.id }) assertTrue(id,page.contains("<option value=\"$id\""))
        assertTrue(page.contains("<option value=\"lots\" selected"))
    }
    @Test fun actionMessagesFadeButWorkingMessagesStay() {
        val page=FrameWeb.render(FrameWeb.State(emptyMap(),emptySet()))
        assertTrue(page.contains("result.textContent='';},8000"))
        assertTrue(page.contains("say('Sending…',true)")); assertTrue(page.contains("say('Uploading APK… Keep this page open.',true)"))
        assertFalse("every message goes through say()",page.contains("result.textContent="+"'Request"))
    }
    @Test fun theCurrentPictureIsUpdatedInPlaceSoThePageDoesNotMove() {
        val page=FrameWeb.render(FrameWeb.State(mapOf("shownStamp" to "1","nowShowing" to "x"),emptySet()))
        assertFalse("the picture block is never replaced by a status refresh",page.contains("'preloadStatus','nowShowing'"))
        assertTrue("its space is reserved before the picture arrives",page.contains("height:clamp(200px,42vw,420px)"))
        assertTrue("the new picture is fetched first and swapped in when ready",page.contains("URL.createObjectURL(b)"))
    }
    @Test fun theFavoritesCardReportsWhatWasShown() {
        val page=FrameWeb.render(FrameWeb.State(mapOf("favoriteLog" to "Favorites shown: 3 today <x>"),emptySet()))
        assertTrue(page.contains("id=\"favoriteLog\"")); assertTrue(page.contains("Favorites shown: 3 today &lt;x&gt;"))
    }
    @Test fun clickingThePictureOpensTheTileUnderThePointerInGooglePhotosOnly() {
        val page=FrameWeb.render(FrameWeb.State(mapOf("photoTiles" to "0.0,0.0,1.0,1.0,https://photos.google.com/share/a/photo/p?key=k&x=\"<b>"),emptySet()))
        assertTrue(page.contains("data-tiles=\"0.0,0.0,1.0,1.0,https://photos.google.com/share/a/photo/p?key=k&amp;x=&quot;&lt;b&gt;\""))
        assertTrue(page.contains("function openTile("))
        assertTrue("only Google Photos addresses are opened",page.contains("url.startsWith('https://photos.google.com/')"))
        assertTrue("without opener access",page.contains("window.open(url,'_blank','noopener')"))
    }
    @Test fun theFavoritesBackupIsEscapedAndCanBeRestored() {
        val hostile = "<script>x</script>\t1\tframe\t0"
        val page = FrameWeb.render(FrameWeb.State(mapOf("favoritesBackup" to hostile), emptySet()))
        assertFalse(page.contains("<script>x</script>")); assertTrue(page.contains("&lt;script&gt;x"))
        assertTrue(page.contains("name=\"favoritesLink\"")); assertTrue(page.contains("value=\"favoritesAlbum\"")); assertTrue(page.contains("value=\"checkFavoritesAlbum\""))
        assertTrue(page.contains("name=\"favoritesImport\"")); assertTrue(page.contains("value=\"importFavorites\""))
    }
}
