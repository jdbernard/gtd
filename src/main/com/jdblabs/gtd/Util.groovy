/** 
 * # Util
 * @author Jonathan Bernard (jdb@jdb-labs.com)
 * @copyright 2013 [JDB Labs LLC](http://jdb-labs.com)
 */
package com.jdblabs.gtd

import java.security.MessageDigest

/**
 * Utility methods common to this implementation of the Getting Things Done
 * method. These methods provide support for working with a GTD repository
 * which follows the organization and convention described 
 * [here](jlp://gtd.jdb-labs.com/notes/organization-and-structure)
 * @org gtd.jdb-labs.com/Util
 */
public class Util {

    /** #### `findAllCopies`
      * Given a GTD item file, find all files in the repository which are exact
      * copies of this file (including thie file itself). This is useful when
      * the same item exists in a project folder and in a next-action context
      * folder.
      *
      * @org gtd.jdb-labs.com/Util/findAllCopies */
    public static List<File> findAllCopies(File original, File inDir) {
        MessageDigest md5 = MessageDigest.getInstance("MD5")

        def copies = []
        def originalMD5 = md5.digest(original.bytes)

        inDir.eachFileRecurse { file -> 
            if (file.isFile() && md5.digest(file.bytes) == originalMD5)
                copies << file }
        
        return copies }

    /** #### `inPath`
      * Determine whether or not a given path is a subpath of a given parent
      * path. This algorithm does not consider symlinks or hard links. It
      * operates based on the textual path names. */
    public static boolean inPath(File parent, File child) {
        def parentPath = parent.canonicalPath.split("[\\\\/]")
        def childPath = child.canonicalPath.split("[\\\\/]")

        /// If the parent path is longer than the child path it cannot contain
        /// the child path.
        if (parentPath.length > childPath.length) return false;

        /// If the parent and child paths do not match at any point, the parent
        /// path does not contain the child path.
        for (int i = 0; i < parentPath.length; i++)
            if (childPath[i] != parentPath[i])
                return false;

        /// The parent path is at least as long as the child path, and the child
        /// path matches the parent path (up until the end of the parent path).
        /// The child path either is the parent path or is contained by the
        /// parent path.
        return true }

    /** #### `getRelativePath`
      * Given a parent path and a child path, assuming the child path is
      * contained within the parent path, return the relative path from the
      * parent to the child. */
    public static String getRelativePath(File parent, File child) {
        def parentPath = parent.canonicalPath.split("[\\\\/]")
        def childPath = child.canonicalPath.split("[\\\\/]")

        /// If the parent path is longer it cannot contain the child path and
        /// we cannot construct a relative path without backtracking.
        if (parentPath.length > childPath.length) return ""

        /// Compare the parent and child path up until the end of the parent
        /// path.
        int b = 0
        while (b < parentPath.length && parentPath[b] == childPath[b] ) b++;

        /// If we stopped before reaching the end of the parent path it must be
        /// that the paths do not match. The parent cannot contain the child and
        /// we cannot build a relative path without backtracking.
        if (b != parentPath.length) return ""
        return (['.'] + childPath[b..<childPath.length]).join('/') }

    /** #### `findGtdRootDir`
      * Starting from a give directory, walk upwards through the file system
      * heirarchy looking for the GTD root directory. The use case that
      * motivates this function is when you are currently down in the GTD
      * folder structure, in a context or project subfolder for example, and
      * you need to find the root directory of the GTD structure, somewhere
      * above you.
      *
      * This function returns a GTD Root Directory map, which has keys
      * representing each of the top-level GTD directories, including a `root`
      * key which corresponds to the parent file of these top-level GTD
      * directories. The values are the File objects representing the
      * directories. For example, if the GTD root is at `/home/user/gtd` then
      * the root map (call it `m`) will have `m.projects ==
      * File('/home/user/gtd/projects')`, `m.root == File('/home/user/gtd')`,
      * etc.
      * @org gtd.jdb-labs.com/notes/root-directory-map */
    public static Map findGtdRootDir(File givenDir) {

        def gtdDirs = [:]

        /// Start by considering the current directory as a candidate.
        File currentDir = givenDir
        while (currentDir != null) {
            /// We recognize the GTD root directory when it contains all of the
            /// GTD top-level directories.
            gtdDirs = ["in", "incubate", "done", "next-actions", "projects",
                       "tickler", "waiting"].
                collectEntries { [it, new File(currentDir, it)] }

            if (gtdDirs.values().every { dir -> dir.exists() && dir.isDirectory() }) {
                gtdDirs.root = currentDir
                return gtdDirs }

            /// If this was not the GTD root, let's try the parent.
            currentDir = currentDir.parentFile }

        /// If we never found the GTD root, we return an empty map.
        return [:] }
}
