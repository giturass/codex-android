package com.hebo.codex.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSelectorPaginationTest {
    @Test
    fun revealsNextLoadedProjectPageWithoutSkippingOptions() {
        assertEquals(16, nextProjectOptionLimit(currentLimit = 8, loadedProjectCount = 20))
    }

    @Test
    fun reservesOnePageForProjectsLoadedFromServer() {
        assertEquals(13, nextProjectOptionLimit(currentLimit = 8, loadedProjectCount = 5))
    }
}
