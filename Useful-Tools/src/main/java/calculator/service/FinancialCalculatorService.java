package calculator.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure calculation logic for the four financial calculator modes.
 * All methods are stateless and return a LinkedHashMap of result fields
 * that the controller serialises directly into the ApiResponse data payload.
 *
 * ── EMI ──────────────────────────────────────────────────────────────────────
 * EMI = P × r × (1+r)^n / ((1+r)^n − 1)
 * where r = monthly rate (annualRate / 100 / 12), n = tenure in months.
 *
 * ── Tax (India FY 2024-25) ───────────────────────────────────────────────────
 * New regime: standard deduction ₹75,000; slabs 0/5/10/15/20/30%.
 * Old regime: user-supplied deductions; slabs 0/5/20/30%.
 * Health and education cess 4% applied on tax > 0 in both regimes.
 *
 * ── Compound Interest ────────────────────────────────────────────────────────
 * A = P(1 + r/n)^(nt)   where n = compounding frequency per year.
 *
 * ── Salary Breakdown ─────────────────────────────────────────────────────────
 * Gross = Basic + HRA + DA + OtherAllowances.
 * Net   = Gross − TotalDeductions.
 */
public class FinancialCalculatorService {

    // ── Compounding frequency map ─────────────────────────────────────────────
    private static final Map<String, Integer> FREQ_MAP = Map.of(
            "annually",      1,
            "semiannually",  2,
            "quarterly",     4,
            "monthly",      12,
            "daily",       365
    );

    // ─────────────────────────────────────────────────────────────────────────
    // EMI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates Equated Monthly Installment.
     *
     * @param principal    Loan amount (positive)
     * @param annualRate   Annual interest rate as a percentage (≥ 0)
     * @param tenure       Loan duration
     * @param tenureUnit   "years" or "months"
     * @return Map of { emi, totalAmount, totalInterest, tenureMonths }
     * @throws IllegalArgumentException if inputs are invalid
     */
    public LinkedHashMap<String, Object> calculateEMI(
            double principal, double annualRate, double tenure, String tenureUnit) {

        validate(principal > 0,  "Principal must be positive.");
        validate(annualRate >= 0, "Annual rate cannot be negative.");
        validate(tenure > 0,     "Tenure must be positive.");

        int months = "years".equalsIgnoreCase(tenureUnit)
                ? (int) Math.round(tenure * 12)
                : (int) Math.round(tenure);

        validate(months > 0, "Effective tenure in months must be positive.");

        double monthlyRate = annualRate / 100.0 / 12.0;
        double emi;

        if (monthlyRate == 0.0) {
            emi = principal / months;
        } else {
            double pow = Math.pow(1 + monthlyRate, months);
            emi = principal * monthlyRate * pow / (pow - 1);
        }

        double totalAmount   = round2(emi * months);
        double totalInterest = round2(totalAmount - principal);
        emi = round2(emi);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("emi",           emi);
        result.put("totalAmount",   totalAmount);
        result.put("totalInterest", totalInterest);
        result.put("tenureMonths",  months);
        result.put("principal",     principal);
        result.put("annualRate",    annualRate);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tax
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates income tax for India FY 2024-25.
     *
     * @param grossIncome Annual gross income
     * @param regime      "new" or "old"
     * @param deductions  Additional deductions (only used for old regime)
     * @return Map of { grossIncome, standardDeduction, otherDeductions,
     *                  taxableIncome, taxBeforeCess, cess, totalTax, netIncome }
     */
    public LinkedHashMap<String, Object> calculateTax(
            double grossIncome, String regime, double deductions) {

        validate(grossIncome >= 0, "Gross income cannot be negative.");
        validate(deductions >= 0,  "Deductions cannot be negative.");

        boolean isNew = !"old".equalsIgnoreCase(regime);

        // Standard deduction
        double standardDeduction = isNew
                ? Math.min(75_000, grossIncome)
                : 0;

        double taxableIncome = Math.max(0, grossIncome - standardDeduction - (isNew ? 0 : deductions));

        // Calculate tax from slabs
        double[][] slabs = isNew
                ? new double[][] {
                    {      0,  300_000, 0.00 },
                    {300_000,  600_000, 0.05 },
                    {600_000,  900_000, 0.10 },
                    {900_000, 1_200_000, 0.15 },
                    {1_200_000, 1_500_000, 0.20 },
                    {1_500_000, Double.MAX_VALUE, 0.30 }
                }
                : new double[][] {
                    {      0,  250_000, 0.00 },
                    {250_000,  500_000, 0.05 },
                    {500_000, 1_000_000, 0.20 },
                    {1_000_000, Double.MAX_VALUE, 0.30 }
                };

        double taxBeforeCess = 0;
        for (double[] slab : slabs) {
            if (taxableIncome <= slab[0]) break;
            taxBeforeCess += (Math.min(taxableIncome, slab[1]) - slab[0]) * slab[2];
        }

        double cess      = isNew ? round2(taxBeforeCess * 0.04) : 0;
        double totalTax  = round2(taxBeforeCess + cess);
        double netIncome = round2(grossIncome - totalTax);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("grossIncome",       grossIncome);
        result.put("standardDeduction", standardDeduction);
        result.put("otherDeductions",   deductions);
        result.put("taxableIncome",     round2(taxableIncome));
        result.put("taxBeforeCess",     round2(taxBeforeCess));
        result.put("cess",              cess);
        result.put("totalTax",          totalTax);
        result.put("netIncome",         netIncome);
        result.put("regime",            isNew ? "new" : "old");
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Compound Interest
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates compound interest.
     *
     * @param principal  Initial investment
     * @param rate       Annual interest rate as a percentage
     * @param time       Investment period in years
     * @param frequency  Compounding frequency: annually|semiannually|quarterly|monthly|daily
     * @return Map of { principal, finalAmount, interestEarned, effectiveRate }
     */
    public LinkedHashMap<String, Object> calculateCompoundInterest(
            double principal, double rate, double time, String frequency) {

        validate(principal > 0, "Principal must be positive.");
        validate(rate >= 0,     "Rate cannot be negative.");
        validate(time > 0,      "Time must be positive.");

        int n = FREQ_MAP.getOrDefault(
                frequency == null ? "" : frequency.toLowerCase(), 1);

        double r           = rate / 100.0;
        double finalAmount = principal * Math.pow(1 + r / n, n * time);
        double interest    = finalAmount - principal;

        // Effective annual rate: (1 + r/n)^n − 1
        double effectiveRate = (Math.pow(1 + r / n, n) - 1) * 100;

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("principal",     principal);
        result.put("finalAmount",   round2(finalAmount));
        result.put("interestEarned", round2(interest));
        result.put("effectiveRate", round4(effectiveRate));
        result.put("frequency",     frequency);
        result.put("rate",          rate);
        result.put("time",          time);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Salary Breakdown
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates monthly salary breakdown.
     *
     * @return Map of { basicSalary, hra, da, allowances, grossSalary,
     *                  pfContribution, professionalTax, otherDeductions,
     *                  totalDeductions, netSalary, annualGross, annualNet }
     */
    public LinkedHashMap<String, Object> calculateSalaryBreakdown(
            double basicSalary, double hra, double da, double allowances,
            double pfContribution, double professionalTax, double otherDeductions) {

        validate(basicSalary > 0,       "Basic salary must be positive.");
        validate(hra >= 0,              "HRA cannot be negative.");
        validate(da >= 0,               "DA cannot be negative.");
        validate(allowances >= 0,       "Allowances cannot be negative.");
        validate(pfContribution >= 0,   "PF contribution cannot be negative.");
        validate(professionalTax >= 0,  "Professional tax cannot be negative.");
        validate(otherDeductions >= 0,  "Other deductions cannot be negative.");

        double grossSalary      = basicSalary + hra + da + allowances;
        double totalDeductions  = pfContribution + professionalTax + otherDeductions;
        double netSalary        = grossSalary - totalDeductions;

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("basicSalary",      basicSalary);
        result.put("hra",              hra);
        result.put("da",               da);
        result.put("allowances",       allowances);
        result.put("grossSalary",      round2(grossSalary));
        result.put("pfContribution",   pfContribution);
        result.put("professionalTax",  professionalTax);
        result.put("otherDeductions",  otherDeductions);
        result.put("totalDeductions",  round2(totalDeductions));
        result.put("netSalary",        round2(netSalary));
        result.put("annualGross",      round2(grossSalary * 12));
        result.put("annualNet",        round2(netSalary * 12));
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    private static void validate(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}