package user.utilities;

import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserUtilsTest {
    private Connection fixture() throws SQLException {
        Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        try(Statement st=db.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("CREATE TABLE user_table(username TEXT PRIMARY KEY)");
            st.execute("INSERT INTO user_table VALUES('synthetic-A'),('synthetic-B')");
            st.execute("CREATE TABLE calc_history(username TEXT,value TEXT)");
            st.execute("INSERT INTO calc_history VALUES('synthetic-A','a'),('synthetic-B','b')");
            st.execute("CREATE TABLE feedback(id INTEGER PRIMARY KEY,username TEXT)");
            st.execute("CREATE TABLE feedback_features(feedback_id INTEGER REFERENCES feedback(id))");
            st.execute("INSERT INTO feedback VALUES(1,'synthetic-A'),(2,'synthetic-B')");
            st.execute("INSERT INTO feedback_features VALUES(1),(2)");
            st.execute("CREATE TABLE catalog(id INTEGER PRIMARY KEY)");
            st.execute("INSERT INTO catalog VALUES(1)");
        }
        return db;
    }
    private int count(Connection db,String table) throws SQLException {
        try(Statement st=db.createStatement();ResultSet rs=st.executeQuery("SELECT count(*) FROM "+table)) {rs.next();return rs.getInt(1);}
    }
    @Test void dataDeletionPreservesAccountsOtherOwnerAndCatalog() throws Exception {
        try(Connection db=fixture()) {
            UserUtils.deleteAtomically(db,"synthetic-A",false);
            assertEquals(2,count(db,"user_table"));assertEquals(1,count(db,"calc_history"));
            assertEquals(1,count(db,"feedback"));assertEquals(1,count(db,"feedback_features"));assertEquals(1,count(db,"catalog"));
            assertTrue(db.getAutoCommit());
        }
    }
    @Test void accountDeletionIncludesChildrenInSameTransaction() throws Exception {
        try(Connection db=fixture()) {
            UserUtils.deleteAtomically(db,"synthetic-A",true);
            assertEquals(1,count(db,"user_table"));assertEquals(1,count(db,"calc_history"));
            assertEquals(1,count(db,"feedback_features"));assertEquals(1,count(db,"catalog"));
        }
    }
    @Test void childFailureRollsBackEarlierDeletesAndPreservesAccount() throws Exception {
        try(Connection db=fixture();Statement st=db.createStatement()) {
            st.execute("CREATE TRIGGER block_feedback BEFORE DELETE ON feedback BEGIN SELECT RAISE(ABORT,'synthetic failure'); END");
            assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db,"synthetic-A",true));
            assertEquals(2,count(db,"calc_history"));assertEquals(2,count(db,"feedback_features"));assertEquals(2,count(db,"user_table"));
            assertTrue(db.getAutoCommit());
        }
    }
    @Test void accountFailureRollsBackAllUsageData() throws Exception {
        try(Connection db=fixture();Statement st=db.createStatement()) {
            st.execute("CREATE TRIGGER block_account BEFORE DELETE ON user_table BEGIN SELECT RAISE(ABORT,'synthetic failure'); END");
            assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db,"synthetic-A",true));
            assertEquals(2,count(db,"calc_history"));assertEquals(2,count(db,"feedback"));assertEquals(2,count(db,"feedback_features"));
        }
    }

    @Test void malformedExistingTableIsNotTreatedAsMissing() throws Exception {
        try(Connection db=fixture();Statement st=db.createStatement()) {
            st.execute("CREATE TABLE emi_calculations(wrong_column TEXT)");
            assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db,"synthetic-A",true));
            assertEquals(2,count(db,"calc_history"));assertEquals(2,count(db,"user_table"));
        }
    }
    @Test void allLegacyOwnerTablesAndRoleMetadataFollowDeletionContract() throws Exception {
        String[] tables={"emi_calculations","tax_calculations","ci_calculations","salary_calculations",
          "password_table","encryption_table","generator_table","password_history","user_activity",
          "user_favorites","tool_metrics","regex_patterns","tool_recommendations"};
        try(Connection db=fixture();Statement st=db.createStatement()) {
            st.execute("ALTER TABLE user_table ADD COLUMN role TEXT DEFAULT 'user'");
            for(String table:tables) {
                st.execute("CREATE TABLE "+table+"(username TEXT REFERENCES user_table(username))");
                st.execute("INSERT INTO "+table+" VALUES('synthetic-A'),('synthetic-B')");
            }
            UserUtils.deleteAtomically(db,"synthetic-A",false);
            for(String table:tables) assertEquals(1,count(db,table));
            assertEquals(2,count(db,"user_table"));
            UserUtils.deleteAtomically(db,"synthetic-A",true);
            assertEquals(1,count(db,"user_table"));
            try(ResultSet rows=st.executeQuery("SELECT username,role FROM user_table")) {
                assertTrue(rows.next());assertEquals("synthetic-B",rows.getString(1));assertEquals("user",rows.getString(2));
            }
        }
    }
    @Test void invalidInputsAndCallerTransactionAreRejectedWithoutTakingOwnership() throws Exception {
        assertFalse(UserUtils.deleteUserData(null));assertFalse(UserUtils.deleteAccountAndData(" "));
        assertThrows(SQLException.class,()->UserUtils.deleteAtomically(null,"synthetic-A",true));
        try(Connection db=fixture()) {
            assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db," ",false));
            db.setAutoCommit(false);
            assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db,"synthetic-A",true));
            assertFalse(db.getAutoCommit());assertEquals(2,count(db,"calc_history"));db.rollback();
        }
    }
    @Test void readOnlyFailureDoesNotDeleteAnything() throws Exception {
        try(Connection db=fixture();Statement st=db.createStatement()) {
            st.execute("PRAGMA query_only=ON");
            assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db,"synthetic-A",true));
            assertEquals(2,count(db,"calc_history"));assertEquals(2,count(db,"user_table"));
        }
    }
    private Connection failing(Connection db, boolean rollbackFails, boolean restoreFails, boolean runtime) {
        return (Connection)java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("commit")) {
                if(runtime) throw new IllegalStateException("synthetic commit");
                throw new SQLException("synthetic commit");
            }
            if(method.getName().equals("rollback") && rollbackFails) throw new SQLException("synthetic rollback");
            if(method.getName().equals("setAutoCommit") && Boolean.TRUE.equals(args[0]) && restoreFails) throw new SQLException("synthetic restore");
            try { return method.invoke(db,args); }
            catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        });
    }
    @Test void commitFailureRollsBackAndCleanupDoesNotMaskPrimaryCause() throws Exception {
        try(Connection db=fixture()) {
            SQLException failure=assertThrows(SQLException.class,()->UserUtils.deleteAtomically(failing(db,false,true,false),"synthetic-A",true));
            assertEquals("synthetic commit",failure.getMessage());assertEquals(1,failure.getSuppressed().length);
            assertEquals(2,count(db,"user_table"));assertEquals(2,count(db,"feedback_features"));
        }
    }
    @Test void failedRollbackNeverRestoresAutoCommitOrCommitsPartialDeletes() throws Exception {
        try(Connection db=fixture()) {
            SQLException failure=assertThrows(SQLException.class,()->UserUtils.deleteAtomically(failing(db,true,false,false),"synthetic-A",true));
            assertEquals(1,failure.getSuppressed().length);assertFalse(db.getAutoCommit());
            db.rollback();assertEquals(2,count(db,"calc_history"));assertEquals(2,count(db,"user_table"));
        }
    }
    @Test void runtimeFailureAlsoRollsBack() throws Exception {
        try(Connection db=fixture()) {
            assertThrows(IllegalStateException.class,()->UserUtils.deleteAtomically(failing(db,false,false,true),"synthetic-A",true));
            assertTrue(db.getAutoCommit());assertEquals(2,count(db,"calc_history"));assertEquals(2,count(db,"user_table"));
        }
    }

    @Test void deferredForeignKeyFailureRollsBackCommit() throws Exception {
        try(Connection db=fixture();Statement st=db.createStatement()) {
            st.execute("CREATE TABLE external_child(owner TEXT REFERENCES user_table(username) DEFERRABLE INITIALLY DEFERRED)");
            st.execute("INSERT INTO external_child VALUES('synthetic-A')");
            assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db,"synthetic-A",true));
            assertEquals(2,count(db,"user_table"));assertEquals(2,count(db,"calc_history"));
            assertEquals(2,count(db,"feedback_features"));assertTrue(db.getAutoCommit());
        }
    }
    @Test void lockedIsolatedDatabaseCannotReportSuccess(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        String url="jdbc:sqlite:"+directory.resolve("synthetic.db");
        try(Connection db=DriverManager.getConnection(url);Connection blocker=DriverManager.getConnection(url);
            Statement st=db.createStatement();Statement lock=blocker.createStatement()) {
            st.execute("CREATE TABLE user_table(username TEXT PRIMARY KEY)");
            st.execute("INSERT INTO user_table VALUES('synthetic-A')");
            st.execute("CREATE TABLE calc_history(username TEXT)");
            st.execute("INSERT INTO calc_history VALUES('synthetic-A')");
            st.execute("PRAGMA busy_timeout=1");lock.execute("BEGIN IMMEDIATE");
            try { assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db,"synthetic-A",true)); }
            finally { lock.execute("ROLLBACK"); }
            assertEquals(1,count(db,"user_table"));assertEquals(1,count(db,"calc_history"));assertTrue(db.getAutoCommit());
        }
    }

    @Test void missingOrAmbiguousAccountNeverAcknowledgesRemoval() throws Exception {
        try(Connection db=fixture()) {
            assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db,"missing-synthetic",true));
            assertEquals(2,count(db,"user_table"));
        }
        try(Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");Statement st=db.createStatement()) {
            st.execute("CREATE TABLE user_table(username TEXT NOT NULL)");
            st.execute("INSERT INTO user_table VALUES('synthetic-A'),('synthetic-A')");
            st.execute("CREATE TABLE calc_history(username TEXT)");
            st.execute("INSERT INTO calc_history VALUES('synthetic-A')");
            assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db,"synthetic-A",true));
            assertEquals(2,count(db,"user_table"));assertEquals(1,count(db,"calc_history"));
        }
    }

    @Test void lastAdminIsRecheckedInsideDeletionTransaction() throws Exception {
        try(Connection db=fixture();Statement st=db.createStatement()) {
            st.execute("ALTER TABLE user_table ADD COLUMN role TEXT DEFAULT 'user'");
            st.execute("UPDATE user_table SET role='admin' WHERE username='synthetic-A'");
            assertThrows(SQLException.class,()->UserUtils.deleteAtomically(db,"synthetic-A",true));
            assertEquals(2,count(db,"calc_history"));assertEquals(2,count(db,"user_table"));
            UserUtils.deleteAtomically(db,"synthetic-A",false);
            assertEquals(2,count(db,"user_table"));
            st.execute("UPDATE user_table SET role='admin' WHERE username='synthetic-B'");
            UserUtils.deleteAtomically(db,"synthetic-A",true);
            assertEquals(1,count(db,"user_table"));
        }
    }
}
