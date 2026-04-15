package com.nldb;

import java.util.List;

public class ChatRequest {
    private String message;
    private List<ChatMessage> history;

    public ChatRequest() {}

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<ChatMessage> getHistory() { return history; }
    public void setHistory(List<ChatMessage> history) { this.history = history; }

    /**
     * Represents a single chat message (user or assistant).
     */
    public static class ChatMessage {
        private String role; // "user" or "assistant"
        private String content;

        public ChatMessage() {}

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
