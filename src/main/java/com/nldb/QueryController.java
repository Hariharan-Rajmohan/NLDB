package com.nldb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@CrossOrigin(origins = "*")
public class QueryController {

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Only block truly dangerous operations (DROP entire tables/databases)
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "\\b(DROP\\s+DATABASE|TRUNCATE|GRANT|REVOKE|EXEC|EXECUTE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> handleQuery(@RequestBody QueryRequest request) {
        String userQuery = request.getUserQuery();

        if (userQuery == null || userQuery.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(QueryResponse.error("Please enter a query."));
        }

        try {
            // Step 1: Generate SQL from natural language via Gemini
            String sql = geminiService.generateSql(userQuery);

            if (sql == null || sql.isBlank()) {
                return ResponseEntity.ok(QueryResponse.error("Gemini returned an empty response. Please try rephrasing your query."));
            }

            // Step 2: Block only truly dangerous operations
            if (DANGEROUS_PATTERN.matcher(sql).find()) {
                return ResponseEntity.ok(QueryResponse.error(sql,
                        "Security: This operation (DROP DATABASE/TRUNCATE/GRANT/REVOKE) is not allowed."));
            }

            // Step 3: Determine query type and execute accordingly
            String sqlUpper = sql.trim().toUpperCase();

            if (sqlUpper.startsWith("SELECT") || sqlUpper.startsWith("SHOW") || sqlUpper.startsWith("DESCRIBE")) {
                // SELECT / read queries — return result rows
                List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
                return ResponseEntity.ok(QueryResponse.success(sql, results));
            } else {
                // DDL/DML: CREATE, INSERT, UPDATE, DELETE, ALTER, DROP TABLE
                int affectedRows = jdbcTemplate.update(sql);
                String action = sqlUpper.split("\\s+")[0]; // CREATE, INSERT, UPDATE, DELETE, ALTER
                String message = action.charAt(0) + action.substring(1).toLowerCase()
                        + " operation completed successfully. " + affectedRows + " row(s) affected.";
                return ResponseEntity.ok(QueryResponse.success(sql, List.of(Map.of(
                        "STATUS", "✅ Success",
                        "OPERATION", action,
                        "ROWS_AFFECTED", affectedRows,
                        "MESSAGE", message
                ))));
            }

        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.length() > 300) {
                message = message.substring(0, 300) + "...";
            }
            return ResponseEntity.ok(QueryResponse.error(
                    "Error processing your query: " + message));
        }
    }

    /**
     * Health check / schema info endpoint
     */
    @GetMapping("/schema")
    public ResponseEntity<Map<String, String>> getSchema() {
        String schema = geminiService.getDatabaseSchema();
        return ResponseEntity.ok(Map.of("schema", schema));
    }
}

