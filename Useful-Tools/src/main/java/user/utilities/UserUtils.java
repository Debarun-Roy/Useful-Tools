package user.utilities;

public class UserUtils {
    
    public static boolean deleteUserData(String username){
        return false;

        /**
         * need to delete from the following tables
         * 1. calc_history: DELETE FROM calc_history WHERE username = ?
         * 2. ci_calculations: DELETE FROM ci_calculations WHERE username = ?
         * 3. emi_calculations: DELETE FROM emi_calculations WHERE username = ?
         * 4. encryption_table: DELETE FROM encryption_table WHERE username = ?
         * 5. password_history: DELETE FROM password_history WHERE username = ?
         * 6. password_table: DELETE FROM password_table WHERE username = ?
         * 7. salary_calculations: DELETE FROM salary_calculations WHERE username = ?
         * 8. tax_calculations: DELETE FROM tax_calculations WHERE username = ?
         * 9. user_table: DELETE FROM user_table WHERE username = ?
         */
    }
}
