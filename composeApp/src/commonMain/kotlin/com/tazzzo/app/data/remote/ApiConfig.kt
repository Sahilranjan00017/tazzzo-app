package com.tazzzo.app.data.remote

/**
 * Single place to wire the Tazzzo backend (JavaScript/Node microservices).
 *
 * When the backend is ready:
 *  1. Point these base URLs at your gateway / services.
 *  2. Implement the Remote* repositories in data/repository using a Ktor
 *     client (add `io.ktor:ktor-client-core` + `ktor-client-darwin` /
 *     `ktor-client-okhttp` in composeApp/build.gradle.kts).
 *  3. Swap the Mock* implementations in ServiceLocator for the Remote* ones.
 */
object ApiConfig {
    // API gateway (e.g. Express / NestJS gateway in front of the microservices)
    const val GATEWAY = "https://api.tazzzo.in"

    // Individual microservices behind the gateway
    const val AUTH_SERVICE = "$GATEWAY/auth/v1"          // OTP login, tokens
    const val CATALOG_SERVICE = "$GATEWAY/catalog/v1"    // categories, products, search
    const val ORDER_SERVICE = "$GATEWAY/orders/v1"       // cart checkout, order tracking
    const val COIN_SERVICE = "$GATEWAY/coins/v1"         // Tazzzo Coin balance & ledger
    const val VOICE_SERVICE = "$GATEWAY/voice/v1"        // voice commerce (coming soon)
    const val SUPPORT_SERVICE = "$GATEWAY/support/v1"    // help center, tickets

    // Endpoint sketch the JS services should expose:
    //   GET  /catalog/v1/categories
    //   GET  /catalog/v1/categories/{id}/products?subcategory=
    //   GET  /catalog/v1/search?q=
    //   POST /auth/v1/otp/request        { phone }
    //   POST /auth/v1/otp/verify         { phone, otp }
    //   POST /orders/v1/orders           { lines[], addressId, payment }
    //   GET  /orders/v1/orders?user=
    //   GET  /coins/v1/balance
    //   GET  /coins/v1/ledger
}
