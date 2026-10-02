package com.tazzzo.app.address

import com.tazzzo.app.data.address.AddressBodies
import com.tazzzo.app.data.address.AddressField
import com.tazzzo.app.data.address.AddressLabel
import com.tazzzo.app.data.address.AddressValidation
import com.tazzzo.app.data.address.AddressValidator
import com.tazzzo.app.data.address.FieldError
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddressValidationTest {
    private fun errors(i: com.tazzzo.app.data.address.AddressInput) = (AddressValidator.validate(i) as? AddressValidation.Invalid)?.errors
    private fun valid(i: com.tazzzo.app.data.address.AddressInput) = (AddressValidator.validate(i) as AddressValidation.Valid).address

    // ---- label ----------------------------------------------------------------------------------------

    @Test fun everyBackendLabelIsValidAndRoundTripsToItsWireValue() {
        for (l in AddressLabel.entries) assertEquals(l.wire, valid(input(label = l)).label.wire)
        assertEquals(listOf("HOME", "WORK", "OTHER"), AddressLabel.entries.map { it.wire })
        assertEquals(listOf("Home", "Work", "Other"), AddressLabel.entries.map { it.display })
    }

    @Test fun aMissingLabelIsRequiredAndThereIsNoCustomLabel() {
        assertEquals(FieldError.Required, errors(input(label = null))!![AddressField.LABEL])
        assertNull(AddressLabel.fromWire("Parents"))
        assertEquals(AddressLabel.WORK, AddressLabel.fromWire("work"))      // case-insensitive, like the backend
    }

    // ---- recipient name ------------------------------------------------------------------------------------

    @Test fun nameLimitsMatchTheBackend() {
        assertEquals("A".repeat(80), valid(input(name = "A".repeat(80))).recipientName)
        assertEquals(FieldError.TooLong, errors(input(name = "A".repeat(81)))!![AddressField.RECIPIENT_NAME])
        assertEquals(FieldError.Required, errors(input(name = "   "))!![AddressField.RECIPIENT_NAME])
        assertEquals("Asha", valid(input(name = "  Asha  ")).recipientName)   // trimmed
    }

    @Test fun controlCharactersAreRejectedAndUnicodeIsAllowed() {
        assertEquals(FieldError.Invalid, errors(input(name = "As\u0000ha"))!![AddressField.RECIPIENT_NAME])
        assertEquals(FieldError.Invalid, errors(input(line1 = "Line\none"))!![AddressField.ADDRESS_LINE1])
        assertEquals("आशा राव", valid(input(name = "आशा राव")).recipientName)
    }

    @Test fun lengthsCountUnicodeCodePointsNotUtf16Units() {
        val eighty = "😀".repeat(80)                                       // 80 code points, 160 UTF-16 units
        assertEquals(80, AddressValidator.codePoints(eighty))
        assertEquals(eighty, valid(input(name = eighty)).recipientName)
        assertEquals(FieldError.TooLong, errors(input(name = "😀".repeat(81)))!![AddressField.RECIPIENT_NAME])
    }

    // ---- recipient phone ---------------------------------------------------------------------------------------------

    @Test fun everyAcceptedPhoneShapeNormalisesToCanonicalPlus91() {
        for (raw in listOf("+919876543210", "9876543210", "09876543210", "98765 43210", "98765-43210", " +91 98765 43210 ")) {
            assertEquals("+919876543210", valid(input(phone = raw)).recipientPhone, raw)
        }
    }

    @Test fun invalidPhonesAreRejected() {
        for (raw in listOf("5876543210", "987654321", "98765432100", "+14155550123", "abcdefghij", "+9198765", "0098765432", "9876a43210")) {
            assertEquals(FieldError.Invalid, errors(input(phone = raw))!![AddressField.RECIPIENT_PHONE], raw)
        }
        assertEquals(FieldError.Required, errors(input(phone = " "))!![AddressField.RECIPIENT_PHONE])
    }

    // ---- postal code -------------------------------------------------------------------------------------------------

    @Test fun validPins() {
        for (p in listOf("560047", "110001", "999999")) assertEquals(p, valid(input(postal = p)).postalCode.value)
        assertEquals("560102", valid(input(postal = " 560102 ")).postalCode.value)
    }

    @Test fun aLeadingZeroPinIsInvalid() {
        assertEquals(FieldError.Invalid, errors(input(postal = "060047"))!![AddressField.POSTAL_CODE])
        assertEquals(FieldError.Invalid, errors(input(postal = "000000"))!![AddressField.POSTAL_CODE])
    }

    @Test fun wrongLengthOrNonDigitPinsAreInvalid() {
        for (p in listOf("56004", "5600477", "56004a", "560 047", "+56004")) assertEquals(FieldError.Invalid, errors(input(postal = p))!![AddressField.POSTAL_CODE], p)
        assertEquals(FieldError.Required, errors(input(postal = ""))!![AddressField.POSTAL_CODE])
    }

    // ---- text limits ------------------------------------------------------------------------------------------------------

    @Test fun textLimitsMatchTheBackendExactly() {
        assertEquals("a".repeat(160), valid(input(line1 = "a".repeat(160))).addressLine1)
        assertEquals(FieldError.TooLong, errors(input(line1 = "a".repeat(161)))!![AddressField.ADDRESS_LINE1])
        assertEquals("a".repeat(160), valid(input(line2 = "a".repeat(160))).addressLine2)
        assertEquals(FieldError.TooLong, errors(input(line2 = "a".repeat(161)))!![AddressField.ADDRESS_LINE2])
        assertEquals("a".repeat(120), valid(input(landmark = "a".repeat(120))).landmark)
        assertEquals(FieldError.TooLong, errors(input(landmark = "a".repeat(121)))!![AddressField.LANDMARK])
        assertEquals(FieldError.TooLong, errors(input(city = "a".repeat(81)))!![AddressField.CITY])
        assertEquals(FieldError.TooLong, errors(input(state = "a".repeat(81)))!![AddressField.STATE])
        assertEquals(FieldError.Required, errors(input(line1 = ""))!![AddressField.ADDRESS_LINE1])
        assertEquals(FieldError.Required, errors(input(city = ""))!![AddressField.CITY])
        assertEquals(FieldError.Required, errors(input(state = ""))!![AddressField.STATE])
    }

    @Test fun optionalFieldsBlankBecomeNullNotEmptyStrings() {
        val v = valid(input(line2 = "   ", landmark = ""))
        assertNull(v.addressLine2); assertNull(v.landmark)
    }

    @Test fun everyBadFieldIsReportedAtOnce() {
        val e = errors(input(label = null, name = "", phone = "x", line1 = "", city = "", state = "", postal = "1"))!!
        assertEquals(setOf(AddressField.LABEL, AddressField.RECIPIENT_NAME, AddressField.RECIPIENT_PHONE, AddressField.ADDRESS_LINE1,
            AddressField.CITY, AddressField.STATE, AddressField.POSTAL_CODE), e.keys)
    }

    // ---- request bodies ----------------------------------------------------------------------------------------------------

    @Test fun theCreateBodyHasExactlyTheBackendFieldsAndNeverCoordinatesOrIdentity() {
        val body = AddressBodies.create(valid(input(line2 = "Sector 6", landmark = "Near park")))
        assertEquals(
            setOf("label", "recipientName", "recipientPhone", "addressLine1", "addressLine2", "landmark", "city", "state", "postalCode"),
            body.keys
        )
        assertEquals(JsonPrimitive("HOME"), body["label"]); assertEquals(JsonPrimitive("+919876543210"), body["recipientPhone"])
        for (forbidden in listOf("latitude", "longitude", "customerId", "isDefault", "version", "addressId", "fulfillmentLocationId")) {
            assertFalse(forbidden in body.keys, forbidden)
        }
    }

    @Test fun optionalFieldsAreOmittedWhenBlank() {
        val body = AddressBodies.create(valid(input()))
        assertFalse("addressLine2" in body.keys); assertFalse("landmark" in body.keys)
    }

    @Test fun thePatchBodyContainsOnlyWhatChanged() {
        val original = ca()
        val body = AddressBodies.patch(original, valid(input(city = "Mysuru", postal = "570001")))!!
        assertEquals(setOf("city", "postalCode"), body.keys)
        assertEquals(JsonPrimitive("570001"), body["postalCode"])
    }

    @Test fun clearingAnOptionalFieldSendsAnExplicitNull() {
        val original = ca(line2 = "Sector 6", landmark = "Near park")
        val body = AddressBodies.patch(original, valid(input(line2 = "", landmark = "Near park")))!!
        assertEquals(setOf("addressLine2"), body.keys)
        assertEquals(JsonNull, body["addressLine2"])
    }

    @Test fun anUnchangedEditSendsNothing() {
        assertNull(AddressBodies.patch(ca(), valid(input())))
    }

    @Test fun personalDataNeverAppearsInToString() {
        val i = input(name = "Asha Rao", phone = "9876543210", line1 = "22, 14th Main", postal = "560102")
        val texts = listOf(i.toString(), valid(i).toString(), AddressValidator.validate(i).let { (it as AddressValidation.Valid).toString() }, ca().toString())
        for (t in texts) for (secret in listOf("Asha", "9876543210", "14th Main", "560102", "Bengaluru")) assertFalse(secret in t, "'$t' leaks $secret")
        assertIs<AddressValidation.Valid>(AddressValidator.validate(i))
        assertTrue(true)
    }
}
