package com.github.erikzielke.model_generator.configuration;

import junit.framework.TestCase;

import javax.xml.bind.JAXB;
import java.io.File;

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
        generation.setDestinationDir("C:\\Users\\Erik\\Documents\\model_generator\\src\\main\\java");
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
        Configuration unmarshal = JAXB.unmarshal(new File("C:\\Users\\Erik\\Documents\\model_generator\\src\\main\\java\\com\\github\\erikzielke\\model_generator\\configuration\\config.xml"), Configuration.class);

        assertEquals("appsync_", unmarshal.getNaming().getTablePrefix());
        assertEquals(2, unmarshal.getExcludeTable().size());
    }
}
