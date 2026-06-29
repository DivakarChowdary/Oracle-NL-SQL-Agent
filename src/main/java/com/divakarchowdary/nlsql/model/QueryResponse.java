package com.divakarchowdary.nlsql.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class QueryResponse {
    private String question;
    private String generatedSql;
    private List<Map<String, Object>> results;
    private int rowCount;
    private long executionTimeMs;
    private String error;
    private boolean success;

    public static QueryResponse error(String question, String sql, String errorMessage) {
        return QueryResponse.builder()
                .question(question)
                .generatedSql(sql)
                .error(errorMessage)
                .success(false)
                .build();
    }
}
