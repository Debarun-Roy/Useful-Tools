package calculator.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import common.DatabaseUtils;

public class ComputeDAO {

	public static void storeExpressionResult(String expr, String ans) {
		try {
			Connection conn = DatabaseUtils.getSQLite3Connection();
			String sql = "INSERT INTO expr_table VALUES(?, ?);";
			PreparedStatement pst = conn.prepareStatement(sql);
			pst.setString(1, expr); //PreparedStatement uses 1-based indexing
			pst.setString(2, ans);
			pst.executeUpdate();
			DatabaseUtils.closeSQLConnection(conn, pst, null);
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
