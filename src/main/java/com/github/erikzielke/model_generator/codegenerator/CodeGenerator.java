package com.github.erikzielke.model_generator.codegenerator;

import com.github.erikzielke.model_generator.databasemodel.Database;

/**
 * Created by Erik on 10/2/13.
 */
public interface CodeGenerator {
    public void generateCode(Database database);
}
