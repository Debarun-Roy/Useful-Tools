package passwordgenerator.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import common.UnifiedLogger;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import passwordgenerator.dao.UserPasswordDAO;
import passwordgenerator.models.PasswordModel;
import passwordgenerator.utilities.EncryptionUtils;
import passwordgenerator.utilities.HashingUtils;

/**
 * FIX (package): Renamed from PasswordGenerator.Controller to passwordgenerator.controller.
 * FIX (import): Now imports common.UnifiedLogger.
 * FIX (model): Updated setSpecialCharactercount → setSpecialCharacterCount.
 */
@WebServlet("/SavePassword")
public class SavePasswordDetailsController extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = new UnifiedLogger().writeLogs("controller");

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            String platform = request.getParameter("platform");

            PasswordModel pass = new PasswordModel();
            pass.setUsername(username);
            pass.setPlatform(platform);

            LinkedHashMap<String, String> encryption = EncryptionUtils.generateEncryptedPassword(password);
            pass.setEncryptedPassword(encryption.get("encrypted_password"));
            pass.setPrivateKey(encryption.get("private_key"));

            String hashedPassword = HashingUtils.generateHashedPassword(password);
            pass.setHashedPassword(hashedPassword);
            pass.setCreatedDate(Instant.now());

            UserPasswordDAO.saveUserPasswordDetails(pass);
            UserPasswordDAO.saveEncryptionDetails(pass);
            logger.info("User password details stored in table successfully");

            request.setAttribute("username", username);
            request.setAttribute("platform", platform);
            request.getRequestDispatcher("/success.jsp").forward(request, response);

        } catch (Exception e) {
            e.printStackTrace();
            request.getRequestDispatcher("/errorPageReloader.jsp").forward(request, response);
        }
    }
}
