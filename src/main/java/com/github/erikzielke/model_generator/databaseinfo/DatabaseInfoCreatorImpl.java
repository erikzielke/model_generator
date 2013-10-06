package com.github.erikzielke.model_generator.databaseinfo;

import com.github.erikzielke.model_generator.configuration.Configuration;
import com.github.erikzielke.model_generator.databasemodel.Database;
import com.github.erikzielke.model_generator.databasemodel.Column;
import com.github.erikzielke.model_generator.databasemodel.Index;
import com.github.erikzielke.model_generator.databasemodel.Table;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by Erik on 10/2/13.
 */
public class DatabaseInfoCreatorImpl implements DatabaseInfoCreator {
    private Configuration configuration;

    @Override
    public Database create(Configuration configuration, Connection connection) {
        this.configuration = configuration;
        Database database = null;
        try {
            database = new Database(connection.getCatalog());
            readTables(database, connection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return database;
    }

    private void readTables(Database database, Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet tables = metaData.getTables(null, null, null, new String[]{"TABLE"});

        List<String> excludeTable = configuration.getExcludeTable();
        List<Pattern> patternList = new ArrayList<Pattern>();
        for (String exclude : excludeTable) {
            patternList.add(Pattern.compile(exclude));
        }




        while (tables.next()) {
            String tableName = tables.getString("TABLE_NAME");
            Table table = new Table(database, tableName);

            boolean shouldExclude = false;
            for (Pattern pattern : patternList) {
                if (pattern.matcher(tableName).matches()) {
                    shouldExclude = true;
                    break;
                }
            }

            if (!shouldExclude) {
                database.addTable(table);
            }

        }
        tables.close();

        for (Table table : database.getTables()) {
            readColumns(table, connection);
            readIndexes(table, connection);
            readPrimaryKey(table, connection);
        }

    }

    private void readPrimaryKey(Table table, Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, table.getName());
        Index index = new Index("PRIMARY");
        while (primaryKeys.next()) {
            String fieldName = primaryKeys.getString("COLUMN_NAME");
            Column columnByName = table.findColumnByName(fieldName);
            index.addColumn(columnByName);
        }
        table.setPrimaryKey(index);
        primaryKeys.close();
    }

    private void readIndexes(Table table, Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet indexes = metaData.getIndexInfo(null, null, table.getName(), false, false);
        Map<String, Index> indexMap = new HashMap<String, Index>();
        while (indexes.next()) {
            String indexName = indexes.getString("INDEX_NAME");
            if (!"PRIMARY".equals(indexName)) {

                Index index = indexMap.get(indexName);
                if (index == null) {
                    index = new Index(indexName);
                    indexMap.put(indexName, index);
                }

                String columnName = indexes.getString("COLUMN_NAME");
                Column columnByName = table.findColumnByName(columnName);
                index.addColumn(columnByName);

            }
        }
        Collection<Index> indexCollection = indexMap.values();
        for (Index index : indexCollection) {
            table.addIndex(index);
        }
        indexes.close();
    }

    private void readColumns(Table table, Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet columns = metaData.getColumns(null, null, table.getName(), null);
        while (columns.next()) {
            Column column = new Column();
            String columnName = columns.getString("COLUMN_NAME");
            column.setTable(table);
            String isAutoIncrement = columns.getString("IS_AUTOINCREMENT");
            column.setAutoIncrement("YES".equals(isAutoIncrement));
            column.setName(columnName);
            int isNullable = columns.getInt("NULLABLE");
            boolean nullable = false;
            switch (isNullable) {
                case DatabaseMetaData.attributeNoNulls:
                    nullable = false;
                    break;
                case DatabaseMetaData.attributeNullableUnknown:
                    nullable = true;
                    break;
                case DatabaseMetaData.attributeNullable:
                    nullable = true;

            }
            column.setNullable(nullable);
            column.setType(columns.getInt("DATA_TYPE"));
            table.addColumn(column);
        }
        columns.close();
    }
}
