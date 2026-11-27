package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshScheduleTest {
    private val hour = RefreshSchedule.INTERVAL_MS

    @Test fun neverReadMeansReadNow() = assertEquals(0L, RefreshSchedule.firstDelay(1_000_000L, 0L, haveCatalog = true))

    @Test fun noCatalogOnDiskMeansReadNowHoweverRecentTheLastRead() = assertEquals(0L, RefreshSchedule.firstDelay(1_000_000L, 999_000L, haveCatalog = false))

    @Test fun aRestartMinutesAfterAGoodReadWaitsOutTheRestOfTheHour() {
        val last = 10_000_000L
        assertEquals(hour - 10 * 60_000L, RefreshSchedule.firstDelay(last + 10 * 60_000L, last, haveCatalog = true))
        assertEquals(hour, RefreshSchedule.firstDelay(last, last, haveCatalog = true))
    }

    @Test fun anHourOrMoreAfterTheLastReadMeansReadNow() {
        val last = 10_000_000L
        assertEquals(0L, RefreshSchedule.firstDelay(last + hour, last, haveCatalog = true))
        assertEquals(0L, RefreshSchedule.firstDelay(last + 5 * hour, last, haveCatalog = true))
    }

    @Test fun aClockThatWentBackwardsDoesNotDelayTheReadForHours() = assertEquals(0L, RefreshSchedule.firstDelay(1_000L, 9_000_000L, haveCatalog = true))
}
