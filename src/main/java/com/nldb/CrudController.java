package com.nldb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CrudController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ===================== TABLES LIST =====================

    @GetMapping("/tables")
    public ResponseEntity<List<String>> getTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'",
                String.class
        );
        return ResponseEntity.ok(tables);
    }

    // ===================== GENERIC CRUD =====================

    /**
     * READ — Get all rows from a table
     */
    @GetMapping("/crud/{table}")
    public ResponseEntity<Map<String, Object>> getAll(@PathVariable String table) {
        validateTableName(table);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + sanitize(table));
        return ResponseEntity.ok(Map.of("rows", rows, "count", rows.size()));
    }

    /**
     * READ — Get a single row by ID
     */
    @GetMapping("/crud/{table}/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String table, @PathVariable int id) {
        validateTableName(table);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + sanitize(table) + " WHERE id = ?", id
        );
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(rows.get(0));
    }

    /**
     * CREATE — Insert a new row
     */
    @PostMapping("/crud/{table}")
    public ResponseEntity<Map<String, Object>> create(@PathVariable String table, @RequestBody Map<String, Object> data) {
        validateTableName(table);
        String tableName = sanitize(table);

        // Remove id field if present (auto-generated)
        data.remove("id");
        data.remove("ID");

        if (data.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No data provided"));
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        StringBuilder placeholders = new StringBuilder("VALUES (");
        Object[] values = new Object[data.size()];

        int i = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (i > 0) {
                sql.append(", ");
                placeholders.append(", ");
            }
            sql.append(sanitize(entry.getKey()));
            placeholders.append("?");
            values[i] = entry.getValue();
            i++;
        }

        sql.append(") ");
        placeholders.append(")");
        sql.append(placeholders);

        jdbcTemplate.update(sql.toString(), values);

        return ResponseEntity.ok(Map.of("message", "Record created successfully"));
    }

    /**
     * UPDATE — Update a row by ID
     */
    @PutMapping("/crud/{table}/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String table,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        validateTableName(table);
        String tableName = sanitize(table);

        // Remove id field from update data
        data.remove("id");
        data.remove("ID");

        if (data.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No data provided"));
        }

        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        Object[] values = new Object[data.size() + 1]; // +1 for WHERE id

        int i = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (i > 0) sql.append(", ");
            sql.append(sanitize(entry.getKey())).append(" = ?");
            values[i] = entry.getValue();
            i++;
        }

        sql.append(" WHERE id = ?");
        values[i] = id;

        int affected = jdbcTemplate.update(sql.toString(), values);
        if (affected == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("message", "Record updated successfully"));
    }

    /**
     * DELETE — Delete a row by ID
     */
    @DeleteMapping("/crud/{table}/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String table, @PathVariable int id) {
        validateTableName(table);
        int affected = jdbcTemplate.update("DELETE FROM " + sanitize(table) + " WHERE id = ?", id);
        if (affected == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "Record deleted successfully"));
    }

    /**
     * Get column metadata for a table
     */
    @GetMapping("/columns/{table}")
    public ResponseEntity<List<Map<String, Object>>> getColumns(@PathVariable String table) {
        validateTableName(table);
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT " +
                "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = 'PUBLIC' " +
                "ORDER BY ORDINAL_POSITION",
                sanitize(table).toUpperCase()
        );
        return ResponseEntity.ok(columns);
    }

    // ===================== SECURITY =====================

    /**
     * Dynamically validates that the table exists in the PUBLIC schema.
     * This allows tables created via the chat feature to be accessible.
     */
    private void validateTableName(String table) {
        List<String> existingTables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'",
                String.class
        );
        if (!existingTables.contains(table.toUpperCase())) {
            throw new IllegalArgumentException("Table not found: " + table);
        }
    }

    private String sanitize(String identifier) {
        // Only allow alphanumeric and underscores
        return identifier.replaceAll("[^a-zA-Z0-9_]", "");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
}
