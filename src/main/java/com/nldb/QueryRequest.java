package com.nldb;

public class QueryRequest {
    private String userQuery;

    public QueryRequest() {}

    public QueryRequest(String userQuery) {
        this.userQuery = userQuery;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }
}
