package com.github.erikzielke.model_generator.configuration;

import junit.framework.TestCase;

import javax.xml.bind.JAXB;
import java.io.File;
import java.io.InputStream;

/**
 * Created by Erik on 10/6/13.
 */
public class ConfigurationTest extends TestCase {
    public void testOutputConfiguration() throws Exception {
        Configuration configuration = new Configuration();
        Jdbc jdbc = new Jdbc();

        jdbc.setUrl("jdbc:mysql://localhost/responsfabrikken_appsync");
        jdbc.setUsername("root");
        jdbc.setPassword("Zwq93hhU");

        Generation generation = new Generation();
        generation.setDestinationDir(".");
        generation.setDestinationPackage("dk");

        Naming naming = new Naming();
        naming.setTablePrefix("appsync_");

        configuration.setJdbc(jdbc);
        configuration.setNaming(naming);
        configuration.setGeneration(generation);
        configuration.getExcludeTable().add("afdasdf");
        configuration.getExcludeTable().add("afdasdf");

        JAXB.marshal(configuration, System.out);
    }

    public void testInputConfiguration() throws Exception {
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("config.xml");
        Configuration unmarshal = JAXB.unmarshal(resourceAsStream, Configuration.class);

        assertEquals("myprefix_", unmarshal.getNaming().getTablePrefix());
        assertEquals(2, unmarshal.getExcludeTable().size());
    }
}
