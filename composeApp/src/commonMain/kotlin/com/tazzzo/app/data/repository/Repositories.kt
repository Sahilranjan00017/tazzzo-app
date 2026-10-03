package com.tazzzo.app.data.repository

import com.tazzzo.app.data.MockCatalog
import com.tazzzo.app.data.search.SearchEngine
import com.tazzzo.app.data.model.*
import kotlinx.coroutines.delay

/**
 * Repository contracts. UI only talks to these interfaces, so switching from
 * the Mock* implementations to real microservice-backed ones (see
 * config/AppEnvironment.kt for the gateway host) is a one-line change in ServiceLocator.
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
            bill = BillSummary(Money.ofRupees(149), Money.ofRupees(158), Money.ofRupees(0), Money.ofRupees(5), 3, Money.ofRupees(154)),
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
            bill = BillSummary(Money.ofRupees(579), Money.ofRupees(684), Money.ofRupees(0), Money.ofRupees(5), 11, Money.ofRupees(584)),
            status = OrderStatus.ON_THE_WAY,
            placedAtLabel = "Today, 5:10 PM",
            address = "Work — Tower B, Ecospace, Bellandur",
            payment = com.tazzzo.app.data.model.PaymentMethodKind.COD
        ),
        Order(
            id = "TZ100480",
            lines = listOf(CartLine(MockCatalog.products.first { it.id == "p43" }, 1)),
            bill = BillSummary(Money.ofRupees(99), Money.ofRupees(132), Money.ofRupees(25), Money.ofRupees(5), 1, Money.ofRupees(129)),
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

/**
 * Stands in for the mock catalogue in REMOTE mode. Every call fails loudly (never returns mock data),
 * so a stray legacy call site surfaces as an error state and a failing test instead of fake commerce.
 */
internal object RemoteModeAddressGuard : AddressRepository {
    override suspend fun getAddresses(): List<Address> =
        throw UnsupportedOperationException("The mock address list is not available in REMOTE mode")
    override suspend fun addAddress(label: String, line1: String, line2: String, pincode: String): Address =
        throw UnsupportedOperationException("The mock address list is not available in REMOTE mode")
}

/** REMOTE mode has no checkout yet: a server cart must never reach the mock checkout / order placement. */
internal object RemoteModeCheckoutGuard : CheckoutRepository {
    private fun refuse(): Nothing =
        throw UnsupportedOperationException("The mock checkout is not available in REMOTE mode")
    override suspend fun getSlots(addressId: String): List<DeliverySlot> = refuse()
    override suspend fun getPaymentMethods(): List<PaymentMethod> = refuse()
    override suspend fun validateCart(lines: List<CartLine>): CartValidation = refuse()
    override suspend fun placeOrder(request: OrderRequest): PlaceOrderResult = refuse()
}

/** REMOTE must never answer with the three seeded fake orders: real orders come from the OrderStore. */
internal object RemoteModeOrderGuard : OrderRepository {
    private fun refuse(): Nothing = throw UnsupportedOperationException("The mock orders are not available in REMOTE mode")
    override suspend fun placeOrder(lines: List<CartLine>, bill: BillSummary, address: String, payment: PaymentMethodKind?, slot: DeliverySlot?, instructionIds: List<String>): Order = refuse()
    override suspend fun getOrders(): List<Order> = refuse()
}

/** REMOTE has no coins contract: no fake balance and no local credit. */
internal object RemoteModeCoinGuard : CoinRepository {
    private fun refuse(): Nothing = throw UnsupportedOperationException("The mock coins are not available in REMOTE mode")
    override suspend fun getBalance(): Int = refuse()
    override suspend fun getLedger(): List<CoinTransaction> = refuse()
    override suspend fun credit(amount: Int, title: String) = refuse()
}

internal object RemoteModeCatalogGuard : CatalogRepository {
    private fun refuse(): Nothing =
        throw UnsupportedOperationException("The mock catalogue is not available in REMOTE catalogue mode")
    override suspend fun getCategories(): List<Category> = refuse()
    override suspend fun getProduct(id: String): Product? = refuse()
    override suspend fun getBanners(): List<PromoBanner> = refuse()
    override suspend fun getBestsellers(): List<Product> = refuse()
    override suspend fun getDeals(): List<Product> = refuse()
    override suspend fun getCounts(): Map<String, Int> = refuse()
    override suspend fun getProducts(categoryId: String, subcategoryId: String?): List<Product> = refuse()
    override suspend fun search(query: String): List<Product> = refuse()
}

/** Poor-man's DI — swap Mock* for Remote* here when the JS microservices land. */
object ServiceLocator {
    private val mockCatalog: CatalogRepository = MockCatalogRepository()

    /**
     * The LEGACY mock-era catalogue. Only available in explicit [com.tazzzo.app.data.catalog.CatalogMode.MOCK]
     * (debug / demo / tests). In REMOTE mode — the only mode a release build can be in — this is a
     * guard that FAILS rather than answering with mock products: a screen that still reaches for it
     * shows its error state, never a silent mock/real mixture. Real screens use [remoteCatalog].
     */
    val catalog: CatalogRepository
        get() = if (catalogMode == com.tazzzo.app.data.catalog.CatalogMode.MOCK) mockCatalog else RemoteModeCatalogGuard

    // --- authentication (PR-03A) --------------------------------------------
    // Lazy: building the secure store needs the platform (an Android Context),
    // and loading ServiceLocator in a plain-JVM unit test must stay possible.
    internal val authScope = kotlinx.coroutines.CoroutineScope(
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

    // --- remote catalogue + serviceability (PR-04A: data foundation, NOT wired to any screen yet) ---
    // Screens still read `catalog` (mock) above; PR-04C switches them using CatalogSource.current.
    private val catalogClient: com.tazzzo.app.data.remote.ApiClient by lazy { com.tazzzo.app.data.remote.ApiClient() }
    private val persistentStoreForCatalog: com.tazzzo.app.data.local.PersistentStore by lazy { com.tazzzo.app.data.local.PersistentStore() }
    private val installationId: () -> com.tazzzo.app.data.catalog.InstallationId = {
        com.tazzzo.app.data.catalog.InstallationId.getOrCreate(persistentStoreForCatalog)
    }
    val catalogMode: com.tazzzo.app.data.catalog.CatalogMode get() = com.tazzzo.app.data.catalog.CatalogSource.current
    val catalogCapabilities: com.tazzzo.app.data.catalog.CatalogCapabilities get() =
        com.tazzzo.app.data.catalog.CatalogCapabilities.forMode(catalogMode)
    val remoteCatalog: com.tazzzo.app.data.catalog.CatalogReader by lazy {
        val source = com.tazzzo.app.data.catalog.RemoteCatalogDataSource(catalogClient, installationId)
        com.tazzzo.app.data.catalog.CatalogReader(source, com.tazzzo.app.data.catalog.TaxonomyCache(source))
    }
    val launchContext: com.tazzzo.app.data.catalog.LaunchContext by lazy {
        com.tazzzo.app.data.catalog.LaunchContext(
            com.tazzzo.app.data.catalog.PersistentPinStore(persistentStoreForCatalog),
            com.tazzzo.app.data.catalog.RemoteServiceabilityDataSource(catalogClient, installationId),
            authScope
        )
    }

    // --- real customer addresses (PR-05): in memory only, backend is the authority ---
    val addressBook: com.tazzzo.app.data.address.AddressBook by lazy {
        com.tazzzo.app.data.address.AddressBook(
            authScope, com.tazzzo.app.data.address.RemoteAddressDataSource(apiClient)
        ) { authSession.isAuthenticated }
    }

    /** The single owner of the selected delivery address; writes the ONE active PIN held by [launchContext]. */
    val deliveryLocation: com.tazzzo.app.data.address.DeliveryLocation by lazy {
        com.tazzzo.app.data.address.DeliveryLocation(
            authScope, launchContext,
            com.tazzzo.app.data.address.PersistentSelectionStore(persistentStoreForCatalog), addressBook
        )
    }

    // --- real server cart (PR-06): in memory only, the backend owns it ---
    /** One thread at a time: the cart store's state is confined to this dispatcher. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val cartScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default.limitedParallelism(1)
    )
    val cart: com.tazzzo.app.data.cart.CartStore by lazy {
        com.tazzzo.app.data.cart.CartStore(
            scope = cartScope,
            source = com.tazzzo.app.data.cart.RemoteCartDataSource(apiClient),
            isAuthenticated = { authSession.isAuthenticated },
            addressId = { deliveryLocation.selectedAddressId.value },
            onAddressSuspect = { addressBook.refresh() }
        )
    }

    // --- real checkout quote (PR-07): in memory only, the backend quote is authoritative ---
    val checkoutAddresses: com.tazzzo.app.data.checkout.CheckoutAddressSource by lazy {
        com.tazzzo.app.data.checkout.DeliveryAddressSource(cartScope, deliveryLocation.selectedAddressId, addressBook.state)
    }
    val checkoutQuote: com.tazzzo.app.data.checkout.CheckoutQuoteStore by lazy {
        com.tazzzo.app.data.checkout.CheckoutQuoteStore(
            scope = cartScope,
            source = com.tazzzo.app.data.checkout.RemoteCheckoutDataSource(apiClient),
            cart = cart,
            addresses = checkoutAddresses,
            isAuthenticated = { authSession.isAuthenticated },
            onAddressSuspect = { addressBook.refresh() }
        )
    }

    // --- real COD order (PR-08): in memory, plus ONE opaque pending-quote recovery record ---
    val orderStore: com.tazzzo.app.data.order.OrderStore by lazy {
        com.tazzzo.app.data.order.OrderStore(
            scope = cartScope,
            source = com.tazzzo.app.data.order.RemoteOrderDataSource(apiClient),
            quotes = checkoutQuote,
            cart = cart,
            pending = com.tazzzo.app.data.order.PersistentPendingOrderStore(persistentStoreForCatalog),
            isAuthenticated = { authSession.isAuthenticated },
            launchEnabled = { com.tazzzo.app.data.order.OrderLaunchGate.enabled(catalogCapabilities) }
        )
    }

    /** Wires login/logout and the delivery location to [cart], [checkoutQuote] and [orderStore]. REMOTE only. */
    fun startCartBinding(initiallyAuthenticated: Boolean) {
        com.tazzzo.app.data.order.OrderSessionBinding(cartScope, authSession.active, orderStore).start(initiallyAuthenticated)
        com.tazzzo.app.data.checkout.CheckoutSessionBinding(cartScope, authSession.active, checkoutQuote).start()
        com.tazzzo.app.data.cart.CartSessionBinding(
            cartScope, authSession.active, deliveryLocation.selectedAddressId, launchContext.pin, cart
        ).start()
    }

    val auth: com.tazzzo.app.data.auth.AuthRepository by lazy {
        com.tazzzo.app.data.auth.RemoteAuthRepository(authRemote, authSession)
    }
    private val mockOrders: OrderRepository = MockOrderRepository()
    private val mockCoins: CoinRepository = MockCoinRepository()

    /** The LEGACY mock orders. MOCK mode only; in REMOTE it REFUSES (real orders come from [orderStore]). */
    val orders: OrderRepository
        get() = if (catalogMode == com.tazzzo.app.data.catalog.CatalogMode.MOCK) mockOrders else RemoteModeOrderGuard

    /** The LEGACY mock coins. MOCK mode only; REMOTE has no coins contract. */
    val coins: CoinRepository
        get() = if (catalogMode == com.tazzzo.app.data.catalog.CatalogMode.MOCK) mockCoins else RemoteModeCoinGuard
    private val mockAddresses: AddressRepository = MockAddressRepository()

    /**
     * The LEGACY mock-era address list. Available only in explicit MOCK mode. In REMOTE mode it REFUSES
     * (never answers with the three mock addresses): real addresses come from [addressBook].
     */
    val addresses: AddressRepository
        get() = if (catalogMode == com.tazzzo.app.data.catalog.CatalogMode.MOCK) mockAddresses else RemoteModeAddressGuard
    private val mockCheckout: CheckoutRepository = MockCheckoutRepository(mockCatalog, mockOrders)

    /** The LEGACY mock checkout. MOCK mode only; in REMOTE mode it REFUSES (the server cart has no checkout yet). */
    val checkout: CheckoutRepository
        get() = if (catalogMode == com.tazzzo.app.data.catalog.CatalogMode.MOCK) mockCheckout else RemoteModeCheckoutGuard
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
