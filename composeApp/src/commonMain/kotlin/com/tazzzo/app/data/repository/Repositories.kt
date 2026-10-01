package com.tazzzo.app.data.repository

import com.tazzzo.app.data.MockCatalog
import com.tazzzo.app.data.search.SearchEngine
import com.tazzzo.app.data.model.*
import kotlinx.coroutines.delay

/**
 * Repository contracts. UI only talks to these interfaces, so switching from
 * the Mock* implementations to real microservice-backed ones (see
 * data/remote/ApiConfig.kt) is a one-line change in ServiceLocator.
 */
interface CatalogRepository {
    suspend fun getCategories(): List<Category>
    /** Backend contract: GET /catalog/v1/products/{id} — see docs/BACKEND_CONTRACTS.md */
    suspend fun getProduct(id: String): Product?
    suspend fun getBanners(): List<PromoBanner>
    suspend fun getBestsellers(): List<Product>
    /** Genuinely discounted SKUs, deepest rupee saving first. Maps to a
     *  `sort=discount&has_discount=true` listing query once one exists. */
    suspend fun getDeals(): List<Product>
    /** SKU counts per category and sub-category id. Derived, never authored. */
    suspend fun getCounts(): Map<String, Int>
    suspend fun getProducts(categoryId: String, subcategoryId: String? = null): List<Product>
    suspend fun search(query: String): List<Product>
}

interface OrderRepository {
    suspend fun placeOrder(
        lines: List<CartLine>,
        bill: BillSummary,
        address: String,
        payment: com.tazzzo.app.data.model.PaymentMethodKind? = null,
        slot: com.tazzzo.app.data.model.DeliverySlot? = null,
        instructionIds: List<String> = emptyList()
    ): Order
    suspend fun getOrders(): List<Order>
}

interface CoinRepository {
    suspend fun getBalance(): Int
    suspend fun getLedger(): List<CoinTransaction>
    suspend fun credit(amount: Int, title: String)
}

// ---------------------------------------------------------------------------
// Mock implementations (in-memory, small delay to simulate the network)
// ---------------------------------------------------------------------------

internal const val FAKE_LATENCY_MS = 350L

class MockCatalogRepository : CatalogRepository {
    private val engine by lazy { SearchEngine(MockCatalog.products, MockCatalog.categories) }

    private var failedOnce = false
    private fun maybeFailForDemo() {
        if (com.tazzzo.app.isDemoFailLoadEnabled() && !failedOnce) {
            failedOnce = true
            throw com.tazzzo.app.data.remote.ApiException(com.tazzzo.app.data.remote.ApiError.Network)
        }
    }

    override suspend fun getCategories(): List<Category> { delay(FAKE_LATENCY_MS); return MockCatalog.categories }
    override suspend fun getProduct(id: String): Product? { delay(FAKE_LATENCY_MS); return MockCatalog.products.find { it.id == id } }
    override suspend fun getBanners(): List<PromoBanner> { maybeFailForDemo(); delay(FAKE_LATENCY_MS / 2); return MockCatalog.banners }
    override suspend fun getBestsellers(): List<Product> { delay(FAKE_LATENCY_MS); return MockCatalog.bestsellers() }
    override suspend fun getDeals(): List<Product> { delay(FAKE_LATENCY_MS); return MockCatalog.deals() }
    override suspend fun getCounts(): Map<String, Int> { delay(FAKE_LATENCY_MS / 2); return MockCatalog.counts() }
    override suspend fun getProducts(categoryId: String, subcategoryId: String?): List<Product> {
        delay(FAKE_LATENCY_MS); return MockCatalog.productsFor(categoryId, subcategoryId)
    }
    override suspend fun search(query: String): List<Product> { delay(200); return engine.search(query) }
}

class MockOrderRepository : OrderRepository {
    /**
     * Seed history covering the statuses the model supports, so the Orders UI
     * is exercised against real variety (multiple orders, different statuses,
     * different item counts) rather than a single happy-path row.
     * These are DEVELOPMENT FIXTURES, replaced wholesale by the orders service.
     */
    private val orders = mutableListOf(
        Order(
            id = "TZ100482",
            lines = listOf(
                CartLine(MockCatalog.products.first { it.id == "p34" }, 2),
                CartLine(MockCatalog.products.first { it.id == "p8" }, 1)
            ),
            bill = BillSummary(149, 158, 0, 5, 3, 154),
            status = OrderStatus.DELIVERED,
            placedAtLabel = "Aug 20, 7:42 PM",
            address = "Home — 22, 14th Main, HSR Layout",
            payment = com.tazzzo.app.data.model.PaymentMethodKind.COD
        ),
        Order(
            id = "TZ100481",
            lines = listOf(
                CartLine(MockCatalog.products.first { it.id == "p14" }, 1),
                CartLine(MockCatalog.products.first { it.id == "p18" }, 1),
                CartLine(MockCatalog.products.first { it.id == "p26" }, 2)
            ),
            bill = BillSummary(579, 684, 0, 5, 11, 584),
            status = OrderStatus.ON_THE_WAY,
            placedAtLabel = "Today, 5:10 PM",
            address = "Work — Tower B, Ecospace, Bellandur",
            payment = com.tazzzo.app.data.model.PaymentMethodKind.COD
        ),
        Order(
            id = "TZ100480",
            lines = listOf(CartLine(MockCatalog.products.first { it.id == "p43" }, 1)),
            bill = BillSummary(99, 132, 25, 5, 1, 129),
            status = OrderStatus.PACKED,
            placedAtLabel = "Today, 6:02 PM",
            address = "Home — 22, 14th Main, HSR Layout",
            payment = com.tazzzo.app.data.model.PaymentMethodKind.COD
        )
    )

    override suspend fun placeOrder(
        lines: List<CartLine>,
        bill: BillSummary,
        address: String,
        payment: com.tazzzo.app.data.model.PaymentMethodKind?,
        slot: com.tazzzo.app.data.model.DeliverySlot?,
        instructionIds: List<String>
    ): Order {
        delay(600)
        val order = Order(
            id = "TZ${100483 + orders.size}",
            lines = lines, bill = bill, status = OrderStatus.PLACED,
            placedAtLabel = "Just now", address = address, payment = payment,
            slot = slot, instructionIds = instructionIds
        )
        orders.add(0, order)
        return order
    }

    override suspend fun getOrders(): List<Order> { delay(FAKE_LATENCY_MS); return orders.toList() }
}

class MockCoinRepository : CoinRepository {
    private var balance = 40
    private val ledger = mutableListOf(
        CoinTransaction("c1", "Welcome bonus", 25, "Aug 18"),
        CoinTransaction("c2", "Order TZ100482 cashback", 3, "Aug 20"),
        CoinTransaction("c3", "Referral: friend joined", 12, "Aug 21")
    )
    override suspend fun getBalance(): Int { delay(150); return balance }
    override suspend fun getLedger(): List<CoinTransaction> { delay(250); return ledger.toList() }
    override suspend fun credit(amount: Int, title: String) {
        balance += amount
        ledger.add(0, CoinTransaction("c${ledger.size + 1}", title, amount, "Today"))
    }
}

/** Poor-man's DI — swap Mock* for Remote* here when the JS microservices land. */
object ServiceLocator {
    val catalog: CatalogRepository = MockCatalogRepository()

    // --- authentication (PR-03A) --------------------------------------------
    // Lazy: building the secure store needs the platform (an Android Context),
    // and loading ServiceLocator in a plain-JVM unit test must stay possible.
    private val authScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    /** Auth calls: no token provider, no recovery hook — they must never recurse into refresh. */
    private val authClient: com.tazzzo.app.data.remote.ApiClient by lazy { com.tazzzo.app.data.remote.ApiClient() }
    private val authRemote: com.tazzzo.app.data.auth.RemoteAuthDataSource by lazy {
        com.tazzzo.app.data.auth.RemoteAuthDataSource(authClient)
    }

    val secureTokenStore: com.tazzzo.app.data.auth.SecureTokenStore by lazy {
        com.tazzzo.app.data.auth.BlobSecureTokenStore(com.tazzzo.app.data.auth.createSecureBlobStore()).also {
            // A reinstall must not inherit the previous install's (Keychain) credentials.
            com.tazzzo.app.data.auth.AuthInstallGuard.clearStaleCredentialsOnFreshInstall(
                com.tazzzo.app.data.local.PersistentStore(), it
            )
        }
    }

    val authSession: com.tazzzo.app.data.auth.AuthSessionManager by lazy {
        com.tazzzo.app.data.auth.AuthSessionManager(authRemote, secureTokenStore, authScope)
    }

    /** The gateway client for authenticated features: attaches the token and recovers from one 401. */
    val apiClient: com.tazzzo.app.data.remote.ApiClient by lazy {
        com.tazzzo.app.data.remote.ApiClient(tokenProvider = authSession, recovery = authSession)
    }

    val auth: com.tazzzo.app.data.auth.AuthRepository by lazy {
        com.tazzzo.app.data.auth.RemoteAuthRepository(authRemote, authSession)
    }
    val orders: OrderRepository = MockOrderRepository()
    val coins: CoinRepository = MockCoinRepository()
    val addresses: AddressRepository = MockAddressRepository()
    val checkout: CheckoutRepository = MockCheckoutRepository(catalog, orders)
    val support: SupportRepository = MockSupportRepository()

    /**
     * Club membership. Local today; the same interface fronts the backend
     * later, where the idempotency guarantees must be enforced server-side.
     */
    val membership: MembershipRepository =
        LocalMembershipRepository(com.tazzzo.app.data.local.PersistentStore())

    /**
     * Payment. [MockPaymentGateway] until Razorpay + a backend exist — it is
     * a TEST gateway and says so on every order and result it produces.
     */
    val payments: PaymentGateway = MockPaymentGateway()
}
