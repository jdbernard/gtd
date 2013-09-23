/** 
 * # PropertyHelp
 * @author Jonathan Bernard (jdb@jdb-labs.com)
 * @copyright 2013 [JDB Labs LLC](http://jdb-labs.com)
 */
package com.jdblabs.gtd

import org.joda.time.DateMidnight
import org.joda.time.DateTime

import java.text.SimpleDateFormat

/**
 * A poor man's serialization/deserialization library. Each Enum value
 * represents a datatype that this class knows how to handle. The Enum entries
 * consist of a regex to match data in textual form, a Class to match Java
 * objects, a parse function to convert the textual form to a Java object, and
 * a format function to convert a Java object to textual form.
 * @org gtd.jdb-labs.com/PropertyHelp
 */
public enum PropertyHelp {

    /// **Note:** Property types should be ordered here in order of decreasing
    /// specificity. That is, subclasses should come before the more general
    /// class so that objects are converted using the most specific class that
    /// PropertyHelp knows how to work with.

    /// #### `DATE_MIDNIGHT` ([`org.joda.time.DateMidnight`][joda-dm])
    /// [joda-dm]: http://joda-time.sourceforge.net/apidocs/org/joda/time/DateMidnight.html
    /// @example 2013-09-22
    DATE_MIDNIGHT(/^\d{4}-\d{2}-\d{2}$/, DateMidnight,
        { v -> DateMidnight.parse(v) },
        { d -> d.toString("YYYY-MM-dd") }),

    /// #### `DATETIME` ([`org.joda.time.DateTime`][joda-dt])
    /// [joda-dt]: http://joda-time.sourceforge.net/apidocs/org/joda/time/DateTime.html
    /// @example 2013-09-22T13:42:57
    DATETIME(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/, DateTime,
        { v -> DateTime.parse(v) },
        { d -> d.toString("YYYY-MM-dd'T'HH:mm:ss") }),

    /// We never want to parse a value into a [`java.util.Date`][java-dt] or
    /// [`java.util.Calendar`][java-cal] object (we are using Joda Time instead
    /// of the standard Java Date and Calendar objects) but we do want to be
    /// able to handle if someone gives us a Date or Calendar object. 
    /// 
    /// [java-dt]: http://docs.oracle.com/javase/6/docs/api/java/util/Date.html
    /// [java-cal]: http://docs.oracle.com/javase/6/docs/api/java/util/Calendar.html

    /// #### `DATE` ([java.util.Date][java-dt])
    /// [java-dt]: http://docs.oracle.com/javase/6/docs/api/java/util/Date.html
    DATE(NEVER_MATCH, Date,
        { v -> v }, // never called
        { d -> dateFormat.format(d) }),

    /// #### `CALENDAR` ([`java.util.Calendar`][java-cal])
    /// [java-cal]: http://docs.oracle.com/javase/6/docs/api/java/util/Calendar.html
    CALENDAR(NEVER_MATCH, Calendar,
        { v -> v }, // never called
        { c ->
            def df = dateFormat.clone()
            df.calendar = c
            df.format(c.time) }),

    /// Similarly, we always parse integers into [`Long`][java-long] objects,
    /// and floating point values into [`Double`][java-double] objects.
    ///
    /// [java-long]: http://docs.oracle.com/javase/6/docs/api/java/lang/Long.html
    /// [java-double]: http://docs.oracle.com/javase/6/docs/api/java/lang/Double.html

    /// #### `INTEGER` ([`java.lang.Integer`][java-int])
    /// [java-int]: http://docs.oracle.com/javase/6/docs/api/java/lang/Integer.html
    INTEGER(NEVER_MATCH, Integer,
        { v -> v as Integer }, // never called
        { i -> i as String }),

    /// #### `LONG` ([`java.lang.Long`][java-long])
    /// [java-long]: http://docs.oracle.com/javase/6/docs/api/java/lang/Long.html
    LONG(/^\d+$/, Long,
        { v -> v as Long },
        { l -> l as String }),

    /// #### `FLOAT` ([`java.lang.Float`][java-float])
    /// [java-float]: http://docs.oracle.com/javase/6/docs/api/java/lang/Float.html
    FLOAT(NEVER_MATCH, Float,
        { v -> v as Float}, // never called
        { f -> f as String}),

    /// #### `DOUBLE` ([`java.lang.Double`][java-double])
    /// [java-double]: http://docs.oracle.com/javase/6/docs/api/java/lang/Double.html
    DOUBLE(/^\d+\.\d+$/, Double,
        { v -> v as Double },
        { d -> d as String });

    String pattern;
    Class klass;
    def parseFun, formatFun;

    private static SimpleDateFormat dateFormat =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    /// This pattern for can never match (is uses negative lookahead to
    /// contradict itself)
    private static String NEVER_MATCH = /(?!x)x/;

    /// #### constructor
    public PropertyHelp(String pattern, Class klass, def parseFun,
    def formatFun) {
        this.pattern = pattern
        this.klass = klass
        this.parseFun = parseFun
        this.formatFun = formatFun }

    /// #### `matches`
    /// Test if this Enum will match a given textual value.
    public boolean matches(String prop) { return prop ==~ pattern }

    /// Test if this Enum will match a given Java Class.
    public boolean matches(Class klass) { return this.klass == klass }

    /// #### `parse`
    /// Try to parse a given textual value.
    public static Object parse(String value) {
        /// Try to find a matching converter.
        def propertyType = PropertyHelp.values().find { it.matches(value) }

        /// Use the converter to parse the value. If we did not find a
        /// converter we assume this value is a plain string.
        return propertyType ? propertyType.parseFun(value) : value }

    /// #### `format`
    /// Try to format a given Java Object as a String.
    public static String format(def object) {
        /// Try to find a converter that can handle this object type.
        def propertyType = PropertyHelp.values().find {
            it.klass.isInstance(object) }

        /// Use the converter to format it as a string. If none was found we
        /// use the object's `toString()` method.
        return propertyType ? propertyType.formatFun(object) : object.toString() }
}
