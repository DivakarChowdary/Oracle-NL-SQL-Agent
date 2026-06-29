package com.divakarchowdary.nlsql.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SchemaMetadata {
    private String tableName;
    private List<ColumnInfo> columns;
    private List<String> primaryKeys;
    private List<ForeignKeyInfo> foreignKeys;
    private List<IndexInfo> indexes;
    private List<String> uniqueConstraints;
    private List<String> checkConstraints;

    @Data
    @Builder
    public static class ColumnInfo {
        private String columnName;
        private String dataType;
        private boolean nullable;
        private Integer dataLength;
        private Integer dataPrecision;
    }

    @Data
    @Builder
    public static class ForeignKeyInfo {
        private String constraintName;
        private String columnName;
        private String referencedTable;
        private String referencedColumn;
    }

    @Data
    @Builder
    public static class IndexInfo {
        private String indexName;
        private List<String> columns;
        private boolean unique;
    }
}
