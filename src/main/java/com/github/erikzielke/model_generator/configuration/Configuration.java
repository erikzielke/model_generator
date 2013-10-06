package com.github.erikzielke.model_generator.configuration;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Erik on 10/6/13.
 */
@XmlRootElement
public class Configuration {
    private Jdbc jdbc;
    private Naming naming;
    private Generation generation;
    private List<String> excludeTable;

    public Configuration() {
        this.excludeTable = new ArrayList<String>();
    }

    public Jdbc getJdbc() {
        return jdbc;
    }

    public void setJdbc(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public Naming getNaming() {
        return naming;
    }

    public void setNaming(Naming naming) {
        this.naming = naming;
    }

    public Generation getGeneration() {
        return generation;
    }

    public void setGeneration(Generation generation) {
        this.generation = generation;
    }

    public List<String> getExcludeTable() {
        return excludeTable;
    }

    public void setExcludeTable(List<String> excludeTable) {
        this.excludeTable = excludeTable;
    }
}
