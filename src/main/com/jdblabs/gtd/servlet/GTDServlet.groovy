package com.jdblabs.gtd.servlet

import com.jdblabs.gtd.Item
import com.jdblabs.gtd.PropertyHelp
import com.jdblabs.gtd.Util
import com.jdbernard.util.SmartConfig
import groovy.json.JsonBuilder
import groovy.json.JsonException
import groovy.json.JsonSlurper
import java.util.regex.Matcher
import javax.servlet.ServletConfig
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

import static javax.servlet.http.HttpServletResponse.*

public class GTDServlet extends HttpServlet {

    protected Map gtdDirs
    private SmartConfig config

    private class TempRequestData {
        public String username
        public def defaultPermissions
    }

    void init(ServletConfig config) {
        String gtdDirName = config.getInitParameter("gtdRootDir")
        this.gtdDirs = Util.findGtdRootDir(new File(gtdDirName))
        if (!gtdDirs) throw new ServletException(
            "Unable to initialize GTD servlet: no GTD root dir found in the " +
            "configured path (${gtdDirName}).")
            
        def cfgFile = new File(gtdDirs.root, '.properties')
        if (!cfgFile.isFile() || !cfgFile.exists()) throw new ServletException(
            "Unable to find the GTD/.properties configuration file. " +
            "Expected to find it at '${cfgFile.canonicalPath}'.")

        this.config = new SmartConfig(cfgFile) }

    void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader("Access-Control-Allow-Origin",
            request.getHeader("Origin") ?: "*")
        response.addHeader("Access-Control-Allow-Credentials", "true")
        response.status = SC_OK
        
        switch (request.servletPath) {
            case '/login':
                response.addHeader("Allow", "POST")
                response.addHeader("Access-Control-Allow-Methods", "POST")
                break
            case ~'/contexts.*':
            case ~'/projects.*':
            case ~'/next-actions/.+':
                response.addHeader("Allow", "GET")
                response.addHeader("Access-Control-Allow-Methods", "GET")
                break
            default:
                response.status = SC_NOT_FOUND }
    }

    void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader("Content-Type", "application/json")
        response.addHeader("Access-Control-Allow-Origin",
            request.getHeader("Origin") ?: "*")
        response.addHeader("Access-Control-Allow-Credentials", "true")

        HttpSession session = request.getSession(true);

        // If the user is posting to /gtd/login then let's try to authenticate
        // them.
        if (request.servletPath == '/login') {
            // Parse the username/password from the request.
            def requestBody
            try { requestBody = new JsonSlurper().parse(request.reader) }
            catch (JsonException jsone) {
                response.status = SC_BAD_REQUEST
                return }

            // Build our list of known users.
            def users = config.accountNames.split(/,/).collect { it.trim() }

            // Lookup the user's password in the configuration (will be null if
            // we are given an invalid username).
            String expectedPwd = config."account.${requestBody.username}.password"

            // Reject the login request if the user is not defined by our
            // configuration. Note: timing attack possible due to string
            // comparison.
            if (!users.contains(requestBody.username) ||
                 requestBody.password != expectedPwd) {
                response.status = SC_UNAUTHORIZED
                response.writer.flush()
                return }

            response.status = SC_OK
            session.setAttribute('authenticated', true)
            session.setAttribute('username', requestBody.username)
            writeJSON([status: "ok"], response)
            return }

        // If the user is not authenticated return a 401 Unauthorized.
        else if (!((boolean)session.getAttribute('authenticated'))) {
            response.status = SC_UNAUTHORIZED
            return }

        // Right now there is no other endpoint that supports POST, so return
        // 404 Not Found or 405 Method Not Allowed
        switch (request.servletPath) {
            case ~/\/gtd\/contexts.*/: 
            case ~/\/gtd\/projects.*/: 
                response.status = SC_METHOD_NOT_ALLOWED
                return
            default:
                response.status = SC_NOT_FOUND
                return
        }
    }

    void doGet(HttpServletRequest request, HttpServletResponse response) {

        response.status = SC_OK
        response.addHeader("Content-Type", "application/json")
        response.addHeader("Access-Control-Allow-Origin",
            request.getHeader("Origin") ?: "*")
        response.addHeader("Access-Control-Allow-Credentials", "true")

        HttpSession session = request.getSession(true);

        // If the user is not authenticated return 401 Unauthorized.
        if (!((boolean)session.getAttribute('authenticated'))) {
            response.status = SC_UNAUTHORIZED
            return }

        def curData = new TempRequestData()

        // Read the username from the session object.
        curData.username = session.getAttribute('username')

        // Determine the user's default permissions.
        curData.defaultPermissions =
            (config."account.${curData.username}.defaultPermissions" ?: "")
            .split(/,/).collect { it.trim() }

        switch(request.servletPath) {

            // If they are invoking /gtd/logout then invalidate their session
            // and return 200 OK
            case "/logout":
                session.removeAttribute("authenticated")
                session.invalidate()
                break

            // If they are GET'ing /gtd/contexts then return the list of
            // contexts that are readable by this user.
            case "/contexts":

                // Filter the directories to find the ones that the user has
                // read access to.
                def selectedContexts = findAllowedDirs("read", curData,
                    gtdDirs['next-actions'].listFiles())

                def returnData = selectedContexts.collect { entry ->
                    [id: entry.dir.name, title: entry.props.title] }

                // Now format our response as JSON and write it to the response
                writeJSON(returnData, response)
                break

            // If they are GET'ing /gtd/contexts/<contextId> then return data
            // for the requested context, assuming it is readable for this
            // user.
            case ~'/contexts/(.+)':
                String contextId = Matcher.lastMatcher[0][1]

                // Try to find the named context.
                File ctxDir = new File(gtdDirs['next-actions'], contextId)

                // Check that they have read permission on this directory.
                def filteredList = findAllowedDirs("read", curData, [ctxDir])
                if (filteredList.size() == 0) {
                    response.status = SC_NOT_FOUND
                    writeJSON([status: "not found"], response)
                    break }

                def entry = filteredList[0]
                def returnData = [id: entry.dir.name, title: entry.props.title]
                writeJSON(returnData, response)
                break

            // If they are GET'ing /gtd/projects then return the list of
            // projects that are readable for this user.
            case "/projects":
                // Filter the directories to find the ones that the user has
                // read access to.
                def selectedProjects = findAllowedDirs("read", curData,
                    gtdDirs['projects'].listFiles())

                def returnData = selectedProjects.collect { entry ->
                    [id: entry.dir.name, title: entry.props.title] }
                writeJSON(returnData, response)
                break

            // If they are GET'ing /gtd/projects/<projectId> then return the
            // list of projects that are readable for this user.
            case ~'/projects/(.+)':
                String projectId = Matcher.lastMatcher[0][1]

                // Try to find the named project.
                File projectDir = new File(gtdDirs['projects'], contextId)

                // Check that they have read permission on this directory.
                def filteredList = findAllowedDirs("read", curData, [projectDir])
                if (filteredList.size() == 0) {
                    response.status = SC_NOT_FOUND
                    writeJSON([status: "not found"], response)
                    break }

                def entry = filteredList[0]
                def returnData = [id: entry.dir.name, title: entry.props.title]
                writeJSON(returnData, response)
                break

            case ~'/next-actions/(.+)':
                // Parse out the list of contexts/projects
                List ids = Matcher.lastMatcher[0][1].split(/,/) as List

                List searchDirs = []

                // Look for each id in our list of contexts
                searchDirs.addAll(ids.collect { id ->
                    new File(gtdDirs['next-actions'], id) })

                // And look for each id in our list of projects
                searchDirs.addAll(ids.collect { id ->
                    new File(gtdDirs['projects'], id) })

                // Filter the directories to find the ones that exist and are
                // readable by our user.
                def actualDirs = findAllowedDirs("read", curData, searchDirs)

                // Collect all the items.
                def items = [], itemFiles = [], uniqueItemFiles = []

                // Collect all the items across all the actual directories.
                itemFiles = actualDirs.collectMany { entry ->
                    entry.dir.listFiles({ f -> !f.isHidden() } as FileFilter) as List }

                // De-duplicate the items using the GTD findAllCopies utility
                // method to remove items that are listed in a chosen context
                // and project. We are going to do this by identifying
                // duplicate items, removing all of them from the itemFiles
                // list and adding only the first to our new uniqueItemFiles
                // list.
                while (itemFiles.size() > 0) {
                    def item = itemFiles.remove(0)

                    // Find all duplicates.
                    def dupes = Util.findAllCopies(item, gtdDirs.root)

                    // Remove them from the source collection.
                    itemFiles.removeAll { f1 -> dupes.any { f2 ->
                        f1.canonicalPath == f2.canonicalPath }}

                    // Add the first one to the destination collection.
                    uniqueItemFiles << item }

                // Create Item objects for each item.
                items = uniqueItemFiles.collect { new Item(it) }

                // Return all the items.
                def returnData = items.collect { item ->
                    def m = [id: item.file.name]
                    item.gtdProperties.each { k, v ->
                        m[k] = PropertyHelp.format(v) }
                    return m }

                writeJSON(returnData, response)
                break

            // Otherwise return a 404 Not Found
            default:
                response.status = SC_NOT_FOUND
                break
        }

        response.writer.flush()
    }

    protected Collection findAllowedDirs(String permission,
    TempRequestData curData, def dirs) {
        return findAllowedDirs([permission], curData, dirs) }

    protected Collection findAllowedDirs(List requiredPermissions,
    TempRequestData curData, def dirs) {
        return dirs.collectMany { dir ->

            println ">> Considering ${dir.canonicalPath}"

            // Only directories can be contexts and projects.
            if (!dir.exists() || !dir.isDirectory()) { return [] }

            // Check for a .properties file in this directory.
            def propFile = new File(dir, '.properties')

            // If it does not exist, defer to the defaults.
            if (!propFile.exists() &&
                !curData.defaultPermissions.containsAll(requiredPermissions)) {
                return [] }
            
            // Look for the account.<curData.username>.Permissions property.
            def itemProps = new SmartConfig(propFile)
            def actualPermissions = itemProps.getProperty(
                "account.${curData.username}.permissions", "default").
                split(/,/).collect { it.trim() }

            // If the user has the correct permission on this context, or
            // if this context inherits their default permissions, and
            // they have the correct permission by default, then we show
            // this context. If this is not the case (tested in the
            // following conditional) we do not show this context.
            if (!actualPermissions.containsAll(requiredPermissions) &&
                !(actualPermissions.containsAll('default') &&
                  curData.defaultPermissions.containsAll(requiredPermissions))) {
                return [] }

            // At this point we know the context exists, and the user
            // has permission to read it.
            return [[ dir: dir, props: itemProps ]] } }

    protected void writeJSON(def data, def response) {
        new JsonBuilder(data).writeTo(response.writer) }
}
