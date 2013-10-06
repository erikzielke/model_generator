package com.github.erikzielke.model_generator.configuration;

/**
 * Created by Erik on 10/6/13.
 */
public class Generation {
    private String destinationDir;
    private String destinationPackage;

    public String getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(String destinationDir) {
        this.destinationDir = destinationDir;
    }

    public String getDestinationPackage() {
        return destinationPackage;
    }

    public void setDestinationPackage(String destinationPackage) {
        this.destinationPackage = destinationPackage;
    }
}
