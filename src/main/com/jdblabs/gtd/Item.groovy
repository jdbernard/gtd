/** 
 * # Item
 * @author Jonathan Bernard (jdb@jdb-labs.com)
 * @copyright 2013 [JDB Labs LLC](http://jdb-labs.com)
 */
package com.jdblabs.gtd

/**
 * One Getting Things Done item (a page in David Allen's system). An item is
 * represented by a file on the filesystem, organized into one of the GTD
 * folders. The Item can have arbitrarily many properties, which are stored in
 * the Item file as standard Java properties. By convention the `action`
 * property is used as the item description (assuming this item is a next
 * action item). Other important properties include:
 *
 * * `outcome`: describes the desired outcome.
 * * `title`: the item title (optionally used if no `action` is defined).
 * * `date`: the due date for this item. Remember that we should not use the
 *   calendar to "schedule" items, but only to represent items which must be
 *   done by or on that day.
 * * `details`: more information related to this item.
 * @org gtd.jdb-labs.com/Item
 */
public class Item {
    
    public File file
    public Map gtdProperties = [:]

    /** 
     * #### constructor
     * Load an item from a file. The typical pattern for creating new Items is
     * to create the file first then pass that file to an Item constructor.
     * Files with no contents are valid GTD items (the file name is used as a
     * description in lieu of an `action` or `title` property). */
    public Item(File f) {
        this.file = f

        /// Read and parse the item's properties from the file.
        def javaProps = new Properties()
        f.withReader { reader -> javaProps.load(reader) }

        /// Properties are stored as plain text in the file. We use the
        /// [PropertyHelp](jlp://gtd.jdb-labs.com/PropertyHelp) Enum to
        /// serialize and deserialize the property objects.
        javaProps.each { k, v -> gtdProperties[k] = PropertyHelp.parse(v) } }

    /** #### `save`
      * Persist the Item to it's file. */
    public void save() {
        def javaProps = new Properties()
        gtdProperties.each { k, v -> javaProps[k] = PropertyHelp.format(v) }
        file.withOutputStream { os -> javaProps.store(os, "") } }

    /** #### `propertyMissing`
      * Provide an implementation of the Groovy dynamic [propertyMissing][1]
      * method to expose the gtdProperties map as properties on the item
      * itself.
      *
      * [1]: http://groovy.codehaus.org/Using+methodMissing+and+propertyMissing
      */
    public def propertyMissing(String name, def value) {
        gtdProperties[name] = value }

    public def propertyMissing(String name) { return gtdProperties[name] }

    /** #### `toString`
      * Provide a standard description of the item. This is used by the CLI
      * interface, for example, to directly display GTD items.
      *
      * Look first for the `action` property, then `outcome`, then `title`.
      * Failing to find any of these properties, pretty-print the filename
      * as a description. */
    public String toString() { 
        if (gtdProperties.action) return gtdProperties.action
        if (gtdProperties.outcome) return gtdProperties.outcome
        if (gtdProperties.title) return gtdProperties.title
        return file.name.replaceAll(/[-_]/, " ").capitalize() }
}
