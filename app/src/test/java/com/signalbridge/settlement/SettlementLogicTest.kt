package com.signalbridge.settlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementLogicTest {

    @Test fun `amount rounds up to whole minutes`() {
        assertEquals(0.0, Settlement.amountRupees(0), 0.0001)
        assertEquals(1.0, Settlement.amountRupees(1), 0.0001)   // 1s -> 1 min
        assertEquals(1.0, Settlement.amountRupees(60), 0.0001)
        assertEquals(2.0, Settlement.amountRupees(61), 0.0001)  // just over -> 2 min
    }

    @Test fun `payee validation gates payment`() {
        assertTrue(Settlement.isValidPayee("arun@okaxis"))
        assertFalse(Settlement.isValidPayee(null))
        assertFalse(Settlement.isValidPayee("not-a-vpa"))
    }
}
