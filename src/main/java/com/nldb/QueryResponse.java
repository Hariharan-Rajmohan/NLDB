package com.nldb;

import java.util.List;
import java.util.Map;

public class QueryResponse {
    private String sql;
    private List<Map<String, Object>> results;
    private int rowCount;
    private String error;

    public QueryResponse() {}

    // Success response
    public static QueryResponse success(String sql, List<Map<String, Object>> results) {
        QueryResponse response = new QueryResponse();
        response.sql = sql;
        response.results = results;
        response.rowCount = results.size();
        return response;
    }

    // Error response
    public static QueryResponse error(String error) {
        QueryResponse response = new QueryResponse();
        response.error = error;
        return response;
    }

    // Error response with SQL
    public static QueryResponse error(String sql, String error) {
        QueryResponse response = new QueryResponse();
        response.sql = sql;
        response.error = error;
        return response;
    }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public List<Map<String, Object>> getResults() { return results; }
    public void setResults(List<Map<String, Object>> results) { this.results = results; }
    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
