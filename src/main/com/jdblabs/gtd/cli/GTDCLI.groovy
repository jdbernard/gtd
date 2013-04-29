package com.jdblabs.gtd.cli

import com.jdbernard.util.LightOptionParser
import com.martiansoftware.nailgun.NGContext
import org.joda.time.DateMidnight
import org.joda.time.DateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class GTDCLI {

    public static final String VERSION = "0.1"
    private static String EOL = System.getProperty("line.separator")

    private static GTDCLI nailgunInst

    private int terminalWidth
    private Scanner stdin
    private File workingDir
    private Logger log = LoggerFactory.getLogger(getClass())

    public static void main(String[] args) {
        GTDCLI inst = new GTDCLI(new File(System.getProperty("user.home"),
            ".gtdclirc"))

        if (args.length > 0) args[-1] = args[-1].trim()
        inst.run(args) }

    public static void nailMain(NGContext context) {
        if (nailgunInst == null)
            nailgunInst = new GTDCLI(new File(
                System.getProperty("user.home"), ".gtdclirc"))
        else nailgunInst.stdin = new Scanner(context.in)

        // trim the last argument, not all cli's are well-behaved
        if (context.args.length > 0) context.args[-1] = context.args[-1].trim()

        nailgunInst.run(context.args) }

    public static void reconfigure(String[] args) {
        if (nailgunInst == null) main(args)
        else {
            nailgunInst = null
            nailgunInst = new GTDCLI(new File(
                System.getProperty("user.home"), ".gritterrc"))

            nailgunInst.run(args) } }

    public GTDCLI(File configFile) {

        // parse the config file
        def config = [:]
        if (configFile.exists())
            config = new ConfigSlurper().parse(configFile.toURL())

        // configure the terminal width
        terminalWidth = (System.getenv().COLUMNS ?: config.terminalWidth ?: 79) as int

        workingDir = config.defaultDirectory ?
            new File(config.defaultDirectory) : 
            new File('.')
        
        stdin = new Scanner(System.in) }

    protected void run(String[] args) {
        
        def cliDefinition = [
            h: [longName: 'help'],
            d: [longName: 'directory', arguments: 1],
            v: [longName: 'version']]

        def opts = LightOptionParser.parseOptions(cliDefinition, args as List)

        if (opts.h) { printUsage(null); return }
        if (opts.v) { println "GTD CLI v$VERSION"; return }
        if (opts.d) workingDir = new File(opts.d)

        def parsedArgs = (opts.args as List) as LinkedList

        if (parsedArgs.size() < 1) printUsage()

        log.debug("argument list: {}", parsedArgs)

        while (parsedArgs.peek()) {
            def command = parsedArgs.poll()

            switch (command.toLowerCase()) {
                case ~/help/: printUsafe(parsedArgs); break
                case ~/process/: process(parsedArgs); break
                default: 
                    parsedArgs.addFirst(command)
                    process(parsedArgs)
                    break } } }

    protected void process(LinkedList args) {
        def rootDir = workingDir

        def path = args.poll()
        if (path) {
            givenDir = new File(path)
            if (givenDir.exists() && givenDir.isDirectory()) rootDir = givenDir
            else { println "'$path' is not a valid directory."; return }}

        def findGtdDir = { dirName ->
            def dir = new File(rootDir, dirName)
            if (!dir.exists() || !dir.isDirectory()) {
                println "'${rootDir.canonicalPath}' is not a valid GTD " +
                    "directory (missing the '$dirName' folder)."
                return null }
            else return dir }

        // check to see if this is the parent GTD folder, in which case it
        // should contain `in`, `incubate`, `next-actions`, `projects`,
        // `tickler`, and `waiting` folders
        def inDir, incubateDir, actionsDir, projectsDir, ticklerDir,
            waitingDir, doneDir
        
        if (!(inDir = findGtdDir("in")) ||
            !(incubateDir = findGtdDir("incubate")) ||
            !(doneDir = findGtdDir("done")) ||
            !(actionsDir = findGtdDir("next-actions")) ||
            !(projectsDir = findGtdDir("projects")) ||
            !(ticklerDir = findGtdDir("tickler")) ||
            !(waitingDir = findGtdDir("waiting")))
            return

        // Start processing items
        inDir.listFiles().collect { new Item(it) }.each { item ->

            println ""
            def response
            def readline = {stdin.nextLine().trim()}
            def prompt = { msg ->
                if (msg instanceof List) msg = msg.join(EOL)
                msg += "> "
                print msg
                def line
                
                while(!(line = readline())) print msg 
                
                return line }

            // 1. Is it actionable?
            if (!item.title) item.title = filenameToString(item.file)
            response = prompt([">> $item", "Is it actionable?"]).toLowerCase()
            
            // Not actionable
            if (!(response ==~ /yes|y/)) {
                response = prompt("Incubate or trash?").toLowerCase()

                // Trash
                if ("trash" =~ response) item.file.delete()

                // Incubate
                else {
                    println "Enter extra info. One 'key: value' pair per line."
                    println "(ex: date: YYYY-MM-DD, details)"
                    println "End with an empty line."
                    print "> "
                    while (response = readline()) {
                        if (!response =~ /[:=]/) continue
                        def parts = response.split(/[:=]/)
                        item[parts[0].trim().toLowerCase()] =
                            PropertyHelp.parse(parts[1].trim())
                        print "> " }

                    def oldFile = item.file
                    item.file = new File(incubateDir, item.file.name)
                    item.save()
                    oldFile.delete() }

            // Actionable
            } else {
                response = prompt("Will it take less than 2 minutes?").toLowerCase()

                // Do it now
                if (response ==~ /yes|y/) {
                    println "Do it now."; print "> "
                    readline();

                    def date = new DateMidnight().toString("YYYY-MM-dd")
                    def oldFile = item.file
                    item.file = new File(doneDir, "$date-${item.file.name}")
                    item.save()
                    oldFile.delete()
                    return }

                // > 2 minutes
                item.outcome = prompt("What is the desired outcome?")

                println "Enter extra info. One 'key: value' pair per line."
                println "(ex: date: YYYY-MM-DD, details)"
                println "End with an empty line."
                print "> "

                while (response = readline()) {
                    if (!(response =~ /[:=]/)) continue
                    def parts = response.split(/[:=]/)
                    item[parts[0].trim().toLowerCase()] =
                        PropertyHelp.parse(parts[1].trim())
                    print "> " }

                response = prompt("Too big for one action?").toLowerCase()

                // Needs to be a project
                if (response ==~ /yes|y/) {
                    def oldFile = item.file
                    item.file = new File(projectsDir,
                                         stringToFilename(item.outcome))
                    item.save()
                    oldFile.delete()
                    println "Moved to projects." }

                // Is a single action
                else {
                    response = prompt("Delegate, defer, or tickler?").
                        toLowerCase()

                    // Delegate
                    if (response =~ /del/) {

                        item.action = prompt([
                            "Next action (who needs to do what).", ""])

                        def oldFile = item.file
                        item.file = new File(waitingDir,
                                             stringToFilename(item.action))
                        item.save()
                        oldFile.delete()

                        println "Moved to ${waitingDir.name} folder." }


                    // Defer
                    else if (response =~ /def/) {
                        item.action = prompt(["Next action.", ""])

                        def oldFile = item.file
                        item.file = new File(actionsDir,
                                             stringToFilename(item.action))
                        item.save()
                        oldFile.delete()

                        println "Moved to the ${actionsDir.name} folder."
                    }

                    // Tickle
                    else {
                        item.action = prompt(["Next action.", ""])
                        item.tickle = prompt([
                            "When do you want it to become active?",
                            "(YYYY-MM-DD)"])

                        def oldFile = item.file
                        item.file = new File(ticklerDir,
                                             stringToFilename(item.action))
                        item.save()
                        oldFile.delete()
                        println "Moved to the ${ticklerDir.name} folder." } } } } }
    
    protected void printUsage(LinkedList args) {

        if (!args) {
            println "Jonathan Bernard's Getting Things Done CLI v$VERSION"
            println "usage: gtd [option...] <command>..."
            println ""
            println "options are:"
            println ""
            println "   -h, --help         Print this usage information."
            println "   -d, --directory    Set the GTD root directory."
            println "   -v, --version      Print the GTD CLI version."
            println ""
            println "top-leve commands:"
            println ""
            println "   process            Process inbox items systematically."
            println "   help <command>     Print detailed help about a command."
        } else {
            def command = args.poll()

            // TODO
            //switch(command.toLowerCase()) {
            //    case ~/process/:
        }
    }

    static String filenameToString(File f) {
        return f.name.replaceAll(/[-_]/, " ").capitalize() }

    static String stringToFilename(String s) {
        return s.replaceAll(/\s/, '-').
                replaceAll(/[';:]/, '').
                toLowerCase() }
}

