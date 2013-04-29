package com.jdblabs.gtd.cli

import org.joda.time.DateMidnight
import org.joda.time.DateTime

import java.text.SimpleDateFormat

public enum PropertyHelp {

    // Property types should be ordered here in order of decreasing specificity.
    // That is, subclasses should come before the more general class so that
    // objects are converted using the most specific class that
    // PropertyHelp knows how to work with.
    DATE_MIDNIGHT(/^\d{4}-\d{2}-\d{2}$/, DateMidnight,
        { v -> DateMidnight.parse(v) },
        { d -> d.toString("YYYY-MM-dd") }),
    DATETIME(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/, DateTime,
        { v -> DateTime.parse(v) },
        { d -> d.toString("YYYY-MM-dd'T'HH:mm:ss") }),
    // We never want to parse a value into a java.util.Date or
    // java.util.Calendar object (we are using Joda Time instead of the
    // standard Java Date and Calendar objects) but we do want to be able to
    // handle if someone gives us a Date or Calendar object. 
    DATE(NEVER_MATCH, Date,
        { v -> v }, // never called
        { d -> dateFormat.format(d) }),
    CALENDAR(NEVER_MATCH, Calendar,
        { v -> v }, // never called
        { c ->
            def df = dateFormat.clone()
            df.calendar = c
            df.format(c.time) }),

    INTEGER(NEVER_MATCH, Integer,
        { v -> v as Integer }, // never called
        { i -> i as String }),
    LONG(/^\d+$/, Long,
        { v -> v as Long },
        { l -> l as String }),
    FLOAT(NEVER_MATCH, Float,
        { v -> v as Float}, // never called
        { f -> f as String}),
    DOUBLE(/^\d+\.\d+$/, Double,
        { v -> v as Double },
        { d -> d as String });

    String pattern;
    Class klass;
    def parseFun, formatFun;

    private static SimpleDateFormat dateFormat =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    // This pattern for can never match (is uses negative lookahead to
    // contradict itself).
    private static String NEVER_MATCH = /(?!x)x/;


    public PropertyHelp(String pattern, Class klass, def parseFun,
    def formatFun) {
        this.pattern = pattern
        this.klass = klass
        this.parseFun = parseFun
        this.formatFun = formatFun }

    public boolean matches(String prop) { return prop ==~ pattern }

    public boolean matches(Class klass) { return this.klass == klass }

    public static Object parse(String value) {
        def propertyType = PropertyHelp.values().find { 
            it.matches(value) }

        return propertyType ? propertyType.parseFun(value) : value }

    public static String format(def object) {
        def propertyType = PropertyHelp.values().find {
            it.klass.isInstance(object) }

        return propertyType ? propertyType.formatFun(object) : object.toString() }
}
