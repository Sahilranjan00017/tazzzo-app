package com.tazzzo.app.data.order

import com.tazzzo.app.data.auth.AuthSessionManager
import com.tazzzo.app.data.auth.RestoreOutcome

/**
 * Cold start: restore the secure session, start the session bindings, then decide what to do with a pending order record.
 *
 *  - [RestoreOutcome.Restored] (including a session kept after a TRANSIENT refresh failure): ONE automatic reconciliation.
 *  - [RestoreOutcome.Rejected] (the backend definitively refused the credential): delete the record, no POST.
 *  - [RestoreOutcome.NoSession] (nothing saved/readable): do nothing. That is not a logout, so the record is KEPT.
 *
 * [startBindings] receives whether a session is present, so the bindings never mistake the pre-restoration state for a logout.
 */
suspend fun restoreSessionAndRecoverOrders(
    session: AuthSessionManager,
    orders: OrderStore,
    startBindings: (initiallyAuthenticated: Boolean) -> Unit
) {
    val outcome = session.restore()
    startBindings(outcome == RestoreOutcome.Restored)
    when (outcome) {
        RestoreOutcome.Restored -> orders.resumeAfterRestore()
        RestoreOutcome.Rejected -> orders.onSessionRejected()
        RestoreOutcome.NoSession -> Unit
    }
}
