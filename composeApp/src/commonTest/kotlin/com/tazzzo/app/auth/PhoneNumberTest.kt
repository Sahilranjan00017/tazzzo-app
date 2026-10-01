package com.tazzzo.app.auth

import com.tazzzo.app.data.auth.PhoneNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PhoneNumberTest {
    @Test fun validIndianNumbersParse() {
        for (n in listOf("6000000000", "7123456789", "8123456789", "9876543210")) assertNotNull(PhoneNumber.parse(n), n)
    }

    @Test fun leadingDigitsBelowSixAreRejected() {
        for (n in listOf("5876543210", "0876543210", "1234567890", "4999999999")) assertNull(PhoneNumber.parse(n), n)
    }

    @Test fun wrongLengthIsRejected() {
        assertNull(PhoneNumber.parse("987654321"))
        assertNull(PhoneNumber.parse(""))
        // Longer input is truncated by sanitize() to 10 digits, so it is the UI that bounds length.
        assertEquals(10, PhoneNumber.sanitize("98765432109999").length)
    }

    @Test fun e164NormalisationIsPlus91AndTenDigits() {
        assertEquals("+919876543210", PhoneNumber.parse("9876543210")!!.e164)
        assertEquals("+91 98765 43210", PhoneNumber.parse("9876543210")!!.display)
    }

    @Test fun pastedFormsReduceToTenLocalDigits() {
        assertEquals("9876543210", PhoneNumber.sanitize("+91 98765-43210"))
        assertEquals("9876543210", PhoneNumber.sanitize("919876543210"))
        assertEquals("9876543210", PhoneNumber.sanitize("09876543210"))
        assertEquals("9876543210", PhoneNumber.sanitize(" 98 76 54 32 10 "))
    }

    @Test fun lettersAndSymbolsAreDropped() {
        assertEquals("98", PhoneNumber.sanitize("9a8-"))
    }

    @Test fun toStringNeverRevealsTheNumber() {
        val p = PhoneNumber.parse("9876543210")!!
        assertFalse(p.toString().contains("9876"))
    }
}
