package com.tazzzo.app

import kotlin.test.Test
import kotlin.test.assertEquals

/** F6: "Start shopping" must land on the Home feed regardless of the last-open tab. */
class GoHomeTest {
    @Test fun goHome_resets_the_selected_tab_not_only_the_back_stack() {
        val app = TazzzoAppState(store = null)
        app.homeTab = HomeTab.ACCOUNT
        app.navigate(Screen.Club); app.navigate(Screen.ClubCheckout)
        app.goHome()
        assertEquals(HomeTab.HOME, app.homeTab)
        assertEquals(listOf<Screen>(Screen.Home), app.backStack.toList())
    }

    @Test fun resetTo_alone_leaves_the_tab_untouched_which_is_why_goHome_exists() {
        val app = TazzzoAppState(store = null)
        app.homeTab = HomeTab.ACCOUNT
        app.resetTo(Screen.Home)
        assertEquals(HomeTab.ACCOUNT, app.homeTab)
    }
}
