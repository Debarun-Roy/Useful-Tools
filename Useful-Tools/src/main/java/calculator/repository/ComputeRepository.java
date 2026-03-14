package calculator.repository;

import calculator.dao.ComputeDAO;

public class ComputeRepository {

	public void storeExpressionResult(String expression, String result) {

        ComputeDAO.storeExpressionResult(expression, result);

    }
}
