package com.tazzzo.app.address

import com.tazzzo.app.data.address.AddressActionResult
import com.tazzzo.app.data.address.AddressBook
import com.tazzzo.app.data.address.AddressField
import com.tazzzo.app.data.address.AddressFailure
import com.tazzzo.app.data.address.AddressLabel
import com.tazzzo.app.data.address.AddressNotice
import com.tazzzo.app.data.address.AddressRules
import com.tazzzo.app.data.address.AddressServiceability
import com.tazzzo.app.data.address.BookState
import com.tazzzo.app.data.address.FieldError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddressBookTest {
    private var signedIn = true
    private fun TestScope.book(s: FakeAddressSource) = AddressBook(backgroundScope, s) { signedIn }
    private fun AddressBook.loaded() = assertIs<BookState.Loaded>(state.value)
    private fun seeded(vararg a: com.tazzzo.app.data.address.CustomerAddress) = FakeAddressSource().apply { server.addAll(a) }

    // ---- list -----------------------------------------------------------------------------------------------------

    @Test fun emptyListIsLoadedNotAFailure() = runTest {
        val s = FakeAddressSource(); val b = book(s)
        b.load(); assertEquals(BookState.Loading, b.state.value); runCurrent()
        assertEquals(emptyList(), b.loaded().addresses); assertNull(b.loaded().defaultId)
    }

    @Test fun populatedListKeepsServerOrderDefaultAndTriStateServiceability() = runTest {
        val s = seeded(
            ca("ADDR_aaaaaa1", isDefault = true), ca("ADDR_bbbbbb2", serviceability = AddressServiceability.NOT_SERVICEABLE),
            ca("ADDR_cccccc3", serviceability = AddressServiceability.UNKNOWN)
        )
        val b = book(s); b.load(); runCurrent()
        assertEquals(listOf("ADDR_aaaaaa1", "ADDR_bbbbbb2", "ADDR_cccccc3"), b.loaded().addresses.map { it.addressId })   // never re-sorted locally
        assertEquals("ADDR_aaaaaa1", b.loaded().defaultId)
        assertEquals(listOf(AddressServiceability.SERVICEABLE, AddressServiceability.NOT_SERVICEABLE, AddressServiceability.UNKNOWN), b.loaded().addresses.map { it.serviceability })
    }

    @Test fun aFailedListIsTypedAndRetryable() = runTest {
        val s = FakeAddressSource().apply { listError = http(503, "SERVICE_UNAVAILABLE") }; val b = book(s)
        b.load(); runCurrent()
        assertEquals(BookState.Failed(AddressFailure.Unavailable), b.state.value)
        s.listError = null; b.load(); runCurrent()                          // load() retries a failed book
        assertIs<BookState.Loaded>(b.state.value)
    }

    @Test fun notSignedInNeverCallsTheBackend() = runTest {
        signedIn = false; val s = seeded(ca()); val b = book(s)
        b.load(); runCurrent()
        assertEquals(BookState.SignedOut, b.state.value); assertEquals(0, s.calls.size)
        signedIn = true
    }

    @Test fun loadDoesNotRefetchWhileLoaded() = runTest {
        val s = seeded(ca()); val b = book(s)
        b.load(); runCurrent(); b.load(); runCurrent()
        assertEquals(1, s.callsOf("list"))
        b.refresh(); runCurrent(); assertEquals(2, s.callsOf("list"))
    }

    // ---- create ------------------------------------------------------------------------------------------------------------

    @Test fun createSendsOnceThenRefetchesTheAuthoritativeList() = runTest {
        val s = FakeAddressSource(); val b = book(s)
        val r = b.create(input()); runCurrent()
        assertEquals(AddressActionResult.Success, r)
        assertEquals(listOf("create", "list"), s.calls)
        assertEquals(1, b.loaded().addresses.size); assertTrue(b.loaded().addresses.single().isDefault)     // the first address is the server's default
    }

    @Test fun clientSideValidationFailsBeforeAnyRequest() = runTest {
        val s = FakeAddressSource(); val b = book(s)
        val r = assertIs<AddressActionResult.ValidationFailed>(b.create(input(postal = "060047", phone = "123")))
        assertEquals(FieldError.Invalid, r.errors[AddressField.POSTAL_CODE]); assertEquals(FieldError.Invalid, r.errors[AddressField.RECIPIENT_PHONE])
        assertTrue(s.calls.isEmpty())
    }

    @Test fun doubleSubmitIsGuardedSoOnlyOneCreateIsSent() = runTest {
        val s = FakeAddressSource().apply { createGate = CompletableDeferred() }; val b = book(s)
        val first = async { b.create(input()) }
        runCurrent()
        val second = b.create(input())
        assertEquals(AddressActionResult.Busy, second)
        assertTrue(b.busy.value)
        s.createGate!!.complete(Unit); runCurrent()
        assertEquals(AddressActionResult.Success, first.await())
        assertEquals(1, s.callsOf("create")); assertFalse(b.busy.value)
    }

    @Test fun theServerLimitIs409AndTheListIsRefreshed() = runTest {
        val s = seeded(ca()).apply { createError = http(409, "ADDRESS_LIMIT_REACHED") }; val b = book(s)
        assertEquals(AddressActionResult.LimitReached, b.create(input())); runCurrent()
        assertEquals(listOf("create", "list"), s.calls)
    }

    @Test fun aServerValidation400IsRejectedNotRetried() = runTest {
        val s = FakeAddressSource().apply { createError = http(400, "INVALID_REQUEST") }; val b = book(s)
        assertEquals(AddressActionResult.Rejected, b.create(input())); runCurrent()
        assertEquals(1, s.callsOf("create")); assertEquals(0, s.callsOf("list"))
    }

    @Test fun theUiLimitIsTenAndTheBookKnowsWhenItIsReached() = runTest {
        assertEquals(10, AddressRules.MAX_ADDRESSES)
        val s = seeded(*(1..10).map { ca("ADDR_x00000$it") }.toTypedArray()); val b = book(s)
        b.load(); runCurrent()
        assertTrue(b.loaded().atLimit)
    }

    // ---- ambiguous create ----------------------------------------------------------------------------------------------------------

    @Test fun anAmbiguousCreateIsNeverAutomaticallyRetried() = runTest {
        for (failure in listOf(ApiException(com.tazzzo.app.data.remote.ApiError.Timeout), ApiException(com.tazzzo.app.data.remote.ApiError.Network), http(500, "INTERNAL"))) {
            val s = FakeAddressSource().apply { createError = failure; createAppliedDespiteError = true }; val b = book(s)
            val r = b.create(input()); runCurrent()
            assertEquals(AddressActionResult.AmbiguousCreate, r)
            assertEquals(1, s.callsOf("create"), "POST must not be resubmitted automatically")
            assertEquals(AddressNotice.AmbiguousCreate, b.notice.value)
            assertEquals(listOf("create", "list"), s.calls)             // refetch and show; nothing else
        }
    }

    @Test fun anAmbiguousCreateDoesNotInferSuccessFromMatchingContentAndDeletesNothing() = runTest {
        // The server DID apply it AND an identical address already existed: content matching cannot tell, so none is attempted.
        val existing = ca("ADDR_exist01", isDefault = true)
        val s = seeded(existing).apply { createError = ApiException(com.tazzzo.app.data.remote.ApiError.Timeout); createAppliedDespiteError = true }
        val b = book(s)
        val r = b.create(input())                                       // same content as `existing`
        runCurrent()
        assertEquals(AddressActionResult.AmbiguousCreate, r)            // not Success
        assertEquals(2, b.loaded().addresses.size)                      // the refreshed truth is shown, duplicates and all
        assertEquals(0, s.callsOf("delete"))                            // a possible duplicate is never deleted for the customer
        assertEquals(1, s.callsOf("create"))
    }

    @Test fun anAmbiguousCreateThatDidNotApplyShowsTheUnchangedListAndLetsTheCustomerDecide() = runTest {
        val s = FakeAddressSource().apply { createError = ApiException(com.tazzzo.app.data.remote.ApiError.Timeout); createAppliedDespiteError = false }
        val b = book(s)
        assertEquals(AddressActionResult.AmbiguousCreate, b.create(input())); runCurrent()
        assertEquals(0, b.loaded().addresses.size)
        s.createError = null                                            // the customer decides to submit again
        assertEquals(AddressActionResult.Success, b.create(input())); runCurrent()
        assertEquals(2, s.callsOf("create"))
    }

    @Test fun aDefinite503OnCreateIsNotTreatedAsAmbiguous() = runTest {
        val s = FakeAddressSource().apply { createError = http(503, "SERVICE_UNAVAILABLE") }; val b = book(s)
        assertEquals(AddressActionResult.Failed(AddressFailure.Unavailable), b.create(input())); runCurrent()
        assertNull(b.notice.value); assertEquals(0, s.callsOf("list"))
    }

    // ---- update ----------------------------------------------------------------------------------------------------------------

    @Test fun updateSendsTheVersionItWasReadAtAndLearnsTheNewOneFromTheRefetch() = runTest {
        val a = ca("ADDR_aaaaaa1", isDefault = true, version = 3); val s = seeded(a); val b = book(s)
        b.load(); runCurrent()
        assertEquals(AddressActionResult.Success, b.update(a, input(city = "Mysuru"))); runCurrent()
        assertEquals("update:ADDR_aaaaaa1:v3", s.calls.first { it.startsWith("update") })
        assertEquals(4, b.loaded().addresses.single().version)                  // refetched, not guessed
        assertTrue(s.calls.last() == "list")
    }

    @Test fun clearingAnOptionalFieldIsAnExplicitNull() = runTest {
        val a = ca(line2 = "Sector 6"); val s = seeded(a); val b = book(s)
        b.update(a, input(line2 = "")); runCurrent()
        assertEquals(JsonNull, s.lastPatch!!["addressLine2"])
    }

    @Test fun anUnchangedEditMakesNoRequest() = runTest {
        val a = ca(); val s = seeded(a); val b = book(s)
        assertEquals(AddressActionResult.Success, b.update(a, input())); runCurrent()
        assertTrue(s.calls.isEmpty())
    }

    @Test fun aStaleUpdateRefetchesExplainsAndRequiresAnExplicitRetry() = runTest {
        val old = ca("ADDR_aaaaaa1", isDefault = true, version = 1); val s = seeded(old.copy(version = 5, city = "Pune")); val b = book(s)
        val r = b.update(old, input(city = "Mysuru")); runCurrent()
        assertEquals(AddressActionResult.Stale, r)
        assertEquals(AddressNotice.StaleRefreshed, b.notice.value)
        assertEquals("Pune", b.loaded().addresses.single().city)               // newer server data replaced the stale copy, not overwritten
        assertEquals(1, s.callsOf("update"))                                   // no automatic retry
        assertEquals(5, b.loaded().addresses.single().version)
    }

    @Test fun anUpdateToAnAddressThatVanishedIsNotFoundAndRefetches() = runTest {
        val a = ca(); val s = FakeAddressSource(); val b = book(s)
        assertEquals(AddressActionResult.NotFound, b.update(a, input(city = "Mysuru"))); runCurrent()
        assertEquals(AddressNotice.NotFoundRefreshed, b.notice.value)
    }

    @Test fun aMissingPreconditionIs428AClientBugNeverSilentlyRetried() = runTest {
        val a = ca(); val s = seeded(a).apply { updateError = http(428, "PRECONDITION_REQUIRED") }; val b = book(s)
        val r = b.update(a, input(city = "Mysuru")); runCurrent()
        assertEquals(AddressActionResult.Failed(AddressFailure.PreconditionRequired), r)
        assertEquals(1, s.callsOf("update"))
    }

    // ---- delete ----------------------------------------------------------------------------------------------------------------------

    @Test fun deleteSendsIfMatchVersionAndRefetches() = runTest {
        val a = ca("ADDR_aaaaaa1", isDefault = true, version = 2); val s = seeded(a); val b = book(s)
        assertEquals(AddressActionResult.Success, b.delete(a)); runCurrent()
        assertEquals(listOf("delete:ADDR_aaaaaa1:v2", "list"), s.calls)
        assertEquals(0, b.loaded().addresses.size)
    }

    @Test fun deletingTheDefaultLetsTheBackendPromoteAndTheBookLearnsItFromTheRefetch() = runTest {
        val d = ca("ADDR_aaaaaa1", isDefault = true); val other = ca("ADDR_bbbbbb2"); val s = seeded(d, other); val b = book(s)
        b.load(); runCurrent()
        b.delete(d); runCurrent()
        assertEquals("ADDR_bbbbbb2", b.loaded().defaultId)                     // chosen by the server, not by the client
        assertEquals(listOf("ADDR_bbbbbb2"), b.loaded().addresses.map { it.addressId })
    }

    @Test fun aStaleDeleteRefetchesAndExplains() = runTest {
        val old = ca(version = 1); val s = seeded(old.copy(version = 4)); val b = book(s)
        assertEquals(AddressActionResult.Stale, b.delete(old)); runCurrent()
        assertEquals(1, b.loaded().addresses.size)                              // still there: nothing was deleted
        assertEquals(AddressNotice.StaleRefreshed, b.notice.value); assertEquals(1, s.callsOf("delete"))
    }

    @Test fun deletingAnAlreadyRemovedAddressIs404AndRefetches() = runTest {
        val s = FakeAddressSource(); val b = book(s)
        assertEquals(AddressActionResult.NotFound, b.delete(ca())); runCurrent()
        assertEquals(AddressNotice.NotFoundRefreshed, b.notice.value)
    }

    // ---- default -------------------------------------------------------------------------------------------------------------------------

    @Test fun setDefaultNeedsNoIfMatchDoesNotTouchTheVersionAndRefetches() = runTest {
        val a = ca("ADDR_aaaaaa1", isDefault = true, version = 2); val c = ca("ADDR_bbbbbb2", version = 7); val s = seeded(a, c); val b = book(s)
        b.load(); runCurrent()
        assertEquals(AddressActionResult.Success, b.setDefault(c)); runCurrent()
        assertEquals(listOf("list", "default:ADDR_bbbbbb2", "list"), s.calls)
        assertEquals("ADDR_bbbbbb2", b.loaded().defaultId)
        assertEquals(7, b.loaded().addresses.first { it.addressId == "ADDR_bbbbbb2" }.version)   // unchanged: no client-side increment
        assertEquals("ADDR_bbbbbb2", b.loaded().addresses.first().addressId)                       // server's new ordering, not reconstructed
    }

    @Test fun defaultSwitchingBackAndForthFollowsTheServer() = runTest {
        val a = ca("ADDR_aaaaaa1", isDefault = true); val c = ca("ADDR_bbbbbb2"); val s = seeded(a, c); val b = book(s)
        b.load(); runCurrent()
        b.setDefault(c); runCurrent(); assertEquals("ADDR_bbbbbb2", b.loaded().defaultId)
        b.setDefault(a); runCurrent(); assertEquals("ADDR_aaaaaa1", b.loaded().defaultId)
    }

    @Test fun aFailedSetDefaultLeavesTheListAndReportsTheFailure() = runTest {
        val a = ca(isDefault = true); val s = seeded(a).apply { defaultError = ApiException(com.tazzzo.app.data.remote.ApiError.Network) }; val b = book(s)
        b.load(); runCurrent()
        assertEquals(AddressActionResult.Failed(AddressFailure.Network), b.setDefault(a)); runCurrent()
        assertEquals(1, b.loaded().addresses.size)
    }

    // ---- refetch failure + sign-out ---------------------------------------------------------------------------------------------------

    @Test fun ifTheRefetchAfterASuccessfulMutationFailsTheBookShowsRetryableErrorNotAStaleList() = runTest {
        val s = FakeAddressSource(); val b = book(s)
        b.load(); runCurrent()
        s.listError = ApiException(com.tazzzo.app.data.remote.ApiError.Network)
        assertEquals(AddressActionResult.Success, b.create(input())); runCurrent()
        assertEquals(BookState.Failed(AddressFailure.Network), b.state.value)
    }

    @Test fun signOutWipesEverythingAndDiscardsInFlightWork() = runTest {
        val s = seeded(ca("ADDR_aaaaaa1", isDefault = true)).apply { createGate = CompletableDeferred() }; val b = book(s)
        b.load(); runCurrent()
        val pending = async { b.create(input()) }
        runCurrent()
        b.signOut()
        assertEquals(BookState.SignedOut, b.state.value); assertNull(b.notice.value); assertFalse(b.busy.value)
        s.createGate!!.complete(Unit); runCurrent(); pending.await()
        assertEquals(BookState.SignedOut, b.state.value, "a late answer must not resurrect the book")
    }

    @Test fun mutationsRequireASession() = runTest {
        signedIn = false; val s = FakeAddressSource(); val b = book(s)
        assertEquals(AddressActionResult.AuthRequired, b.create(input()))
        assertTrue(s.calls.isEmpty()); signedIn = true
    }

    @Test fun anUnauthenticatedAnswerAsksForLogin() = runTest {
        val s = FakeAddressSource().apply { createError = http(401, "UNAUTHENTICATED") }; val b = book(s)
        assertEquals(AddressActionResult.AuthRequired, b.create(input()))
    }

    @Test fun theBookNeverExposesPersonalDataInItsToString() = runTest {
        val s = seeded(ca(isDefault = true)); val b = book(s)
        b.load(); runCurrent()
        for (secret in listOf("Asha", "9876543210", "14th Main", "560102")) assertFalse(secret in b.state.value.toString(), secret)
        assertEquals(AddressLabel.HOME, b.loaded().addresses.single().label)
    }
}
