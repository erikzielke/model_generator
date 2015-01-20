package com.github.erikzielke.model_generator.codegenerator.namingstrategy;

import com.github.erikzielke.model_generator.databasemodel.Column;
import com.github.erikzielke.model_generator.databasemodel.Table;
import org.apache.commons.lang.StringUtils;

/**
 * Created by Erik on 10/6/13.
 */
public class DefaultNamingStrategy implements NamingStrategyInterface {
    private String tablePrefix;

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public String getFieldName(Column column) {
        String fieldName = snakeCaseToCamelCase(column.getName());
        fieldName = fieldName.replaceAll("\\-", "_");
        return fieldName;
    }

    public String getSetterName(Column column) {
        String str = snakeCaseToCamelCase(column.getName());
        str = str.replaceAll("\\-", "_");
        return "set" + StringUtils.capitalize(str);
    }

    public String getGetterName(Column column) {
        String str = snakeCaseToCamelCase(column.getName());
        str = str.replaceAll("\\-", "_");
        return "get" + StringUtils.capitalize(str);
    }

    public String getPojoName(Table table) {
        if (tablePrefix != null && table.getName().startsWith(tablePrefix)) {
            return StringUtils.capitalize(snakeCaseToCamelCase(table.getName().replaceFirst(tablePrefix, "")));
        }
        return StringUtils.capitalize(snakeCaseToCamelCase(table.getName()));
    }

    private String snakeCaseToCamelCase(String snakeCased) {
        StringBuilder stringBuilder = new StringBuilder();
        boolean capital = false;
        for (int i = 0; i < snakeCased.length(); i++) {
            char character = snakeCased.charAt(i);

            if (character != '_')
                if (capital) {
                    capital = false;
                    String st = new String(new char[]{character});
                    stringBuilder.append(st.toUpperCase());
                } else {
                    stringBuilder.append(character);
                }
            else {
                capital = true;

            }


        }
        return stringBuilder.toString();
    }
}
