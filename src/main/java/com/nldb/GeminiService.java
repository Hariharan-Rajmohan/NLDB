package com.nldb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GeminiService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final String GEMINI_API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    // Models ordered by preference — 2.5 models are current and have better availability
    private static final String[] MODELS = {
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemma-3-4b-it"
    };

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 2000;
    private static final long MIN_REQUEST_INTERVAL_MS = 1500; // minimum gap between API calls

    private final AtomicLong lastRequestTime = new AtomicLong(0);

    /**
     * Dynamically fetches the database schema from metadata.
     */
    public String getDatabaseSchema() {
        StringBuilder schema = new StringBuilder();
        try {
            Connection conn = jdbcTemplate.getDataSource().getConnection();
            DatabaseMetaData metaData = conn.getMetaData();

            // Get all tables in the PUBLIC schema
            ResultSet tables = metaData.getTables(null, "PUBLIC", "%", new String[]{"TABLE"});
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                schema.append("Table ").append(tableName.toLowerCase()).append("(");

                // Get columns for each table
                ResultSet columns = metaData.getColumns(null, "PUBLIC", tableName, "%");
                List<String> columnNames = new ArrayList<>();
                while (columns.next()) {
                    columnNames.add(columns.getString("COLUMN_NAME").toLowerCase());
                }
                schema.append(String.join(", ", columnNames));
                schema.append(")\n");
                columns.close();
            }
            tables.close();
            conn.close();
        } catch (SQLException e) {
            schema.append("Error fetching schema: ").append(e.getMessage());
        }
        return schema.toString().trim();
    }

    /**
     * Constructs the prompt and calls Gemini API to generate SQL.
     */
    public String generateSql(String userQuery) throws IOException, InterruptedException {
        String schema = getDatabaseSchema();

        String prompt = "Convert the following natural language request into a MySQL SQL query.\n\n"
                + "Database Schema:\n"
                + schema + "\n\n"
                + "User Request:\n"
                + userQuery + "\n\n"
                + "Rules:\n"
                + "- Return ONLY the SQL query, nothing else.\n"
                + "- Do not include any markdown formatting, code blocks, or backticks.\n"
                + "- Do not include explanations or comments.\n"
                + "- You can generate SELECT, INSERT, UPDATE, DELETE, CREATE TABLE, ALTER TABLE, and DROP TABLE statements.\n"
                + "- For INSERT statements, generate valid INSERT INTO syntax.\n"
                + "- For CREATE TABLE, include appropriate column types.\n"
                + "- The query must be valid MySQL syntax.\n";

        String apiKey = System.getProperty("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your_gemini_api_key_here")) {
            throw new IOException("GEMINI_API_KEY is not configured. Please set it in the .env file.");
        }

        // Build request body JSON manually to avoid extra dependencies
        String escapedPrompt = prompt.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        String requestBody = """
                {
                  "contents": [{
                    "parts": [{
                      "text": "%s"
                    }]
                  }],
                  "generationConfig": {
                    "temperature": 0.1,
                    "maxOutputTokens": 256
                  }
                }
                """.formatted(escapedPrompt);

        // Enforce minimum interval between requests to avoid rate limits
        long now = System.currentTimeMillis();
        long last = lastRequestTime.get();
        long elapsed = now - last;
        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed);
        }

        // Try each model with retries and exponential backoff
        IOException lastException = null;
        for (String model : MODELS) {
            String url = GEMINI_API_BASE + model + ":generateContent?key=" + apiKey;

            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                try {
                    lastRequestTime.set(System.currentTimeMillis());

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .timeout(Duration.ofSeconds(30))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 429) {
                        // Rate limited — wait and retry, then try next model
                        long waitTime = BASE_DELAY_MS * (long) Math.pow(2, attempt);
                        System.out.println("Rate limited (429) on model " + model + ". Waiting " + waitTime + "ms before retry " + (attempt + 1) + "/" + MAX_RETRIES);
                        Thread.sleep(waitTime);
                        lastException = new IOException("Rate limited by Gemini API (429).");
                        continue;
                    }

                    if (response.statusCode() != 200) {
                        throw new IOException("Gemini API error (HTTP " + response.statusCode() + "): " + response.body());
                    }

                    return extractTextFromResponse(response.body());

                } catch (IOException e) {
                    lastException = e;
                    if (attempt < MAX_RETRIES - 1) {
                        long waitTime = BASE_DELAY_MS * (long) Math.pow(2, attempt);
                        Thread.sleep(waitTime);
                    }
                }
            }
            // If all retries on this model failed with 429, log and try next model
            System.out.println("All retries exhausted for model " + model + ". Trying next model...");
        }
        throw lastException != null
                ? new IOException("All Gemini models are rate-limited. Please wait 30-60 seconds and try again.")
                : new IOException("Failed after retries");
    }

    /**
     * Generates SQL from a chat message with conversation history context.
     * Used for the dataset creator chat feature.
     */
    public String generateSqlFromChat(String userMessage, java.util.List<ChatRequest.ChatMessage> history)
            throws IOException, InterruptedException {

        String schema = getDatabaseSchema();

        // Build conversation context from history
        StringBuilder conversationContext = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            conversationContext.append("Previous conversation:\n");
            for (ChatRequest.ChatMessage msg : history) {
                conversationContext.append(msg.getRole().equals("user") ? "User: " : "Assistant: ");
                conversationContext.append(msg.getContent()).append("\n");
            }
            conversationContext.append("\n");
        }

        String prompt = "You are a database assistant that helps users create and manage datasets.\n\n"
                + "Current Database Schema:\n"
                + schema + "\n\n"
                + conversationContext
                + "User Request:\n"
                + userMessage + "\n\n"
                + "Rules:\n"
                + "- Generate valid MySQL SQL statements to fulfill the user's request.\n"
                + "- You can generate CREATE TABLE, INSERT INTO, ALTER TABLE, DROP TABLE, and SELECT statements.\n"
                + "- For CREATE TABLE, always include an 'id INT AUTO_INCREMENT PRIMARY KEY' column.\n"
                + "- When inserting multiple rows, generate separate INSERT statements, one per line.\n"
                + "- Do not include any markdown formatting, code blocks, or backticks.\n"
                + "- IMPORTANT: Your response must be in TWO parts separated by '---REPLY---':\n"
                + "  Part 1: The SQL statements (one per line)\n"
                + "  Part 2: A short, friendly confirmation message describing what was done.\n"
                + "- Example response format:\n"
                + "  CREATE TABLE products (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2));\n"
                + "  ---REPLY---\n"
                + "  Created table 'products' with columns: id, name, and price.\n"
                + "- If the user asks a question that doesn't need SQL (like 'what tables exist?'), respond with:\n"
                + "  ---REPLY---\n"
                + "  Your answer here.\n";

        String apiKey = System.getProperty("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your_gemini_api_key_here")) {
            throw new IOException("GEMINI_API_KEY is not configured. Please set it in the .env file.");
        }

        String escapedPrompt = prompt.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        String requestBody = """
                {
                  "contents": [{
                    "parts": [{
                      "text": "%s"
                    }]
                  }],
                  "generationConfig": {
                    "temperature": 0.2,
                    "maxOutputTokens": 2048
                  }
                }
                """.formatted(escapedPrompt);

        // Enforce minimum interval between requests
        long now = System.currentTimeMillis();
        long last = lastRequestTime.get();
        long elapsed = now - last;
        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed);
        }

        // Try each model with retries
        IOException lastException = null;
        for (String model : MODELS) {
            String url = GEMINI_API_BASE + model + ":generateContent?key=" + apiKey;

            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                try {
                    lastRequestTime.set(System.currentTimeMillis());

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .timeout(Duration.ofSeconds(30))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 429) {
                        long waitTime = BASE_DELAY_MS * (long) Math.pow(2, attempt);
                        System.out.println("Rate limited (429) on model " + model + " [chat]. Waiting " + waitTime + "ms");
                        Thread.sleep(waitTime);
                        lastException = new IOException("Rate limited by Gemini API (429).");
                        continue;
                    }

                    if (response.statusCode() != 200) {
                        throw new IOException("Gemini API error (HTTP " + response.statusCode() + "): " + response.body());
                    }

                    return extractTextFromResponse(response.body());

                } catch (IOException e) {
                    lastException = e;
                    if (attempt < MAX_RETRIES - 1) {
                        long waitTime = BASE_DELAY_MS * (long) Math.pow(2, attempt);
                        Thread.sleep(waitTime);
                    }
                }
            }
            System.out.println("All retries exhausted for model " + model + " [chat]. Trying next model...");
        }
        throw lastException != null
                ? new IOException("All Gemini models are rate-limited. Please wait 30-60 seconds and try again.")
                : new IOException("Failed after retries");
    }

    /**
     * Extracts the text content from the Gemini API JSON response.
     * Simple JSON parsing without external library.
     */
    private String extractTextFromResponse(String json) throws IOException {
        // Look for the "text" field in candidates[0].content.parts[0].text
        String marker = "\"text\"";
        int textIdx = json.indexOf(marker);
        if (textIdx == -1) {
            throw new IOException("Unexpected Gemini response format: no 'text' field found. Response: " + json);
        }

        // Find the colon after "text"
        int colonIdx = json.indexOf(':', textIdx);
        if (colonIdx == -1) {
            throw new IOException("Unexpected Gemini response format after 'text' field.");
        }

        // Find the opening quote of the value
        int openQuote = json.indexOf('"', colonIdx + 1);
        if (openQuote == -1) {
            throw new IOException("Unexpected Gemini response format: no value for 'text'.");
        }

        // Find the closing quote (handle escaped quotes)
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = openQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> result.append('\n');
                    case 't' -> result.append('\t');
                    case 'r' -> result.append('\r');
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    default -> { result.append('\\'); result.append(c); }
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
            }
        }

        String sql = result.toString().trim();

        // Clean up any residual markdown formatting
        if (sql.startsWith("```")) {
            sql = sql.replaceAll("```(?:sql)?\\s*", "").replaceAll("```\\s*$", "").trim();
        }

        return sql;
    }
}
