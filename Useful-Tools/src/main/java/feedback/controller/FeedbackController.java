package feedback.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import common.ApiResponse;
import feedback.dao.FeedbackDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Receives and persists user feedback.
 *
 * POST /api/feedback/submit
 * Content-Type: application/json
 *
 * Request body:
 * {
 *   "overallRating": 4,                        // required, 1–5
 *   "generalComment": "Great app overall!",    // optional
 *   "featureFeedback": [                       // optional, may be empty
 *     {
 *       "featureName": "Calculator",
 *       "rating": 5,                           // optional, 1–5
 *       "comment": "Love the complex tab"      // optional
 *     }
 *   ]
 * }
 *
 * Response 200:
 *   { "success": true, "data": { "feedbackId": 7, "message": "Thank you…" } }
 *
 * Response 400:
 *   { "success": false, "errorCode": "INVALID_RATING", "error": "…" }
 */
@WebServlet("/api/feedback/submit")
public class FeedbackController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    // ── Inner request model classes used by Gson ──────────────────────────────

    private static class FeatureFeedbackRequest {
        String  featureName;
        Integer rating;
        String  comment;
    }

    private static class FeedbackRequest {
        int overallRating;
        String generalComment;
        List<FeatureFeedbackRequest> featureFeedback;
    }

    // ── Handler ───────────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            // ── 1. Resolve authenticated user ─────────────────────────────────
            HttpSession session = request.getSession(false);
            String username = (session != null)
                    ? (String) session.getAttribute("username") : null;

            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(gson.toJson(ApiResponse.fail(
                        "You must be logged in to submit feedback.",
                        "UNAUTHENTICATED")));
                return;
            }

            // ── 2. Parse JSON body ─────────────────────────────────────────────
            FeedbackRequest body;
            try {
                body = gson.fromJson(request.getReader(), FeedbackRequest.class);
            } catch (JsonSyntaxException jse) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body must be valid JSON.", "INVALID_JSON")));
                return;
            }

            if (body == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Request body is required.", "MISSING_BODY")));
                return;
            }

            // ── 3. Validate overall rating ─────────────────────────────────────
            if (body.overallRating < 1 || body.overallRating > 5) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(ApiResponse.fail(
                        "Overall rating must be an integer between 1 and 5.",
                        "INVALID_RATING")));
                return;
            }

            // ── 4. Persist top-level feedback row ──────────────────────────────
            long feedbackId = FeedbackDAO.saveFeedback(
                    username, body.overallRating, body.generalComment);

            if (feedbackId < 0) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print(gson.toJson(ApiResponse.fail(
                        "Failed to save feedback. Please try again.",
                        "INTERNAL_ERROR")));
                return;
            }

            // ── 5. Persist per-feature feedback rows ───────────────────────────
            if (body.featureFeedback != null) {
                for (FeatureFeedbackRequest ff : body.featureFeedback) {
                    if (ff == null || ff.featureName == null || ff.featureName.isBlank()) {
                        continue; // skip malformed entries silently
                    }
                    // Clamp feature rating to valid range or null out
                    Integer featureRating = ff.rating;
                    if (featureRating != null && (featureRating < 1 || featureRating > 5)) {
                        featureRating = null;
                    }
                    FeedbackDAO.saveFeatureFeedback(
                            feedbackId, ff.featureName.trim(), featureRating, ff.comment);
                }
            }

            // ── 6. Return success ──────────────────────────────────────────────
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("feedbackId", feedbackId);
            data.put("message", "Thank you for your feedback!");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(ApiResponse.ok(data)));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(gson.toJson(ApiResponse.fail(
                    "An unexpected error occurred while saving your feedback.",
                    "INTERNAL_ERROR")));
        }
    }
}
