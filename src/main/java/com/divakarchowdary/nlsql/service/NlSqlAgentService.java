package com.divakarchowdary.nlsql.service;

import com.divakarchowdary.nlsql.model.QueryRequest;
import com.divakarchowdary.nlsql.model.QueryResponse;
import com.divakarchowdary.nlsql.model.SchemaMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class NlSqlAgentService {

    private final SchemaMetadataService schemaService;
    private final ClaudeSqlGeneratorService claudeService;
    private final QueryExecutorService executorService;

    public QueryResponse processQuestion(QueryRequest request) {
        long start = System.currentTimeMillis();
        String generatedSql = null;

        try {
            log.info("Processing question: {}", request.getQuestion());


            String tableName = resolveTable(request);

            SchemaMetadata metadata = schemaService.fetchMetadata(tableName);
            log.info("Schema fetched: {} columns, {} indexes, {} FK(s)",
                    metadata.getColumns().size(),
                    metadata.getIndexes().size(),
                    metadata.getForeignKeys().size());

            generatedSql = claudeService.generateSql(request.getQuestion(), metadata);

            List<Map<String, Object>> results = executorService.execute(generatedSql);

            long elapsed = System.currentTimeMillis() - start;
            log.info("Pipeline complete in {}ms — {} rows", elapsed, results.size());

            return QueryResponse.builder()
                    .question(request.getQuestion())
                    .generatedSql(generatedSql)
                    .results(results)
                    .rowCount(results.size())
                    .executionTimeMs(elapsed)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Pipeline failed: {}", e.getMessage(), e);
            return QueryResponse.error(request.getQuestion(), generatedSql, e.getMessage());
        }
    }

    private String resolveTable(QueryRequest request) {
        if (request.getTableName() != null && !request.getTableName().isBlank()) {
            return request.getTableName().toUpperCase();
        }

        // Auto-detect: ask Claude which table the question is about
        List<String> tables = schemaService.fetchAvailableTables();
        if (tables.isEmpty()) {
            throw new RuntimeException("No tables found in the current Oracle schema.");
        }

        String detected = claudeService.detectTableName(request.getQuestion(), tables);
        if (detected.isBlank() || !tables.contains(detected)) {
            log.warn("Table detection returned '{}', falling back to first table: {}", detected, tables.get(0));
            return tables.get(0).toUpperCase();
        }

        return detected;
    }
}
