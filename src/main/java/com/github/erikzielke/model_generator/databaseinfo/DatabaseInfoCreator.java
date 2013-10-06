package com.github.erikzielke.model_generator.databaseinfo;

import com.github.erikzielke.model_generator.configuration.Configuration;
import com.github.erikzielke.model_generator.databasemodel.Database;

import java.sql.Connection;

/**
 *
 */
public interface DatabaseInfoCreator {
    Database create(Configuration configuration, Connection connection);
}
