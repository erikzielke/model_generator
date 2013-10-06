package com.github.erikzielke.model_generator.databasemodel;

import java.util.*;

/**
 * Table in a database.
 */
public class Table {
    private String name;

    private Database database;
    private List<Column> columns;
    private List<Index> indexes;
    private Index primaryKey;

    private Map<String, Column> columnMap;

    public Table(Database database, String name) {
        this.database = database;
        this.name = name;
        this.columns = new ArrayList<Column>();
        this.indexes = new ArrayList<Index>();

        columnMap = new HashMap<String, Column>();
    }

    public String getName() {
        return name;
    }


    public void addColumn(Column column) {
        columns.add(column);
        columnMap.put(column.getName(), column);
    }

    public void addIndex(Index index) {
        indexes.add(index);
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public List<Index> getIndexes() {
        return Collections.unmodifiableList(indexes);
    }

    public void setPrimaryKey(Index primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Index getPrimaryKey() {
        return primaryKey;
    }

    public Column findColumnByName(String columnName) {
        return columnMap.get(columnName);
    }

    public boolean isColumnPartOfPrimaryKey(Column column) {
        if (primaryKey != null) {
            List<Column> primaryColumns = primaryKey.getColumns();
            for (Column primaryColumn : primaryColumns) {
                if (column.equals(primaryColumn)) {
                    return true;
                }
            }
        }
        return false;
    }
}
