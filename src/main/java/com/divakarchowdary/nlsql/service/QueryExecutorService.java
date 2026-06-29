package com.divakarchowdary.nlsql.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryExecutorService {

    private static final int MAX_ROWS = 500;
    private final JdbcTemplate jdbc;

    private static final String[] BLOCKED_KEYWORDS = {
            "DROP", "TRUNCATE", "DELETE", "INSERT", "UPDATE",
            "ALTER", "GRANT", "REVOKE", "EXEC", "EXECUTE"
    };

    public List<Map<String, Object>> execute(String sql) {
        validateQuery(sql);

        log.info("Executing SQL: {}", sql);
        jdbc.setMaxRows(MAX_ROWS);

        List<Map<String, Object>> results = jdbc.queryForList(sql);
        log.info("Query returned {} rows", results.size());
        return results;
    }

    private void validateQuery(String sql) {
        String normalized = sql.trim().toUpperCase();


        if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
            throw new SecurityException(
                    "Only SELECT queries are allowed. Received: "
                            + sql.substring(0, Math.min(50, sql.length())));
        }

        for (String keyword : BLOCKED_KEYWORDS) {
            Pattern pattern = Pattern.compile("\\b" + keyword + "\\b");
            if (pattern.matcher(normalized).find()) {
                throw new SecurityException("Blocked keyword detected: " + keyword);
            }
        }
    }
}