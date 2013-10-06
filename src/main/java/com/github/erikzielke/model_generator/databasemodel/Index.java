package com.github.erikzielke.model_generator.databasemodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Indices on a table.
 */
public class Index {
    /**
     * Name of index.
     */
    private String name;
    /**
     * Fields in the index.
     */
    private List<Column> columns;

    public Index(String name) {
        this.name = name;
        this.columns = new ArrayList<Column>();
    }

    public String getName() {
        return name;
    }

    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public void addColumn(Column column) {
        columns.add(column);
    }
}
