package com.jdblabs.gtd

public class Item {
    
    public File file
    public Map gtdProperties = [:]

    public Item(File f) {
        this.file = f

        def javaProps = new Properties()
        f.withReader { reader -> javaProps.load(reader) }
        javaProps.each { k, v -> gtdProperties[k] = PropertyHelp.parse(v) } }

    public void save() {
        def javaProps = new Properties()
        gtdProperties.each { k, v -> javaProps[k] = PropertyHelp.format(v) }
        file.withOutputStream { os -> javaProps.store(os, "") } }

    public def propertyMissing(String name, def value) {
        gtdProperties[name] = value }

    public def propertyMissing(String name) { return gtdProperties[name] }

    public String toString() { 
        if (gtdProperties.action) return gtdProperties.action
        if (gtdProperties.outcome) return gtdProperties.outcome
        if (gtdProperties.title) return gtdProperties.title
        return file.name.replaceAll(/[-_]/, " ").capitalize() }
}
