package com.nldb;

public class ChatResponse {
    private String reply;
    private String sql;
    private boolean success;
    private boolean tablesUpdated;
    private String error;

    public ChatResponse() {}

    public static ChatResponse success(String reply, String sql, boolean tablesUpdated) {
        ChatResponse r = new ChatResponse();
        r.reply = reply;
        r.sql = sql;
        r.success = true;
        r.tablesUpdated = tablesUpdated;
        return r;
    }

    public static ChatResponse error(String error) {
        ChatResponse r = new ChatResponse();
        r.error = error;
        r.success = false;
        return r;
    }

    // Getters & Setters
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public boolean isTablesUpdated() { return tablesUpdated; }
    public void setTablesUpdated(boolean tablesUpdated) { this.tablesUpdated = tablesUpdated; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
