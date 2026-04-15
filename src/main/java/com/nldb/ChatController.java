package com.nldb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Block truly dangerous operations
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "\\b(DROP\\s+DATABASE|TRUNCATE|GRANT|REVOKE|EXEC|EXECUTE|SHUTDOWN|DATABASE|SYSTEM|CALL)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Chat endpoint — accepts a message + conversation history,
     * generates SQL via Gemini, executes it, and returns a reply.
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String message = request.getMessage();

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ChatResponse.error("Please enter a message."));
        }

        try {
            // Step 1: Call Gemini with chat context
            String rawResponse = geminiService.generateSqlFromChat(message, request.getHistory());

            if (rawResponse == null || rawResponse.isBlank()) {
                return ResponseEntity.ok(ChatResponse.error(
                        "I couldn't understand that request. Could you try rephrasing?"));
            }

            // Step 2: Parse the response into SQL + reply parts
            String sqlPart = "";
            String replyPart = "";

            if (rawResponse.contains("---REPLY---")) {
                String[] parts = rawResponse.split("---REPLY---", 2);
                sqlPart = parts[0].trim();
                replyPart = parts.length > 1 ? parts[1].trim() : "";
            } else {
                // If no separator, treat the whole response as a reply (no SQL)
                replyPart = rawResponse.trim();
            }

            // Clean up any markdown formatting from SQL
            if (sqlPart.startsWith("```")) {
                sqlPart = sqlPart.replaceAll("```(?:sql)?\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            boolean tablesUpdated = false;

            // Step 3: Execute SQL if present
            if (!sqlPart.isEmpty()) {
                // Check for dangerous operations
                if (DANGEROUS_PATTERN.matcher(sqlPart).find()) {
                    return ResponseEntity.ok(ChatResponse.error(
                            "Sorry, that operation is not allowed for security reasons."));
                }

                // Split into individual statements and execute each
                String[] statements = sqlPart.split(";");
                int totalAffected = 0;
                StringBuilder executedSql = new StringBuilder();

                for (String stmt : statements) {
                    String trimmed = stmt.trim();
                    if (trimmed.isEmpty()) continue;

                    // Clean markdown from individual statements too
                    if (trimmed.startsWith("```")) {
                        trimmed = trimmed.replaceAll("```(?:sql)?\\s*", "")
                                .replaceAll("```\\s*$", "").trim();
                    }
                    if (trimmed.isEmpty()) continue;

                    String upper = trimmed.toUpperCase();

                    if (upper.startsWith("SELECT") || upper.startsWith("SHOW") || upper.startsWith("DESCRIBE")) {
                        // Read query — execute but don't count as affected
                        List<Map<String, Object>> results = jdbcTemplate.queryForList(trimmed);
                        if (replyPart.isEmpty()) {
                            replyPart = "Query returned " + results.size() + " rows.";
                        }
                    } else {
                        // DDL/DML — execute and track
                        int affected = jdbcTemplate.update(trimmed);
                        totalAffected += affected;

                        // Check if this creates or alters a table
                        if (upper.startsWith("CREATE") || upper.startsWith("ALTER") || upper.startsWith("DROP")) {
                            tablesUpdated = true;
                        }
                    }

                    executedSql.append(trimmed).append(";\n");
                }

                // If no reply was generated, create one
                if (replyPart.isEmpty()) {
                    replyPart = "Done! " + totalAffected + " row(s) affected.";
                }

                return ResponseEntity.ok(
                        ChatResponse.success(replyPart, executedSql.toString().trim(), tablesUpdated));
            }

            // No SQL to execute — just return the reply
            return ResponseEntity.ok(ChatResponse.success(replyPart, null, false));

        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.length() > 300) {
                msg = msg.substring(0, 300) + "...";
            }
            return ResponseEntity.ok(ChatResponse.error(
                    "Error: " + msg));
        }
    }

    /**
     * Returns all tables in the database (for the CRUD dynamic table list).
     */
    @GetMapping("/tables")
    public ResponseEntity<List<String>> getAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'",
                String.class
        );
        return ResponseEntity.ok(tables);
    }
}
