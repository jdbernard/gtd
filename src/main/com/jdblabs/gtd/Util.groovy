package com.jdblabs.gtd

import java.security.MessageDigest

public class Util {

    public static List<File> findAllCopies(File original, File inDir) {
        MessageDigest md5 = MessageDigest.getInstance("MD5")

        def copies = []
        def originalMD5 = md5.digest(original.bytes)

        inDir.eachFileRecurse { file -> 
            if (file.isFile() && md5.digest(file.bytes) == originalMD5)
                copies << file }
        
        return copies }

    public static boolean inPath(File parent, File child) {
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

    public static String getRelativePath(File parent, File child) {
        def parentPath = parent.canonicalPath.split("/")
        def childPath = child.canonicalPath.split("/")

        if (parentPath.length > childPath.length) return ""

        int b = 0
        while (b < parentPath.length && parentPath[b] == childPath[b] ) b++;

        if (b != parentPath.length) return ""
        return (['.'] + childPath[b..<childPath.length]).join('/') }

    public static Map findGtdRootDir(File givenDir) {

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
}
