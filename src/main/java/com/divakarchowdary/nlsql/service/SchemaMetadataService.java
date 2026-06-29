package com.divakarchowdary.nlsql.service;

import com.divakarchowdary.nlsql.model.SchemaMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaMetadataService {

    private final JdbcTemplate jdbc;

    public SchemaMetadata fetchMetadata(String tableName) {
        String upper = tableName.toUpperCase();
        log.info("Fetching schema metadata for table: {}", upper);

        return SchemaMetadata.builder()
                .tableName(upper)
                .columns(fetchColumns(upper))
                .primaryKeys(fetchPrimaryKeys(upper))
                .foreignKeys(fetchForeignKeys(upper))
                .indexes(fetchIndexes(upper))
                .uniqueConstraints(fetchUniqueConstraints(upper))
                .checkConstraints(fetchCheckConstraints(upper))
                .build();
    }

    public List<String> fetchAvailableTables() {
        String sql = "SELECT table_name FROM user_tables ORDER BY table_name";
        return jdbc.queryForList(sql, String.class);
    }

    /**
     * Fetches distinct values for VARCHAR/CHAR columns (max 20 distinct values per column).
     * This tells Claude the actual values stored so it generates correct WHERE clauses.
     * e.g. GENDER column has 'M', 'F' — not 'Male', 'Female'
     */
    public Map<String, List<String>> fetchSampleValues(String tableName) {
        Map<String, List<String>> sampleValues = new LinkedHashMap<>();
        List<SchemaMetadata.ColumnInfo> columns = fetchColumns(tableName.toUpperCase());

        for (SchemaMetadata.ColumnInfo col : columns) {
            String dataType = col.getDataType().toUpperCase();
            if (!dataType.contains("VARCHAR") && !dataType.contains("CHAR")) continue;

            try {
                String sql = String.format(
                        "SELECT DISTINCT %s FROM %s WHERE %s IS NOT NULL AND ROWNUM <= 20 ORDER BY 1",
                        col.getColumnName(), tableName, col.getColumnName());

                List<String> values = jdbc.queryForList(sql, String.class);
                if (!values.isEmpty()) {
                    sampleValues.put(col.getColumnName(), values);
                }
            } catch (Exception e) {
                log.debug("Could not fetch sample values for {}.{}: {}",
                        tableName, col.getColumnName(), e.getMessage());
            }
        }

        return sampleValues;
    }

    /**
     * Formats schema metadata into a structured string for the Claude prompt.
     * Includes actual column values so Claude uses the right values in WHERE clauses.
     */
    public String formatForPrompt(SchemaMetadata meta) {
        StringBuilder sb = new StringBuilder();
        sb.append("TABLE: ").append(meta.getTableName()).append("\n\n");

        sb.append("COLUMNS:\n");
        meta.getColumns().forEach(c -> sb.append(String.format(
                "  %-30s %s%s%n",
                c.getColumnName(),
                c.getDataType(),
                c.isNullable() ? "" : " NOT NULL")));

        if (!meta.getPrimaryKeys().isEmpty()) {
            sb.append("\nPRIMARY KEY: ")
                    .append(String.join(", ", meta.getPrimaryKeys())).append("\n");
        }

        if (!meta.getForeignKeys().isEmpty()) {
            sb.append("\nFOREIGN KEYS:\n");
            meta.getForeignKeys().forEach(fk -> sb.append(String.format(
                    "  %s → %s.%s%n",
                    fk.getColumnName(), fk.getReferencedTable(), fk.getReferencedColumn())));
        }

        if (!meta.getIndexes().isEmpty()) {
            sb.append("\nINDEXES:\n");
            meta.getIndexes().forEach(idx -> sb.append(String.format(
                    "  %s (%s)%s%n",
                    idx.getIndexName(),
                    String.join(", ", idx.getColumns()),
                    idx.isUnique() ? " UNIQUE" : "")));
        }

        if (!meta.getUniqueConstraints().isEmpty()) {
            sb.append("\nUNIQUE CONSTRAINTS: ")
                    .append(String.join(", ", meta.getUniqueConstraints())).append("\n");
        }

        // Critical fix: include actual stored values so Claude uses correct literals
        Map<String, List<String>> sampleValues = fetchSampleValues(meta.getTableName());
        if (!sampleValues.isEmpty()) {
            sb.append("\nACTUAL COLUMN VALUES (use ONLY these exact values in WHERE clauses):\n");
            sampleValues.forEach((col, values) ->
                    sb.append(String.format("  %s: %s%n", col,
                            values.stream()
                                    .map(v -> "'" + v + "'")
                                    .collect(Collectors.joining(", ")))));
        }

        return sb.toString();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private List<SchemaMetadata.ColumnInfo> fetchColumns(String tableName) {
        String sql = """
                SELECT column_name, data_type, nullable, data_length, data_precision
                FROM all_tab_columns
                WHERE table_name = ?
                ORDER BY column_id
                """;
        return jdbc.query(sql, (rs, row) -> SchemaMetadata.ColumnInfo.builder()
                .columnName(rs.getString("column_name"))
                .dataType(rs.getString("data_type"))
                .nullable("Y".equals(rs.getString("nullable")))
                .dataLength(rs.getInt("data_length"))
                .dataPrecision(rs.getInt("data_precision"))
                .build(), tableName);
    }

    private List<String> fetchPrimaryKeys(String tableName) {
        String sql = """
                SELECT cc.column_name
                FROM all_constraints c
                JOIN all_cons_columns cc ON c.constraint_name = cc.constraint_name
                    AND c.owner = cc.owner
                WHERE c.constraint_type = 'P'
                AND c.table_name = ?
                ORDER BY cc.position
                """;
        return jdbc.queryForList(sql, String.class, tableName);
    }

    private List<SchemaMetadata.ForeignKeyInfo> fetchForeignKeys(String tableName) {
        String sql = """
                SELECT c.constraint_name, cc.column_name,
                       rc.table_name AS referenced_table,
                       rcc.column_name AS referenced_column
                FROM all_constraints c
                JOIN all_cons_columns cc ON c.constraint_name = cc.constraint_name
                    AND c.owner = cc.owner
                JOIN all_constraints rc ON c.r_constraint_name = rc.constraint_name
                    AND c.owner = rc.owner
                JOIN all_cons_columns rcc ON rc.constraint_name = rcc.constraint_name
                    AND rc.owner = rcc.owner AND cc.position = rcc.position
                WHERE c.constraint_type = 'R'
                AND c.table_name = ?
                ORDER BY cc.position
                """;
        return jdbc.query(sql, (rs, row) -> SchemaMetadata.ForeignKeyInfo.builder()
                .constraintName(rs.getString("constraint_name"))
                .columnName(rs.getString("column_name"))
                .referencedTable(rs.getString("referenced_table"))
                .referencedColumn(rs.getString("referenced_column"))
                .build(), tableName);
    }

    private List<SchemaMetadata.IndexInfo> fetchIndexes(String tableName) {
        String sql = """
                SELECT i.index_name, i.uniqueness,
                       LISTAGG(ic.column_name, ', ')
                           WITHIN GROUP (ORDER BY ic.column_position) AS columns
                FROM all_indexes i
                JOIN all_ind_columns ic ON i.index_name = ic.index_name
                    AND i.owner = ic.index_owner
                WHERE i.table_name = ?
                GROUP BY i.index_name, i.uniqueness
                ORDER BY i.index_name
                """;
        return jdbc.query(sql, (rs, row) -> SchemaMetadata.IndexInfo.builder()
                .indexName(rs.getString("index_name"))
                .unique("UNIQUE".equals(rs.getString("uniqueness")))
                .columns(Arrays.asList(rs.getString("columns").split(",\\s*")))
                .build(), tableName);
    }

    private List<String> fetchUniqueConstraints(String tableName) {
        String sql = """
                SELECT cc.column_name
                FROM all_constraints c
                JOIN all_cons_columns cc ON c.constraint_name = cc.constraint_name
                    AND c.owner = cc.owner
                WHERE c.constraint_type = 'U'
                AND c.table_name = ?
                ORDER BY cc.position
                """;
        return jdbc.queryForList(sql, String.class, tableName);
    }

    private List<String> fetchCheckConstraints(String tableName) {
        String sql = """
                SELECT search_condition
                FROM all_constraints
                WHERE constraint_type = 'C'
                AND table_name = ?
                AND search_condition IS NOT NULL
                """;
        return jdbc.queryForList(sql, String.class, tableName);
    }
}