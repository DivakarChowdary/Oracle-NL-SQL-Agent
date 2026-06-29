package com.divakarchowdary.nlsql.controller;

import com.divakarchowdary.nlsql.model.QueryRequest;
import com.divakarchowdary.nlsql.model.QueryResponse;
import com.divakarchowdary.nlsql.service.NlSqlAgentService;
import com.divakarchowdary.nlsql.service.SchemaMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NlSqlController {

    private final NlSqlAgentService agentService;
    private final SchemaMetadataService schemaService;

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        log.info("Query request: {}", request.getQuestion());
        QueryResponse response = agentService.processQuestion(request);
        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }

    @GetMapping("/tables")
    public ResponseEntity<List<String>> getTables() {
        return ResponseEntity.ok(schemaService.fetchAvailableTables());
    }

    @GetMapping("/schema/{tableName}")
    public ResponseEntity<Map<String, Object>> getSchema(@PathVariable String tableName) {
        var meta = schemaService.fetchMetadata(tableName.toUpperCase());
        return ResponseEntity.ok(Map.of(
                "tableName", meta.getTableName(),
                "columns", meta.getColumns(),
                "primaryKeys", meta.getPrimaryKeys(),
                "foreignKeys", meta.getForeignKeys(),
                "indexes", meta.getIndexes()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "agent", "Oracle NL-to-SQL Agent v1.0.0"
        ));
    }
}
