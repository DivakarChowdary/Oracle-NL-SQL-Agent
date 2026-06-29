package com.divakarchowdary.nlsql.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.divakarchowdary.nlsql.model.SchemaMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Slf4j
@Service
public class ClaudeSqlGeneratorService {

    @Value("${anthropic.api-key}")
    private String apiKey;

    private AnthropicClient client;
    private final SchemaMetadataService schemaService;

    public ClaudeSqlGeneratorService(SchemaMetadataService schemaService) {
        this.schemaService = schemaService;
    }

    @PostConstruct
    public void init() {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        log.info("Claude SQL Generator initialized.");
    }

    public String generateSql(String question, SchemaMetadata metadata) {
        log.info("Generating SQL for: {}", question);

        String schemaContext = schemaService.formatForPrompt(metadata);

        String prompt = """
                You are an expert Oracle SQL developer. Convert the following natural-language question
                into an optimized Oracle SQL query.

                SCHEMA INFORMATION:
                %s

                QUESTION: %s

                RULES:
                1. Return ONLY the SQL query — no explanation, no markdown, no code fences.
                2. Use the indexed columns in WHERE clauses to ensure optimal query performance.
                3. Use proper Oracle SQL syntax (not MySQL/PostgreSQL).
                4. If the question involves counts, use COUNT(*) or COUNT(column_name) appropriately.
                5. For date comparisons use Oracle date functions (TRUNC, TO_DATE, SYSDATE).
                6. Always alias columns in SELECT for readability.
                7. Use the primary key and foreign key relationships when joining tables.
                8. Prefer indexed columns in ORDER BY and WHERE clauses.
                9. If the question is ambiguous, write the most likely intended query.
                10. Do not include a trailing semicolon.

                SQL QUERY:
                """.formatted(schemaContext, question);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_SONNET_4_6)
                    .maxTokens(1000L)
                    .addUserMessage(prompt)
                    .build();

            Message response = client.messages().create(params);

            String sql = response.content().stream()
                    .filter(ContentBlock::isText)
                    .map(ContentBlock::asText)
                    .map(TextBlock::text)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Empty response from Claude"))
                    .trim();

            sql = sql.replaceAll("^```sql\\s*", "")
                     .replaceAll("^```\\s*", "")
                     .replaceAll("\\s*```$", "")
                     .trim();

            log.info("Generated SQL: {}", sql);
            return sql;

        } catch (Exception e) {
            log.error("SQL generation failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate SQL: " + e.getMessage(), e);
        }
    }

    public String detectTableName(String question, java.util.List<String> availableTables) {
        log.info("Detecting table for question: {}", question);

        String tableList = String.join("\n", availableTables);

        String prompt = """
                Given the following question and list of available Oracle tables,
                return ONLY the single most relevant table name (exact match from the list).
                If multiple tables are needed, return only the primary/main table.
                Return NOTHING else — just the table name.

                AVAILABLE TABLES:
                %s

                QUESTION: %s

                TABLE NAME:
                """.formatted(tableList, question);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_SONNET_4_6)
                    .maxTokens(50L)
                    .addUserMessage(prompt)
                    .build();

            Message response = client.messages().create(params);

            String detected = response.content().stream()
                    .filter(ContentBlock::isText)
                    .map(ContentBlock::asText)
                    .map(TextBlock::text)
                    .findFirst()
                    .orElse("")
                    .trim()
                    .toUpperCase();

            log.info("Detected table: {}", detected);
            return detected;

        } catch (Exception e) {
            log.error("Table detection failed: {}", e.getMessage());
            return availableTables.isEmpty() ? "" : availableTables.get(0).toUpperCase();
        }
    }
}
