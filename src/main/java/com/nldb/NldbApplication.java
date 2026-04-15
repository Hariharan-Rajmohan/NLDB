package com.nldb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NldbApplication {
    public static void main(String[] args) {
        // Load .env file before Spring starts (for local development)
        io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
                .ignoreIfMissing()
                .load();

        // Set Gemini API key as system property — check .env first, then system env var
        String apiKey = dotenv.get("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            System.setProperty("GEMINI_API_KEY", apiKey);
        }

        SpringApplication.run(NldbApplication.class, args);
    }
}
