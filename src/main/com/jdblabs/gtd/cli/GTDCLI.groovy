package com.jdblabs.gtd.cli

import com.jdbernard.util.LightOptionParser
import com.martiansoftware.nailgun.NGContext
import java.security.MessageDigest
import org.joda.time.DateMidnight
import org.joda.time.DateTime
//import org.slf4j.Logger
//import org.slf4j.LoggerFactory

public class GTDCLI {

    public static final String VERSION = "0.8"
    private static String EOL = System.getProperty("line.separator")
    private static GTDCLI nailgunInst

    private MessageDigest md5 = MessageDigest.getInstance("MD5")
    private int terminalWidth
    private Scanner stdin
    private File workingDir
    private Map<String, File> gtdDirs
    //private Logger log = LoggerFactory.getLogger(getClass())

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

        gtdDirs = findGtdRootDir(workingDir)
        if (!gtdDirs) {
            println "fatal: '${workingDir.canonicalPath}'"
            println "       is not a GTD repository (or any of the parent directories)."
            return }

        while (parsedArgs.peek()) {
            def command = parsedArgs.poll()

            switch (command.toLowerCase()) {
                case ~/help/: printUsage(parsedArgs); break
                case ~/done/: done(parsedArgs); break
                case ~/cal|calendar/: calendar(parsedArgs); break
                case ~/process/: process(parsedArgs); break
                case ~/list-copies/: listCopies(parsedArgs); break
                case ~/new/: newAction(parsedArgs); break
                default: 
                    println "Unrecognized command: ${command}"
                    break } } }

    protected void process(LinkedList args) {

        def path = args.poll()
        if (path) {
            def givenDir = new File(path)
            if (!(gtdDirs = findGtdRootDir(givenDir))) {
                println "'$path' is not a valid directory."; return }}

        // Start processing items
        gtdDirs.in.listFiles().collect { new Item(it) }.each { item ->

            println ""
            def response
            def readline = {stdin.nextLine().trim()}

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
                    item.file = new File(gtdDirs.incubate, item.file.name)
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
                    item.file = new File(gtdDirs.done, "$date-${item.file.name}")
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
                    item.file = new File(gtdDirs.projects,
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
                        item.file = new File(gtdDirs.waiting,
                                             stringToFilename(item.action))
                        item.save()
                        oldFile.delete()

                        println "Moved to ${gtdDirs.waiting.name} folder." }


                    // Defer
                    else if (response =~ /def/) {
                        item.action = prompt(["Next action.", ""])

                        def oldFile = item.file
                        item.file = new File(gtdDirs["next-actions"],
                                             stringToFilename(item.action))
                        item.save()
                        oldFile.delete()

                        println "Moved to the ${gtdDirs['next-actions'].name} folder."
                    }

                    // Tickle
                    else {
                        item.action = prompt(["Next action.", ""])
                        item.tickle = prompt([
                            "When do you want it to become active?",
                            "(YYYY-MM-DD)"])

                        def oldFile = item.file
                        item.file = new File(gtdDirs.tickler,
                                             stringToFilename(item.action))
                        item.save()
                        oldFile.delete()
                        println "Moved to the ${gtdDirs.tickler.name} folder." } } } } }

    protected void done(LinkedList args) {

        def selectedFilePath = args.poll()
        def selectedFile = new File(selectedFilePath)

        if (!selectedFile) {
            println "gtd done command requires a <action-file> parameter."
            return }

        def item
        if (selectedFile.isAbsolute()) item = new Item(selectedFile)
        else item = new Item(new File(workingDir, selectedFilePath))

        def itemMd5 = md5.digest(item.file.bytes)

        // Move to the done folder.
        def oldFile = item.file
        def date = new DateMidnight().toString("YYYY-MM-dd")
        item.file = new File(gtdDirs.done, "$date-${item.file.name}")
        item.save()

        // Check if this item was in a project folder.
        if (inPath(gtdDirs.projects, oldFile)) {

            // Delete any copies of this item in the next actions folder.
            findAllCopies(oldFile, gtdDirs."next-actions").each { file ->
                println "Deleting duplicate entry from the " +
                        "${file.parentFile.name} context."
                file.delete() }

            // Delete any copies of this item in the waiting folder.
            findAllCopies(oldFile, gtdDirs.waiting).each { file ->
                println "Deleting duplicate entry from the " +
                    "${file.parentFile.name} waiting context."
                file.delete() }}

        // Check if this item was in the next-action or waiting folder.
        if (inPath(gtdDirs["next-actions"], oldFile) ||
            inPath(gtdDirs.waiting, oldFile)) {

            // Delete any copies of this item in the projects folder.
            findAllCopies(oldFile, gtdDirs.projects).each { file ->
                println "Deleting duplicate entry from the " +
                    "${file.parentFile.name} project."
                file.delete() }}

        // Delete the original
        oldFile.delete()

        println "'$item' marked as done." }
    
    protected void calendar(LinkedList args) {
        def itemsOnCalendar = []

        def addCalendarItems = { file ->
            if (!file.isFile()) return
            def item = new Item(file)
            if (item.date) itemsOnCalendar << item }

        gtdDirs."next-actions".eachFileRecurse(addCalendarItems)
        gtdDirs.waiting.eachFileRecurse(addCalendarItems)
        gtdDirs.projects.eachFileRecurse(addCalendarItems)

        itemsOnCalendar = itemsOnCalendar.unique { md5.digest(it.file.bytes) }.
                                          sort { it.date }

        if (!itemsOnCalendar) println "No items on the calendar."

        def currentDate = null
            
        itemsOnCalendar.each { item ->
            def itemDay = new DateMidnight(item.date)
            if (itemDay != currentDate) {
                if (currentDate != null) println ""
                println itemDay.toString("EEE, MM/dd")
                println "----------"
                currentDate = itemDay }

            println "  $item" } }

    protected void listCopies(LinkedList args) {

        args.each { filePath ->
            def file = new File(filePath)

            if (!file.isAbsolute()) file = new File(workingDir, filePath)

            if (!file.isFile()) {
                println "${file.canonicalPath} is not a regular file."
                return }

            String originalRelativePath = getRelativePath(gtdDirs.root, file)
            println "Copies of $originalRelativePath:"
            println ""

            findAllCopies(file, gtdDirs.root).each { copy ->
                if (copy.canonicalPath != file.canonicalPath) {
                    String relativePath = getRelativePath(gtdDirs.root, copy)
                    println "  $relativePath" }} }

        args.clear() }

    protected void newAction(LinkedList args) {

        def response = prompt(["Next action?", ""])
        def file = new File(workingDir, stringToFilename(response))
        file.createNewFile()
        def item = new Item(file)
        
        item.action = response

        println "Enter extra info. One 'key: value' pair per line."
        println "(ex: date: YYYY-MM-DD, project=my-project)"
        println "End with an empty line."
        print "> "

        while (response = stdin.nextLine().trim()) {
            if (!(response =~ /[:=]/)) continue
            def parts = response.split(/[:=]/)
            item[parts[0].trim().toLowerCase()] =
                PropertyHelp.parse(parts[1].trim())
            print "> " }

        item.save() }

    protected void printUsage(LinkedList args) {

        if (!args) {
            println """\
Jonathan Bernard's Getting Things Done CLI v$VERSION
usage: gtd [option...] <command>...

options are:

   -h, --help                  Print this usage information.
   -d, --directory             Set the GTD root directory.
   -v, --version               Print the GTD CLI version.
                              
top-level commands:           
                              
   help <command>              Print detailed help about a command.
   process                     Process inbox items systematically.
   done <action-file>          Mark an action as done. This will automatically
                               take care of duplicates of the action in project 
                               or next-actions sub-folders.
   calendar                    Show the tasks with specific days assigned to
                               them, sorted by date.
   list-copies <action-file>   Given an action item, list all the other places
                               there the same item is filed (cross-reference
                               with a project folder, for example).
   new                         Interactively create a new action item in the
                               current folder."""
        } else {
            def command = args.poll()

            switch(command.toLowerCase()) {
                case ~/process/: println """\
usage: gtd process

This is an interactive command.

GTD CLI goes through all the items in the "in" folder for this GTD repository
and guides you through the *process* step of the GTD method as follows:

                Is the item actionable?
                           V
                           +---------------------------> No
                           |                           /   \\
                          Yes                 Incubate       Trash
                           |              (Someday/Maybe)
                           V
         Yes <--Too big for one action? --> No
          |                                 |
          V                                 |
  Move to projects                          V
(still needs organization)        What is the next action?
                                          / 
                                         / 
                          Defer, delegate, or tickler?
                          /         |              \\
                         /     Move to the       Set a date for this
              Move to the        waiting       to become active again.
              next-actions      directory        Move to the tickler
              directory                              directory."""
                    break

                case ~/done/: println """\
usage: gtd done <action-file>

Where <action-file> is expected to be the path (absolute or relative) to an
action item file. The action item file is expected to be in the *projects*
folder, the *next-actions* folder, the *waiting* folder, or a subfolder of one of
the aforementioned folders. The item is prepended with the current date and
moved to the *done* folder. If the item was in a project folder, the
*next-actions* and *waiting* folders are scanned recursively for duplicates of
the item, which are removed if found. Similarly, if the action was in a
*next-actions* or *waiting* folder the *projects* folder is scanned recursively
for duplicates.

The intention of the duplicate removal is to allow you to copy actions from
project folders into next action or waiting contexts, so you can keep a view of
the item organized by the project or in your next actions list. The GTD CLI tool
is smart enough to recognize that these are the same items filed in more than
one place and deal with them all in one fell swoop. Duplicates are determined by
exact file contents (MD5 has of the file contents)."""
                    break

                case ~/calendar/: println """\
usage: gtd calendar

Print an agenda of all the actions that are on the calendar, sorted by date.
This prints a date heading first, then all of the actions assogned to that day.
Remember that in the GTD calendar items are supposed to be hard dates, IE.
things that *must* be done on the assigned date."""
                    break

                case ~/list-copies/: println """\
usage: gtd list-copies <action-file>

Where <action-file> is expected to be the path (absolute or relative) to an
action item file.

This command searched through the current GTD repository for any items that are
duplicates of this item."""
                    break

                case ~/new/: println """\
usage: gtd new

This command is interactive (maybe allow it to take interactive prompts in the
future?). It prompts the user for the next action and any extended properties
that should be associated with it, then creates the action file in the current
directory."""
                    break
            }
        }
    }

    protected List<File> findAllCopies(File original, File inDir) {
        def copies = []
        def originalMD5 = md5.digest(original.bytes)

        inDir.eachFileRecurse { file -> 
            if (file.isFile() && md5.digest(file.bytes) == originalMD5)
                copies << file }
        
        return copies }

    protected boolean inPath(File parent, File child) {
        def parentPath = parent.canonicalPath.split("/")
        def childPath = child.canonicalPath.split("/")

        // If the parent path is longer than the child path it cannot contain
        // the child path.
        if (parentPath.length > childPath.length) return false;

        // If the parent and child paths do not match at any point, the parent
        // path does not contain the child path.
        for (int i = 0; i < parentPath.length; i++)
            if (childPath[i] != parentPath[i])
                return false;

        // The parent path is at least as long as the child path, and the child
        // path matches the parent path (up until the end of the parent path).
        // The child path either is the parent path or is contained by the
        // parent path.
        return true }

    protected String getRelativePath(File parent, File child) {
        def parentPath = parent.canonicalPath.split("/")
        def childPath = child.canonicalPath.split("/")

        if (parentPath.length > childPath.length) return ""

        int b = 0
        while (b < parentPath.length && parentPath[b] == childPath[b] ) b++;

        if (b != parentPath.length) return ""
        return (['.'] + childPath[b..<childPath.length]).join('/') }

    protected Map findGtdRootDir(File givenDir) {

        def gtdDirs = [:]

        File currentDir = givenDir
        while (currentDir != null) {
            gtdDirs = ["in", "incubate", "done", "next-actions", "projects",
                       "tickler", "waiting"].
                collectEntries { [it, new File(currentDir, it)] }

            if (gtdDirs.values().every { dir -> dir.exists() && dir.isDirectory() }) {
                gtdDirs.root = currentDir
                return gtdDirs }

            currentDir = currentDir.parentFile }

        return [:] }

    protected String prompt(def msg) {
        if (msg instanceof List) msg = msg.join(EOL)
        msg += "> "
        print msg
        def line
        
        while(!(line = stdin.nextLine().trim())) print msg 
        
        return line }

    static String filenameToString(File f) {
        return f.name.replaceAll(/[-_]/, " ").capitalize() }

    static String stringToFilename(String s) {
        return s.replaceAll(/\s/, '-').
                replaceAll(/[';:(\.$)]/, '').
                toLowerCase() }

}

