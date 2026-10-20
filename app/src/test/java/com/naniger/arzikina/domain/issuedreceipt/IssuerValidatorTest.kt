package com.naniger.arzikina.domain.issuedreceipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuerValidatorTest {

    private fun validate(
        name: String = "Boutique Aïssa",
        address: String = "Niamey, Plateau",
        phone: String = "+227 90 00 00 00",
        email: String? = null,
        taxId: String? = null
    ) = IssuerValidator.validate(name, address, phone, email, taxId)

    @Test fun completeProfileIsValid() {
        assertTrue(validate(email = "contact@aissa.ne", taxId = "NIF 123").isEmpty())
    }

    @Test fun blankRequiredFieldsAreReported() {
        assertEquals(
            setOf(IssuedReceiptError.BUSINESS_NAME_BLANK, IssuedReceiptError.BUSINESS_ADDRESS_BLANK, IssuedReceiptError.BUSINESS_PHONE_INVALID),
            validate(name = " ", address = "", phone = "")
        )
    }

    @Test fun invalidEmailIsReportedButBlankIsAllowed() {
        assertEquals(setOf(IssuedReceiptError.BUSINESS_EMAIL_INVALID), validate(email = "pas-un-email"))
        assertTrue(validate(email = "  ").isEmpty())
    }

    @Test fun tooLongTextIsReported() {
        assertEquals(setOf(IssuedReceiptError.TEXT_TOO_LONG), validate(name = "a".repeat(IssuerValidator.MAX_NAME_LENGTH + 1)))
    }

    @Test fun phoneRejectsLetters() {
        assertEquals(setOf(IssuedReceiptError.BUSINESS_PHONE_INVALID), validate(phone = "abc123456"))
    }
}
