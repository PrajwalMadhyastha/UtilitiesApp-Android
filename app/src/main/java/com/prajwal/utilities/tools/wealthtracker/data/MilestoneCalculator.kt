package com.prajwal.utilities.tools.wealthtracker.data

import java.text.NumberFormat
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Pure Kotlin milestone calculator.
 * No Android dependencies — fully unit-testable.
 *
 * Uses a step-up SIP (Systematic Investment Plan) model:
 *  - Monthly SIP starts at [monthlySip]
 *  - Each year the SIP increases by [annualStepupPct]%
 *  - Portfolio grows at [annualReturnPct]% per year
 *  - Calculation starts from current portfolio value [currentPortfolioValue]
 */
object MilestoneCalculator {

    var isValuesHidden by mutableStateOf(false)

    data class MilestoneResult(
        val targetAmount: Double,
        val monthsToReach: Int,       // -1 if target already reached
        val yearsToReach: Double,
        val projectionPoints: List<ProjectionPoint> // year → projected value
    )

    data class ProjectionPoint(
        val year: Int,          // years from now
        val portfolioValue: Double
    )

    /**
     * Calculate how long it takes to reach each [targets] amount.
     * Also returns a year-by-year projection for the chart.
     *
     * @param currentPortfolioValue  Current total market value of the portfolio
     * @param monthlySip             Monthly investment amount (₹)
     * @param annualStepupPct        Yearly step-up percentage (e.g. 10.0 for 10%)
     * @param annualReturnPct        Expected annual return (e.g. 12.0 for 12%)
     * @param targets                List of target amounts (e.g. 1Cr, 5Cr, 10Cr)
     * @param projectionYears        How many years ahead to project
     */
    fun calculate(
        currentPortfolioValue: Double,
        monthlySip: Double,
        annualStepupPct: Double,
        annualReturnPct: Double,
        targets: List<Double> = listOf(1_00_00_000.0, 5_00_00_000.0, 10_00_00_000.0),
        projectionYears: Int = 30
    ): List<MilestoneResult> {
        val monthlyRate = (annualReturnPct / 100) / 12
        val stepupMultiplier = 1 + annualStepupPct / 100

        // Build month-by-month projection
        val monthlyValues = mutableListOf<Double>()
        var portfolioValue = currentPortfolioValue
        var currentMonthlySip = monthlySip

        for (month in 1..(projectionYears * 12)) {
            // Apply this month's SIP first, then grow for a month
            portfolioValue = (portfolioValue + currentMonthlySip) * (1 + monthlyRate)
            monthlyValues.add(portfolioValue)

            // Step up SIP at start of each new year
            if (month % 12 == 0) {
                currentMonthlySip *= stepupMultiplier
            }
        }

        // Build year-by-year projection points (index 11, 23, 35 ... = end of each year)
        val projectionPoints = buildList {
            add(ProjectionPoint(0, currentPortfolioValue))
            for (year in 1..projectionYears) {
                val idx = (year * 12) - 1
                if (idx < monthlyValues.size) {
                    add(ProjectionPoint(year, monthlyValues[idx]))
                }
            }
        }

        return targets.map { target ->
            if (currentPortfolioValue >= target) {
                MilestoneResult(
                    targetAmount = target,
                    monthsToReach = -1,
                    yearsToReach = 0.0,
                    projectionPoints = projectionPoints
                )
            } else {
                val monthIndex = monthlyValues.indexOfFirst { it >= target }
                val months = if (monthIndex >= 0) monthIndex + 1 else projectionYears * 12 + 1
                MilestoneResult(
                    targetAmount = target,
                    monthsToReach = months,
                    yearsToReach = months / 12.0,
                    projectionPoints = projectionPoints
                )
            }
        }
    }

    fun formatInr(amount: Double): String {
        if (isValuesHidden) return "****"
        val isNegative = amount < 0
        val absAmount = kotlin.math.abs(amount)
        val formatted = when {
            absAmount >= 1_00_00_000 -> "₹%.2f Cr".format(absAmount / 1_00_00_000)
            absAmount >= 1_00_000 -> "₹%.1f L".format(absAmount / 1_00_00_000)
            else -> "₹%.0f".format(absAmount)
        }
        return if (isNegative) "-$formatted" else formatted
    }

    /** Format a number with exact Indian commas (e.g. ₹1,23,456) and optional decimals */
    fun formatInrExact(amount: Double, showDecimals: Boolean = false): String {
        if (isValuesHidden) return "****"
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        format.maximumFractionDigits = if (showDecimals) 2 else 0
        format.minimumFractionDigits = if (showDecimals) 2 else 0
        return format.format(amount)
    }
}
