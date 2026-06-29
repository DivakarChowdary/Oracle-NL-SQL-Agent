package com.divakarchowdary.nlsql;

import com.divakarchowdary.nlsql.model.QueryResponse;
import com.divakarchowdary.nlsql.service.QueryExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

class OracleNlSqlAgentTests {

    @Test
    void queryResponse_success_hasCorrectFields() {
        QueryResponse resp = QueryResponse.builder()
                .question("How many rows?")
                .generatedSql("SELECT COUNT(*) AS total FROM my_table")
                .results(List.of(Map.of("TOTAL", 42)))
                .rowCount(1)
                .executionTimeMs(123)
                .success(true)
                .build();

        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getRowCount());
        assertEquals(123, resp.getExecutionTimeMs());
        assertNull(resp.getError());
    }

    @Test
    void queryResponse_error_hasErrorMessage() {
        QueryResponse resp = QueryResponse.error(
                "Show me data", "SELECT * FROM unknown_table", "Table not found");

        assertFalse(resp.isSuccess());
        assertEquals("Table not found", resp.getError());
        assertEquals("SELECT * FROM unknown_table", resp.getGeneratedSql());
    }

    @Test
    void queryExecutorService_blocksNonSelectQueries() {
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        QueryExecutorService service = new QueryExecutorService(mockJdbc);

        assertThrows(SecurityException.class, () -> service.execute("DELETE FROM my_table"));
        assertThrows(SecurityException.class, () -> service.execute("DROP TABLE my_table"));
        assertThrows(SecurityException.class, () -> service.execute("INSERT INTO my_table VALUES (1)"));
        assertThrows(SecurityException.class, () -> service.execute("UPDATE my_table SET col = 1"));
    }

    @Test
    void queryExecutorService_allowsSelectQuery() {
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        when(mockJdbc.queryForList(anyString()))
                .thenReturn(List.of(Map.of("COL", "value")));

        QueryExecutorService service = new QueryExecutorService(mockJdbc);
        List<Map<String, Object>> results = service.execute("SELECT * FROM my_table");

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void queryExecutorService_allowsWithQuery() {
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        when(mockJdbc.queryForList(anyString()))
                .thenReturn(List.of(Map.of("CNT", 5)));

        QueryExecutorService service = new QueryExecutorService(mockJdbc);
        List<Map<String, Object>> results = service.execute(
                "WITH cte AS (SELECT * FROM t) SELECT COUNT(*) AS cnt FROM cte");

        assertNotNull(results);
    }
}
