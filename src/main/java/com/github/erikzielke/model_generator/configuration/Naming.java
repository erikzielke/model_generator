package com.github.erikzielke.model_generator.configuration;

import com.sun.codemodel.JExpression;

/**
 * Created by Erik on 10/6/13.
 */
public class Naming {
    private String tablePrefix;
    private String jndi;

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public String getJndi() {
        return jndi;
    }

    public void setJndi(String jndi) {
        this.jndi = jndi;
    }
}
