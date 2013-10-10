package com.github.erikzielke.model_generator;

import com.github.erikzielke.model_generator.codegenerator.JavaCodeGenerator;
import com.github.erikzielke.model_generator.configuration.Configuration;
import com.github.erikzielke.model_generator.configuration.Generation;
import com.github.erikzielke.model_generator.configuration.Jdbc;
import com.github.erikzielke.model_generator.configuration.Naming;
import com.github.erikzielke.model_generator.databaseinfo.DatabaseInfoCreator;
import com.github.erikzielke.model_generator.databaseinfo.DatabaseInfoCreatorImpl;
import com.github.erikzielke.model_generator.databasemodel.Database;
import org.codehaus.jackson.map.PropertyNamingStrategy;

import javax.xml.bind.JAXB;
import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * This runs the generation from the command line.
 */
public class CommandLineRunner {
    public static void main(String[] args) {

        if (args.length != 5 && args.length != 1) {
            System.out.println("usage: java -jar ModelGenerator jdbcConnectionString username password packageName destinationDirectory");
            System.exit(1);
        }

        Configuration configuration = new Configuration();
        if (args.length == 5) {
            Naming naming = new Naming();
            configuration.setNaming(naming);

            Generation generation = new Generation();
            generation.setDestinationPackage(args[3]);
            generation.setDestinationDir(args[4]);
            configuration.setGeneration(generation);

            Jdbc jdbc = new Jdbc();
            jdbc.setUrl(args[0]);
            jdbc.setUsername(args[1]);
            jdbc.setPassword(args[2]);
        }
        if (args.length == 1) {
            configuration = JAXB.unmarshal(new File(args[0]), Configuration.class);
        }

        File file = new File(configuration.getGeneration().getDestinationDir());
        if (!file.exists()) {
            System.out.println("Directory " + file.getPath() + " does not exists");
            System.exit(1);
        }

        //Loading driver
        try {
            DriverManager.getDriver(configuration.getJdbc().getUrl());
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        try {
            Connection connection = DriverManager.getConnection(configuration.getJdbc().getUrl(), configuration.getJdbc().getUsername(), configuration.getJdbc().getPassword());
            DatabaseInfoCreator databaseInfoCreator = new DatabaseInfoCreatorImpl();
            Database database = databaseInfoCreator.create(configuration, connection);

            JavaCodeGenerator javaCodeGenerator = new JavaCodeGenerator(configuration);
            javaCodeGenerator.getNamingStrategy().setTablePrefix(configuration.getNaming().getTablePrefix());
            javaCodeGenerator.setPackageName(configuration.getGeneration().getDestinationPackage());
            javaCodeGenerator.setDestinationDir(file);
            javaCodeGenerator.generateCode(database);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }
}
