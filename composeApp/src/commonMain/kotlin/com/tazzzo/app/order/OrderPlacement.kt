package com.tazzzo.app.order

import com.tazzzo.app.CheckoutSession
import com.tazzzo.app.Screen
import com.tazzzo.app.TazzzoAppState
import com.tazzzo.app.analytics.Analytics
import com.tazzzo.app.analytics.AnalyticsEvents
import com.tazzzo.app.data.model.Address
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.OrderRequest
import com.tazzzo.app.data.model.PaymentMethodKind
import com.tazzzo.app.data.model.PlaceOrderResult
import com.tazzzo.app.data.repository.CheckoutRepository
import com.tazzzo.app.data.repository.CoinRepository
import com.tazzzo.app.data.repository.MembershipRepository
import com.tazzzo.app.data.repository.ServiceLocator

/**
 * The ONE path through which an order is placed and its client-side effects
 * are applied.
 *
 * Why this exists: the coin credit and the cart clear used to be written out
 * by hand at each call site (the checkout screen and the demo autopilot). The
 * checkout screen learned to gate those effects on `!replayed`; the demo
 * autopilot did not, so a replayed placement credited coins twice through that
 * path. Duplicating a money side effect is the defect — not the missing `if` —
 * so both callers now go through here and there is only one place left that
 * can get the guard wrong.
 *
 * Rule for anyone adding to this file: a *replayed* placement is the SAME
 * order coming back (duplicate tap, or a retry after a timeout the server
 * actually honoured). Analytics may record `replayed`; state mutation must
 * never ignore it. Every new side effect belongs inside `applyFirstPlacement`.
 *
 * Coin crediting here is DEMO behaviour and must move server-side before
 * launch (BLOCKERS.md P0 "Real backend"). This function is the single seam
 * where that removal will happen.
 */
object OrderPlacement {

    /**
     * Places the current cart and applies the client-side result.
     *
     * Returns null when the attempt was refused before it reached the
     * repository (a placement is already in flight). Otherwise returns the
     * repository result, after the corresponding app state has been applied.
     *
     * The same [CheckoutSession.idempotencyKey] is submitted on every attempt,
     * including retries after a failure, so the server ledger — not the client
     * — decides whether this is a new order or a replay.
     */
    suspend fun place(
        app: TazzzoAppState,
        session: CheckoutSession,
        address: Address,
        slot: DeliverySlot,
        payment: PaymentMethodKind,
        checkout: CheckoutRepository = ServiceLocator.checkout,
        coins: CoinRepository = ServiceLocator.coins,
        memberships: MembershipRepository = ServiceLocator.membership,
        navigate: Boolean = true
    ): PlaceOrderResult? {
        if (session.placement is CheckoutSession.Placement.InFlight) return null
        session.placement = CheckoutSession.Placement.InFlight
        Analytics.track(AnalyticsEvents.ORDER_ATTEMPTED)

        val orderLines = app.cartLines()
        val orderBill = app.bill(orderLines)
        val result = checkout.placeOrder(
            OrderRequest(
                idempotencyKey = session.idempotencyKey,
                lines = orderLines,
                bill = orderBill,
                addressId = address.id,
                addressText = address.label + " — " + address.line1,
                slotId = slot.id,
                payment = payment,
                slot = slot,
                instructionIds = session.instructionIds.toList()
            )
        )

        when (result) {
            is PlaceOrderResult.Placed -> {
                Analytics.track(
                    AnalyticsEvents.ORDER_SUCCESS,
                    mapOf("order_id" to result.order.id, "replayed" to result.replayed.toString())
                )
                // Gated: only the FIRST placement of this key may move money.
                if (!result.replayed) {
                    applyFirstPlacement(
                        app = app,
                        coins = coins,
                        memberships = memberships,
                        orderId = result.order.id,
                        coinsEarned = orderBill.coinsEarned,
                        itemTotalRupees = orderBill.itemTotal,
                        clubDiscountRupees = orderBill.clubDiscount
                    )
                }
                // Idempotent on repeat: showing the same order again is safe.
                app.lastOrder = result.order
                app.clearCart()
                app.checkout = null
                if (navigate) {
                    app.goHome()
                    app.navigate(Screen.OrderSuccess(result.order.id))
                }
            }

            is PlaceOrderResult.Failed -> {
                Analytics.track(
                    AnalyticsEvents.ORDER_FAILURE,
                    mapOf("retryable" to result.retryable.toString())
                )
                // Cart is deliberately left untouched so "Try again" reuses the
                // same key against the same basket.
                session.placement = CheckoutSession.Placement.Failed(result.reason, result.retryable)
            }

            is PlaceOrderResult.Rejected -> {
                session.validation = result.validation
                session.placement = CheckoutSession.Placement.Idle
            }
        }
        return result
    }

    /**
     * Side effects that must happen EXACTLY ONCE per real order.
     * Anything added here inherits the `!replayed` guard by construction.
     */
    private suspend fun applyFirstPlacement(
        app: TazzzoAppState,
        coins: CoinRepository,
        memberships: MembershipRepository,
        orderId: String,
        coinsEarned: Int,
        itemTotalRupees: Int,
        clubDiscountRupees: Int
    ) {
        coins.credit(coinsEarned, "Order $orderId cashback")
        app.user = app.user.copy(coinBalance = app.user.coinBalance + coinsEarned)

        // Club progress advances only on orders where the discount actually
        // applied — "eligible order" means exactly that, not "any order by a
        // member". The repository is ALSO idempotent on orderId, so this is
        // guarded twice: once by !replayed here, once by the order ledger
        // there. Progress that can be double-counted is progress a customer
        // will eventually notice is wrong.
        if (clubDiscountRupees > 0) {
            memberships.recordEligibleOrder(
                orderId = orderId,
                itemTotalRupees = itemTotalRupees,
                discountAppliedRupees = clubDiscountRupees,
                placedAtLabel = "Today"
            )
            app.membership = memberships.getState()
        }
    }
}
