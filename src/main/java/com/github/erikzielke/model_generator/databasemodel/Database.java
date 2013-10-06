package com.github.erikzielke.model_generator.databasemodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a database.
 */
public class Database {
    private String name;
    private List<Table> tables;

    public Database(String name) {
        this.name = name;
        tables = new ArrayList<Table>();
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addTable(Table table) {
        tables.add(table);
    }

    public List<Table> getTables() {
        return Collections.unmodifiableList(tables);
    }
}
