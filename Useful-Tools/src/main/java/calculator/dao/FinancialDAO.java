package calculator.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

import common.DatabaseUtils;

/**
 * DAO for persisting financial calculator results.
 *
 * ── Required schema ───────────────────────────────────────────────────────────
 * Run the following SQL once to create the four tables:
 *
 *   CREATE TABLE IF NOT EXISTS emi_calculations (
 *     id               INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username         TEXT    NOT NULL,
 *     principal        REAL    NOT NULL,
 *     annual_rate      REAL    NOT NULL,
 *     tenure_months    INTEGER NOT NULL,
 *     emi              REAL    NOT NULL,
 *     total_amount     REAL    NOT NULL,
 *     total_interest   REAL    NOT NULL,
 *     calculated_at    TEXT    NOT NULL
 *   );
 *
 *   CREATE TABLE IF NOT EXISTS tax_calculations (
 *     id               INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username         TEXT    NOT NULL,
 *     gross_income     REAL    NOT NULL,
 *     regime           TEXT    NOT NULL,
 *     taxable_income   REAL    NOT NULL,
 *     total_tax        REAL    NOT NULL,
 *     net_income       REAL    NOT NULL,
 *     calculated_at    TEXT    NOT NULL
 *   );
 *
 *   CREATE TABLE IF NOT EXISTS ci_calculations (
 *     id               INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username         TEXT    NOT NULL,
 *     principal        REAL    NOT NULL,
 *     annual_rate      REAL    NOT NULL,
 *     time_years       REAL    NOT NULL,
 *     frequency        TEXT    NOT NULL,
 *     final_amount     REAL    NOT NULL,
 *     interest_earned  REAL    NOT NULL,
 *     calculated_at    TEXT    NOT NULL
 *   );
 *
 *   CREATE TABLE IF NOT EXISTS salary_calculations (
 *     id               INTEGER PRIMARY KEY AUTOINCREMENT,
 *     username         TEXT    NOT NULL,
 *     basic_salary     REAL    NOT NULL,
 *     gross_salary     REAL    NOT NULL,
 *     total_deductions REAL    NOT NULL,
 *     net_salary       REAL    NOT NULL,
 *     calculated_at    TEXT    NOT NULL
 *   );
 */
public class FinancialDAO {

    public static void saveEMI(
            String username, double principal, double annualRate,
            int tenureMonths, double emi, double totalAmount, double totalInterest) {

        String sql = "INSERT INTO emi_calculations "
                + "(username, principal, annual_rate, tenure_months, emi, total_amount, total_interest, calculated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, username);
            pst.setDouble(2, principal);
            pst.setDouble(3, annualRate);
            pst.setInt(4, tenureMonths);
            pst.setDouble(5, emi);
            pst.setDouble(6, totalAmount);
            pst.setDouble(7, totalInterest);
            pst.setString(8, Instant.now().toString());
            pst.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void saveTax(
            String username, double grossIncome, String regime,
            double taxableIncome, double totalTax, double netIncome) {

        String sql = "INSERT INTO tax_calculations "
                + "(username, gross_income, regime, taxable_income, total_tax, net_income, calculated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?);";

        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, username);
            pst.setDouble(2, grossIncome);
            pst.setString(3, regime);
            pst.setDouble(4, taxableIncome);
            pst.setDouble(5, totalTax);
            pst.setDouble(6, netIncome);
            pst.setString(7, Instant.now().toString());
            pst.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void saveCompoundInterest(
            String username, double principal, double annualRate,
            double timeYears, String frequency, double finalAmount, double interestEarned) {

        String sql = "INSERT INTO ci_calculations "
                + "(username, principal, annual_rate, time_years, frequency, final_amount, interest_earned, calculated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, username);
            pst.setDouble(2, principal);
            pst.setDouble(3, annualRate);
            pst.setDouble(4, timeYears);
            pst.setString(5, frequency);
            pst.setDouble(6, finalAmount);
            pst.setDouble(7, interestEarned);
            pst.setString(8, Instant.now().toString());
            pst.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void saveSalaryBreakdown(
            String username, double basicSalary, double grossSalary,
            double totalDeductions, double netSalary) {

        String sql = "INSERT INTO salary_calculations "
                + "(username, basic_salary, gross_salary, total_deductions, net_salary, calculated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?);";

        try (Connection conn = DatabaseUtils.getSQLite3Connection();
             PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, username);
            pst.setDouble(2, basicSalary);
            pst.setDouble(3, grossSalary);
            pst.setDouble(4, totalDeductions);
            pst.setDouble(5, netSalary);
            pst.setString(6, Instant.now().toString());
            pst.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}