package com.jdblabs.gtd.cli

public class Item {
    
    public File file
    public Map properties = [:]

    public Item(File f) {
        this.file = f

        def javaProps = new Properties()
        f.withReader { reader -> javaProps.load(reader) }
        javaProps.each { k, v -> properties[k] = PropertyHelp.parse(v) } }

    public void save() {
        def javaProps = new Properties()
        properties.each { k, v -> javaProps[k] = PropertyHelp.format(v) }
        file.withOutputStream { os -> javaProps.store(os, "") } }

    public def propertyMissing(String name, def value) {
        properties[name] = value }

    public def propertyMissing(String name) { return properties[name] }

    public String toString() { 
        if (properties.action) return properties.action
        if (properties.outcome) return properties.outcome
        if (properties.title) return properties.title
        return file.name.replaceAll(/[-_]/, " ").capitalize() }
}
