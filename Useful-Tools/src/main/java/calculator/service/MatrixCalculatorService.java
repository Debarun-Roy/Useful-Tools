package calculator.service;

/**
 * Pure-Java 2×2 and 3×3 matrix arithmetic.
 *
 * No external library is required — cofactor expansion, adjugate/det inverse,
 * and standard O(n³) multiplication are trivially implementable for fixed small sizes.
 *
 * All public methods validate their inputs and throw IllegalArgumentException on
 * bad size or dimension mismatch so the controller can return HTTP 400 cleanly.
 *
 * Floating-point rounding:
 *   Values within 1e-10 of zero are snapped to exactly 0.0 by round() to eliminate
 *   display noise like 1.2246467991473532E-16 (near-zero from sin(π)).
 */
public class MatrixCalculatorService {

    // ── Determinant ───────────────────────────────────────────────────────────

    public double determinant(double[][] m, int size) {
        validate(m, size, "matrix1");
        return size == 2 ? det2(m) : det3(m);
    }

    private static double det2(double[][] m) {
        return m[0][0] * m[1][1] - m[0][1] * m[1][0];
    }

    private static double det3(double[][] m) {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
             - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
             + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }

    // ── Transpose ─────────────────────────────────────────────────────────────

    public double[][] transpose(double[][] m, int size) {
        validate(m, size, "matrix1");
        double[][] r = new double[size][size];
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                r[j][i] = m[i][j];
        return r;
    }

    // ── Inverse ───────────────────────────────────────────────────────────────

    public double[][] inverse(double[][] m, int size) {
        validate(m, size, "matrix1");
        double det = size == 2 ? det2(m) : det3(m);
        if (Math.abs(det) < 1e-12) {
            throw new IllegalArgumentException(
                    "Matrix is singular (determinant ≈ 0) — inverse does not exist.");
        }
        return size == 2 ? inverse2(m, det) : inverse3(m, det);
    }

    private static double[][] inverse2(double[][] m, double det) {
        return new double[][] {
            {  m[1][1] / det, -m[0][1] / det },
            { -m[1][0] / det,  m[0][0] / det }
        };
    }

    private static double[][] inverse3(double[][] m, double det) {
        // Cofactor matrix C[i][j] = (-1)^(i+j) * minor determinant
        double[][] C = new double[3][3];
        C[0][0] =  (m[1][1]*m[2][2] - m[1][2]*m[2][1]);
        C[0][1] = -(m[1][0]*m[2][2] - m[1][2]*m[2][0]);
        C[0][2] =  (m[1][0]*m[2][1] - m[1][1]*m[2][0]);
        C[1][0] = -(m[0][1]*m[2][2] - m[0][2]*m[2][1]);
        C[1][1] =  (m[0][0]*m[2][2] - m[0][2]*m[2][0]);
        C[1][2] = -(m[0][0]*m[2][1] - m[0][1]*m[2][0]);
        C[2][0] =  (m[0][1]*m[1][2] - m[0][2]*m[1][1]);
        C[2][1] = -(m[0][0]*m[1][2] - m[0][2]*m[1][0]);
        C[2][2] =  (m[0][0]*m[1][1] - m[0][1]*m[1][0]);

        // Inverse = transpose(C) / det   (transpose(C) is the adjugate)
        double[][] inv = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                inv[i][j] = C[j][i] / det;
        return inv;
    }

    // ── Multiply ──────────────────────────────────────────────────────────────

    public double[][] multiply(double[][] a, double[][] b, int size) {
        validate(a, size, "matrix1");
        validate(b, size, "matrix2");
        double[][] r = new double[size][size];
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                for (int k = 0; k < size; k++)
                    r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void validate(double[][] m, int size, String label) {
        if (size != 2 && size != 3) {
            throw new IllegalArgumentException(
                    "Matrix size must be 2 or 3. Received: " + size);
        }
        if (m == null || m.length != size) {
            throw new IllegalArgumentException(
                    label + " must have exactly " + size + " rows.");
        }
        for (int i = 0; i < size; i++) {
            if (m[i] == null || m[i].length != size) {
                throw new IllegalArgumentException(
                        label + " row " + i + " must have exactly " + size + " elements.");
            }
        }
    }

    /** Snaps values extremely close to zero to exactly 0.0, then rounds to 10 d.p. */
    public static double round(double v) {
        if (Math.abs(v) < 1e-10) return 0.0;
        return Math.round(v * 1e10) / 1e10;
    }

    public static double[][] roundMatrix(double[][] m) {
        double[][] r = new double[m.length][m[0].length];
        for (int i = 0; i < m.length; i++)
            for (int j = 0; j < m[0].length; j++)
                r[i][j] = round(m[i][j]);
        return r;
    }
}