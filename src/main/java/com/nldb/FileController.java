package com.nldb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class FileController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String UPLOAD_DIR = "uploads";

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload."));
        }

        try {
            // Ensure upload directory exists
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Create a unique filename to prevent overwriting
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;
            Path filePath = uploadPath.resolve(uniqueFilename);

            // Save the file to disk
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // The URL path to access the file via browser
            String fileUrl = "/uploads/" + uniqueFilename;
            String fileType = file.getContentType();

            // Create files table if not exists
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS files (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "filename VARCHAR(255), " +
                    "file_url VARCHAR(255), " +
                    "file_type VARCHAR(100), " +
                    "uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Insert file metadata into database
            jdbcTemplate.update("INSERT INTO files (filename, file_url, file_type) VALUES (?, ?, ?)",
                    originalFilename, fileUrl, fileType);

            return ResponseEntity.ok(Map.of(
                    "message", "File uploaded successfully",
                    "fileUrl", fileUrl,
                    "fileName", originalFilename,
                    "fileType", fileType
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save file: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Database error: " + e.getMessage()));
        }
    }
}
