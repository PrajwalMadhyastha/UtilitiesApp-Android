package com.prajwal.utilities.tools.wealthtracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MilestoneCalculatorTest {

    @Test
    fun testFormatInrForLakhsAndCrores() {
        MilestoneCalculator.isValuesHidden = false

        // Under 1 Lakh
        assertEquals("₹50000", MilestoneCalculator.formatInr(50000.0))
        assertEquals("₹99999", MilestoneCalculator.formatInr(99999.0))

        // 1 Lakh to 1 Crore
        assertEquals("₹1.0 L", MilestoneCalculator.formatInr(100000.0))
        assertEquals("₹5.0 L", MilestoneCalculator.formatInr(500000.0))
        assertEquals("₹25.5 L", MilestoneCalculator.formatInr(2550000.0))
        assertEquals("₹99.0 L", MilestoneCalculator.formatInr(9900000.0))

        // 1 Crore and above
        assertEquals("₹1.00 Cr", MilestoneCalculator.formatInr(10000000.0))
        assertEquals("₹2.50 Cr", MilestoneCalculator.formatInr(25000000.0))
        assertEquals("₹10.75 Cr", MilestoneCalculator.formatInr(107500000.0))

        // Negative values
        assertEquals("-₹5.0 L", MilestoneCalculator.formatInr(-500000.0))
        assertEquals("-₹1.50 Cr", MilestoneCalculator.formatInr(-15000000.0))
    }

    @Test
    fun testHiddenValuesMasking() {
        MilestoneCalculator.isValuesHidden = true
        assertEquals("****", MilestoneCalculator.formatInr(500000.0))
        assertEquals("****", MilestoneCalculator.formatInrExact(500000.0))
        MilestoneCalculator.isValuesHidden = false
    }
}
