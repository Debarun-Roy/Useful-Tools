package passwordgenerator.utilities;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import common.AppConfig;
import common.UnifiedLogger;

/**
 * RecaptchaUtils — server-side verification of Google reCAPTCHA v3 tokens.
 *
 * Frontend (ForgotPasswordPage.jsx) obtains a token client-side via the
 * useGoogleReCaptcha() hook from "react-google-recaptcha-v3" (wired up in
 * main.jsx via GoogleReCaptchaProvider) and sends it to
 * ForgotPasswordCaptchaController, which calls verify() here.
 *
 * reCAPTCHA v3 does not return a simple pass/fail — it returns a risk score
 * from 0.0 (likely a bot) to 1.0 (likely a human). SCORE_THRESHOLD is the
 * cutoff below which a request is treated as failed. 0.5 is Google's own
 * documented default recommendation; tune per observed traffic if this
 * starts producing false positives/negatives.
 *
 * The "action" field is also checked against the value the frontend passed
 * to executeRecaptcha(action) — this stops a token minted for one action
 * (e.g. a different form) from being replayed against this endpoint.
 *
 * SECURITY: fails CLOSED. Any missing configuration, network failure, or
 * malformed response from Google is treated as a failed verification —
 * never as an automatic pass. A misconfigured RECAPTCHA_SECRET_KEY must
 * never silently disable this check.
 */
public class RecaptchaUtils {

    private static final Logger logger = new UnifiedLogger().writeLogs("dao");

    private static final String VERIFY_URL = AppConfig.getRequired("recaptcha_verify_url");
    private static final double SCORE_THRESHOLD = Double.parseDouble(AppConfig.getRequired("recaptcha_score_threshold"));

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private RecaptchaUtils() { }

    /**
     * Verifies a reCAPTCHA v3 token against Google's siteverify endpoint.
     *
     * @param token          The token produced client-side by grecaptcha.execute().
     * @param expectedAction The action name the frontend passed to
     *                       executeRecaptcha(action) — must match exactly.
     * @param remoteIp       Optional; the caller's IP address (improves Google's
     *                       risk analysis). May be null.
     * @return true only if Google confirms success, the score meets
     *         SCORE_THRESHOLD, and the action matches. false in every other
     *         case, including any network or parsing error.
     * @throws IllegalStateException if RECAPTCHA_SECRET_KEY is not configured
     *         in the environment — callers must treat this as a server
     *         configuration error (HTTP 500), not as "captcha failed".
     */
    public static boolean verify(String token, String expectedAction, String remoteIp) {
        String secretKey = System.getenv("RECAPTCHA_SECRET_KEY");
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "RECAPTCHA_SECRET_KEY environment variable is not configured.");
        }

        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            StringBuilder form = new StringBuilder();
            form.append("secret=").append(urlEncode(secretKey));
            form.append("&response=").append(urlEncode(token));
            if (remoteIp != null && !remoteIp.isBlank()) {
                form.append("&remoteip=").append(urlEncode(remoteIp));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VERIFY_URL))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();

            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warning("reCAPTCHA siteverify returned HTTP " + response.statusCode());
                return false;
            }

            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();

            boolean success = body.has("success") && body.get("success").getAsBoolean();
            if (!success) {
                logger.info("reCAPTCHA verification failed: " + body);
                return false;
            }

            double score = body.has("score") ? body.get("score").getAsDouble() : 0.0;
            if (score < SCORE_THRESHOLD) {
                logger.info("reCAPTCHA score " + score + " below threshold " + SCORE_THRESHOLD);
                return false;
            }

            if (expectedAction != null) {
                String actualAction = body.has("action") ? body.get("action").getAsString() : null;
                if (!expectedAction.equals(actualAction)) {
                    logger.warning("reCAPTCHA action mismatch: expected '" + expectedAction
                            + "' but got '" + actualAction + "'");
                    return false;
                }
            }

            return true;

        } catch (InterruptedException e) {
            logger.warning("reCAPTCHA verification request interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            logger.warning("reCAPTCHA verification request failed: " + e.getMessage());
            return false;
        } catch (Exception e) {
            // Any parsing / unexpected error — fail closed, never fail open.
            logger.warning("reCAPTCHA verification error: " + e.getMessage());
            return false;
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
