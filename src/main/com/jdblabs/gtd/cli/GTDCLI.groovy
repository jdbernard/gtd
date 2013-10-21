/** 
 * # GTDCLI
 * @author Jonathan Bernard (jdb@jdb-labs.com)
 * @copyright 2013 [JDB Labs LLC](http://jdb-labs.com)
 */
package com.jdblabs.gtd.cli

import com.jdblabs.gtd.Item
import com.jdblabs.gtd.PropertyHelp
import com.jdbernard.util.LightOptionParser
import com.martiansoftware.nailgun.NGContext
import java.security.MessageDigest
import org.joda.time.DateMidnight
import org.joda.time.DateTime
//import org.slf4j.Logger
//import org.slf4j.LoggerFactory

import static com.jdblabs.gtd.Util.*

/**
 * Command-line helper for working with this implementation of the Getting
 * Things Done method.
 * @org gtd.jdb-labs.com/cli/GTDCLI */
public class GTDCLI {

    public static final String VERSION = "1.5"
    private static String EOL = System.getProperty("line.separator")

    /// We have a persistent instance when we are in the context of a Nailgun
    /// setup.
    private static GTDCLI nailgunInst

    /// Used to wrap lines intelligently.
    private int terminalWidth

    private Scanner stdin
    private File workingDir

    /// The [GTD Root Directory map][root-map] for our repository.
    ///
    /// [root-map]: jlp://gtd.jdb-labs.com/notes/root-directory-map
    private Map<String, File> gtdDirs

    //private Logger log = LoggerFactory.getLogger(getClass())

    /** #### `main`
      * Main entry point for a normal GTD CLI process. */
    public static void main(String[] args) {
        /// Instantiate our GTDCLI instance using the configuration file at
        /// `$HOME/.gtdclirc`.
        GTDCLI inst = new GTDCLI(new File(System.getProperty("user.home"),
            ".gtdclirc"))

        /// Actual processing is done by the
        /// [`run`](jlp://gtd.jdb-labs.com/cli/GTDCLI/run) method
        if (args.length > 0) args[-1] = args[-1].trim()
        inst.run(args) }

    /** #### `nailMain`
      * Entry point for a GTD CLI process under [Nailgun][ng].
      * [ng]: http://www.martiansoftware.com/nailgun/ */
    public static void nailMain(NGContext context) {
        if (nailgunInst == null)
            nailgunInst = new GTDCLI(new File(
                System.getProperty("user.home"), ".gtdclirc"))
        else nailgunInst.stdin = new Scanner(context.in)

        /// Trim the last argument; not all cli's are well-behaved
        if (context.args.length > 0) context.args[-1] = context.args[-1].trim()

        nailgunInst.run(context.args) }

    /** #### `reconfigure`
      * This method reloads the configuration before invoking the run function,
      * allowing a long-lived instance to react to configuration changes. */
    public static void reconfigure(String[] args) {
        /// If we do not have a long-running Nailgun instance we just call
        /// main.
        if (nailgunInst == null) main(args)
        else {
            /// Discard our old instance and instantiate a new one in order to
            /// read afresh the configuration file.
            nailgunInst = null
            nailgunInst = new GTDCLI(new File(
                System.getProperty("user.home"), ".gritterrc"))

            nailgunInst.run(args) } }

    /** #### `constructor`
      * Create a new GTDCLI instance, using the given configuration file. */
    public GTDCLI(File configFile) {

        /// Parse the groovy config file
        def config = [:]
        if (configFile.exists())
            config = new ConfigSlurper().parse(configFile.toURL())

        /// Configure the terminal width
        terminalWidth = (System.getenv().COLUMNS ?: config.terminalWidth ?: 79) as int

        /// Configure our default working directory.
        workingDir = config.defaultDirectory ?
            new File(config.defaultDirectory) : 
            new File('.')
        
        stdin = new Scanner(System.in) }

    /** #### `run`
      * This method does the work of processing the user input and taking the
      * appropriate action.
      * @org gtd.jdb-labs.com/cli/GTDCLI/run */
    protected void run(String[] args) {
        
        /// Simple CLI options:
        def cliDefinition = [
            /// -h, --help
            /// :   Show the usage information.
            h: [longName: 'help'],
            /// -d, --directory
            /// :   Set the working directory for the CLI.
            d: [longName: 'directory', arguments: 1],
            /// -v, --version
            /// :   Print version information.
            v: [longName: 'version']]

        def opts = LightOptionParser.parseOptions(cliDefinition, args as List)

        if (opts.h) { printUsage(null); return }
        if (opts.v) { println "GTD CLI v$VERSION"; return }
        if (opts.d) workingDir = new File(opts.d)

        /// View the arguments as a [`LinkedList`][1] so we can use [`peek`][2]
        /// and [`poll`][3].
        ///
        /// [1]: http://docs.oracle.com/javase/6/docs/api/java/util/LinkedList.html
        /// [2]: http://docs.oracle.com/javase/6/docs/api/java/util/LinkedList.html#peek()
        /// [3]: http://docs.oracle.com/javase/6/docs/api/java/util/LinkedList.html#poll()
        def parsedArgs = (opts.args as List) as LinkedList

        if (parsedArgs.size() < 1) printUsage()

        /// Make sure we are in a GTD directory.
        gtdDirs = findGtdRootDir(workingDir)
        if (!gtdDirs) {
            println "fatal: '${workingDir.canonicalPath}'"
            println "       is not a GTD repository (or any of the parent directories)."
            return }

        while (parsedArgs.peek()) {
            /// Pull off the first argument.
            def command = parsedArgs.poll()

            /// Match the first argument and invoke the proper command method.
            switch (command.toLowerCase()) {
                case ~/help/: printUsage(parsedArgs); break
                case ~/done/: done(parsedArgs); break
                case ~/cal|calendar/: calendar(parsedArgs); break
                case ~/process/: process(parsedArgs); break
                case ~/list-copies/: listCopies(parsedArgs); break
                case ~/new/: newAction(parsedArgs); break
                case ~/tickler/: tickler(parsedArgs); break
                case ~/ls|list/: ls(parsedArgs); break;
                default: 
                    println "Unrecognized command: ${command}"
                    break } } }

    /** #### `process`
      * Implement the *process* step of the GTD method. For details, see the
      * [online help][help-process] included by running `gtd help process`
      *
      * [help-process]: jlp://gtd.jdb-labs.com/cli/GTDCLI/help/process
      */
    protected void process(LinkedList args) {

        def path = args.poll()
        if (path) {
            def givenDir = new File(path)
            if (!(gtdDirs = findGtdRootDir(givenDir))) {
                println "'$path' is not a valid directory."; return }}

        /// Start processing items
        gtdDirs.in.listFiles().collect { new Item(it) }.each { item ->

            println ""
            def response
            def readline = {stdin.nextLine().trim()}
            def oldFile = item.file

            /// 1. Is it actionable?
            if (!item.title) item.title = filenameToString(item.file)
            response = prompt([">> $item", "Is it actionable?"]).toLowerCase()
            
            /// Not actionable, should we incubate this or trash it?
            if (!(response ==~ /yes|y/)) {
                response = prompt("Incubate or trash?").toLowerCase()

                /// Trash
                if ("trash" =~ response) item.file.delete()

                /// Incubate
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

                    item.file = new File(gtdDirs.incubate, item.file.name)
                    item.save()
                    oldFile.delete() }

            /// It is actionable. Can we do it now in less than 2 minutes?
            } else {
                response = prompt("Will it take less than 2 minutes?").toLowerCase()

                /// Yes, so do it now.
                if (response ==~ /yes|y/) {
                    println "Do it now."; print "> "
                    readline();

                    def date = new DateMidnight().toString("YYYY-MM-dd")
                    item.file = new File(gtdDirs.done, "$date-${item.file.name}")
                    item.save()
                    oldFile.delete()
                    return }

                /// It will take more than 2 minutes. Track it in our system.
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

                /// Does this need to be a separate project?
                response = prompt("Too big for one action?").toLowerCase()

                /// Yes, this deserves it's own project folder.
                if (response ==~ /yes|y/) {
                    item.file = new File(gtdDirs.projects,
                                         stringToFilename(item.outcome))
                    item.save()
                    oldFile.delete()
                    println "Moved to projects." }

                /// No, we can track this in one item. Is this something we
                /// need someone else to do, should we defer it to our
                /// next-actions list, or should we forget about it until a
                /// future date?
                else {
                    response = prompt("Delegate, defer, or tickler?").
                        toLowerCase()

                    /// Delegate, move to the *waiting* folder.
                    if (response =~ /del/) {

                        item.action = prompt([
                            "Next action (who needs to do what).", ""])

                        item.file = new File(promptContext(gtdDirs.waiting),
                                             stringToFilename(item.action)) }


                    /// Defer, move to the *next-actions* folder.
                    else if (response =~ /def/) {
                        item.action = prompt(["Next action.", ""])

                        item.file = new File(promptContext(gtdDirs["next-actions"]),
                                             stringToFilename(item.action)) }

                    /// Forget for now, move it to the *tickler* folder.
                    else {
                        item.action = prompt(["Next action.", ""])
                        item.tickle = prompt([
                            "When do you want it to become active?",
                            "(YYYY-MM-DD)"])

                        item.file = new File(gtdDirs.tickler,
                                             stringToFilename(item.action)) }
                        
                    item.save()
                    oldFile.delete()

                    println "Moved to " +
                        getRelativePath(gtdDirs.root, item.file.parentFile)
                        
                    /// If we have a project property, and a corresponding
                    /// project folder exists, copy the item there.
                    def projectDir = new File(gtdDirs.projects,
                                              item.project ?: '')
                    if (item.project && projectDir.exists() &&
                        projectDir.isDirectory()) {
                        item.file = new File(projectDir,
                                             stringToFilename(item.action))
                        item.save()
                        println "Copied to " +
                            getRelativePath(gtdDirs.root, item.file.parentFile) } } } } }

    /** #### `done`
      * Implement the `done` command to mark items as completed. For detailed
      * information see the [online help][help-done] by running 
      * `gtd help done`.
      *
      * [help-done]: jlp://gtd.jdb-labs.com/cli/GTDCLI/help/done
      */
    protected void done(LinkedList args) {

        def selectedFilePath = args.poll()
        def selectedFile = new File(selectedFilePath)

        if (!selectedFile) {
            println "gtd done command requires a <action-file> parameter."
            return }

        def item
        if (selectedFile.isAbsolute()) item = new Item(selectedFile)
        else item = new Item(new File(workingDir, selectedFilePath))

        /// Move to the done folder.
        def oldFile = item.file
        def date = new DateMidnight().toString("YYYY-MM-dd")
        item.file = new File(gtdDirs.done, "$date-${item.file.name}")
        item.save()

        /// Check if this item was in a project folder.
        if (inPath(gtdDirs.projects, oldFile)) {

            /// Delete any copies of this item from the next actions folder.
            findAllCopies(oldFile, gtdDirs."next-actions").each { file ->
                println "Deleting duplicate entry from the " +
                        "${file.parentFile.name} context."
                file.delete() }

            /// Delete any copies of this item from the waiting folder.
            findAllCopies(oldFile, gtdDirs.waiting).each { file ->
                println "Deleting duplicate entry from the " +
                    "${file.parentFile.name} waiting context."
                file.delete() }}

        /// Check if this item was in the next-action or waiting folder.
        if (inPath(gtdDirs["next-actions"], oldFile) ||
            inPath(gtdDirs.waiting, oldFile)) {

            /// Delete any copies of this item from the projects folder.
            findAllCopies(oldFile, gtdDirs.projects).each { file ->
                println "Deleting duplicate entry from the " +
                    "${file.parentFile.name} project."
                file.delete() }}

        /// Delete the original
        oldFile.delete()

        println "'$item' marked as done." }
    
    /** #### `calendar`
      * Implement the `calendar` command to show all the items which are
      * scheduled on the calendar. For detailed information see the
      * [online help][help-calendar] by running `gtd help calendar`.
      *
      * [help-calendar]: jlp://gtd.jdb-labs.com/cli/GTDCLI/help/calendar
      */
    protected void calendar(LinkedList args) {
        def itemsOnCalendar = []

        MessageDigest md5 = MessageDigest.getInstance("MD5")

        /// Temporary helper function to add GTD item files that have the
        /// `date` property defined.
        def addCalendarItems = { file ->
            if (!file.isFile()) return
            def item = new Item(file)
            if (item.date) itemsOnCalendar << item }

        /// Look through each of the `next-actions`, `waiting`, and `projects`
        /// folders for items which should be on the calendar
        gtdDirs."next-actions".eachFileRecurse(addCalendarItems)
        gtdDirs.waiting.eachFileRecurse(addCalendarItems)
        gtdDirs.projects.eachFileRecurse(addCalendarItems)

        /// De-duplicate the list.
        itemsOnCalendar = itemsOnCalendar.unique { md5.digest(it.file.bytes) }.
                                          sort { it.date }

        if (!itemsOnCalendar) println "No items on the calendar."

        def currentDate = null
            
        /// Print each day of items.
        itemsOnCalendar.each { item ->
            def itemDay = new DateMidnight(item.date)
            if (itemDay != currentDate) {
                if (currentDate != null) println ""
                println itemDay.toString("EEE, MM/dd")
                println "----------"
                currentDate = itemDay }

            println "  $item" } }

    /** #### `listCopies`
      * Implement the `list-copies` command to show all the copies of a given
      * item in the repository. For detailed information see the
      * [online help][help-list-copies] by running `gtd help list-copies`.
      *
      * [help-list-copies]: jlp://gtd.jdb-labs.com/cli/GTDCLI/help/list-copies
      */
    protected void listCopies(LinkedList args) {

        args.each { filePath ->
            /// First find the file they have named.
            def file = new File(filePath)

            if (!file.isAbsolute()) file = new File(workingDir, filePath)

            if (!file.isFile()) {
                println "${file.canonicalPath} is not a regular file."
                return }

            String originalRelativePath = getRelativePath(gtdDirs.root, file)
            println "Copies of $originalRelativePath:"
            println ""

            /// Find all copies using [`Util.findAllCopies`][1] and print their
            /// relative paths.
            /// [1]: jlp://gtd.jdb-labs.com/Util/findAllCopies
            findAllCopies(file, gtdDirs.root).each { copy ->
                if (copy.canonicalPath != file.canonicalPath) {
                    String relativePath = getRelativePath(gtdDirs.root, copy)
                    println "  $relativePath" }} }

        args.clear() }

    /** #### `new`
      * Implement the `new` command to create a new GTD item in the current
      * directory. For detailed information see the [online help][help-new] by
      * running `gtd help new`.
      *
      * [help-new]: jlp://gtd.jdb-labs.com/cli/GTDCLI/help/new
      */
    protected void newAction(LinkedList args) {

        /// Get the next action.
        def response = prompt(["Next action?", ""])
        def file = new File(workingDir, stringToFilename(response))
        file.createNewFile()
        def item = new Item(file)
        
        item.action = response

        println "Enter extra info. One 'key: value' pair per line."
        println "(ex: date: YYYY-MM-DD, project=my-project)"
        println "End with an empty line."
        print "> "

        /// Read in item properties.
        while (response = stdin.nextLine().trim()) {
            /// Skip lines that do not contain either `:` or `=` (the key-value
            /// delimiters).
            if (!(response =~ /[:=]/)) continue

            /// Split the line into key and value and add this property to the
            /// item.
            def parts = response.split(/[:=]/)
            item[parts[0].trim().toLowerCase()] =
                PropertyHelp.parse(parts[1].trim())
            print "> " }

        item.save()
        
        /// If we have a project property, and a corresponding project folder
        /// exists, copy the item there.
        def projectDir = new File(gtdDirs.projects, item.project ?: '')
        if (item.project && projectDir.exists() && projectDir.isDirectory()) {
            item.file = new File(projectDir, stringToFilename(item.action))
            item.save()
            println "Copied to " +
                getRelativePath(gtdDirs.root, item.file.parentFile) } }

    /** #### `tickler`
      * Implement the `tickler` command to move items in the *tickler* folder to
      * the *next-actions* folder if their time has come. For detailed
      * information see the [online help][help-tickler] by running
      * `gtd help tickler`.
      *
      * [help-tickler]: jlp://gtd.jdb-labs.com/cli/GTDCLI/help/tickler
      */
    protected void tickler(LinkedList args) {

        gtdDirs.tickler.eachFileRecurse { file ->
            def item = new Item(file)
            def today = new DateMidnight()

            /// If the item is scheduled to be tickled today (or in the past)
            /// then move it into the next-actions folder
            if ((item.tickle as DateMidnight) <= today) {
                println "Moving '${item.action}' out of the tickler."
                def oldFile = item.file
                item.file = new File(gtdDirs."next-actions",
                                     stringToFilename(item.action))
                item.gtdProperties.remove("tickle")
                item.save()
                oldFile.delete() }}}

    /** #### `ls`
      * Implement the `ls` command to pretty print all items in a context
      * folder, a project folder, or the *next-action* folder. For detailed
      * information see the [online help][help-ls] by running
      * `gtd help ls`.
      *
      * [help-ls]: jlp://gtd.jdb-labs.com/cli/GTDCLI/help/ls
      */
    protected void ls(LinkedList args) {

        def target = args.poll()

        /// Temporary helper function to print all the items in a given
        /// directory.
        def printItems = { dir ->
            if (!dir.exists() || !dir.isDirectory()) return
            println "-- ${getRelativePath(gtdDirs.root, dir)} --"
            dir.eachFile { file ->
                if (!file.exists() || !file.isFile() || file.isHidden() ||
                    file.name.startsWith('.'))
                    return

                def item = new Item(file)
                println item.action }

            println "" }

        /// If we have a named context or project, look for those items
        /// specifically
        if (target) {

            printItems(new File(gtdDirs['next-actions'], target))
            printItems(new File(gtdDirs.waiting, target))
            printItems(new File(gtdDirs.projects, target)) }

        /// Otherwise print all items in the *next-actions* and *waiting*
        /// folders and all their subfolders.
        else {
            printItems(gtdDirs['next-actions'])
            printItems(gtdDirs['waiting'])
            gtdDirs['next-actions'].eachDir(printItems)
            gtdDirs['waiting'].eachDir(printItems) } }

    /** #### `help`
      * Implement the `help` command which provides the online-help. Users can
      * access the online help for a command by running `gtd help <command>`.*/
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
                               current folder.
   tickler                     Search the tickler file for items that need to be
                               delivered and move them to the *next-actions*
                               folder."""
        } else {
            def command = args.poll()

            switch(command.toLowerCase()) {
                /// Online help for the `process` command.
                /// @org gtd.jdb-labs.com/cli/GTDCLI/help/process
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

                /// Online help for the `done` command.
                /// @org gtd.jdb-labs.com/cli/GTDCLI/help/done
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
exact file contents (MD5 hash of the file contents)."""
                    break

                /// Online help for the `calendar` command.
                /// @org gtd.jdb-labs.com/cli/GTDCLI/help/calendar
                case ~/calendar/: println """\
usage: gtd calendar

Print an agenda of all the actions that are on the calendar, sorted by date.
This prints a date heading first, then all of the actions assogned to that day.
Remember that in the GTD calendar items are supposed to be hard dates, IE.
things that *must* be done on the assigned date."""
                    break

                /// Online help for the `list-copies` command.
                /// @org gtd.jdb-labs.com/cli/GTDCLI/help/list-copies
                case ~/list-copies/: println """\
usage: gtd list-copies <action-file>

Where <action-file> is expected to be the path (absolute or relative) to an
action item file.

This command searched through the current GTD repository for any items that are
duplicates of this item."""
                    break

                /// Online help for the `new` command.
                /// @org gtd.jdb-labs.com/cli/GTDCLI/help/new
                case ~/new/: println """\
usage: gtd new

This command is interactive (maybe allow it to take interactive prompts in the
future?). It prompts the user for the next action and any extended properties
that should be associated with it, then creates the action file in the current
directory."""
                    break

                /// Online help for the `tickler` command.
                /// @org gtd.jdb-labs.com/cli/GTDCLI/help/tickler
                case ~/tickler/: println """\
usage: gtd tickler

This command should be scheduled for execution once a day. It checks the tickler
file for any items that should become active (based on their <tickle> property)
and moves them out of the tickler file and into the next-actions file."""
                    break

                /// Online help for the `ls`/`list-context` command.
                /// @org gtd.jdb-labs.com/cli/GTDCLI/help/ls
                case ~/ls|list-context/: println """\
usage gtd ls [<context> ...]

This command lists all the tasks for a given context or project. The purpose is
to list in one place items that are sitting in the next-actions folder or the
waiting folder for a specific context or list items for a given project. If no
context or project is named, all contexts are listed."""
            }
        }
    }

    /** #### `prompt`
      * Prompt the user for an answer to a question. This is a helper to loop
      * until the user has entered an actual response.  */
    protected String prompt(def msg) {
        if (msg instanceof List) msg = msg.join(EOL)
        msg += "> "
        print msg
        def line
        
        while(!(line = stdin.nextLine().trim())) print msg 
        
        return line }

    /** #### `promptContext`
      * Prompt the user to choose a context (subdirectory of the given
      * directory). */
    protected File promptContext(File baseDir) {
        print "Context?> "
        def line
        def contextFile

        line = stdin.nextLine().trim()
        contextFile = line ? new File(baseDir, line) : baseDir

        while (!contextFile.exists() || !contextFile.isDirectory()) {
            println "Available contexts:"
            baseDir.eachDir { print "\t${it.name}"}
            println ""

            print "Context?> "
            line = stdin.nextLine().trim()
            contextFile = line ? new File(baseDir, line) : baseDir }
        
        return contextFile }

    /** #### `filenameToString`
      * The default pretty-print conversion for filenames. */
    public static String filenameToString(File f) {
        return f.name.replaceAll(/[-_]/, " ").capitalize() }

    /** #### `stringToFilename`
      * Helper method to convert a user-entered string into something more
      * palatable for a filename. */
    public static String stringToFilename(String s) {
        return s.replaceAll(/\s/, '-').
                replaceAll(/[';:(\.$)]/, '').
                toLowerCase() }
}

