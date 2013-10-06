package com.github.erikzielke.model_generator.codegenerator.namingstrategy;

import com.github.erikzielke.model_generator.databasemodel.Column;
import com.github.erikzielke.model_generator.databasemodel.Table;

/**
 * This is the interface for the naming strategy.
 */
public interface NamingStrategyInterface {
    String getFieldName(Column column);

    String getSetterName(Column column);

    String getGetterName(Column column);

    String getPojoName(Table column);

    void setTablePrefix(String prefix);
}
