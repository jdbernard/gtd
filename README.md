# Jonathan Bernard's Getting Things Done implementation.

This is my adaptation of the Getting Things Done system by David Allen. There
are a lot of tools that adapt his system for various digital platforms, but
most of them move away from the folder-based system he created. They try to
create new systems based on the core principles of the method outlined in
[*Getting Things Done*][book], but I was unable to find a system that followed
the details of his method. I do not think there is anything wrong with
reimagining the system based on the core principles. David Allen advocates that
in the book himself. Still, I was very attracted to the folder-based
implementation that he descibes; I only wanted to use digital folders and files
instead of physical folders and pages.

## History and Motivation

My method initially started as a simple collection of folders, intended to
mirror the physical system. As I used this I noticed some common use patterns
that would benefit from an automated tool. In particular, I wanted to have the
system walk my through the *process* phase.  It was too easy for me to forget
some of the important principles of this phase: immediately doing anything that
could be done in 5 minutes or less, identifying the next action for an item,
and sorting the action correctly.  Out of this the [command-line tool][cli] was
born.

As I started using the system for everything I started desiring to have some
way to publish my plans (or at least some contexts of my plans, like work).
This lead me to implement a [REST API][servlet] that interfaced with the
repository (still just files) so that I could easily embed this information
in a web page, or allow controlled access to the system from a client
application.

## How It Works

*TODO*

## Code Index

### com.jdblabs.gtd

[Item](jlp://gtd.jdb-labs.com/Item)
:   One item in the GTD system (a *next action* for example). This class is a
    wrapper around the File to make it easier to work programatically with GTD
    items.

[PropertyHelp](jlp://gtd.jdb-labs.com/PropertyHelp)
:   Simple serialization support for item properties. Used to read and write
    properties from an item file. 

[Util](jlp://gtd.jdb-labs.com/Util)
:   Utility methods common to this implementation of the Getting Things Done
    method.

### com.jdblabs.gtd.cli

[GTDCLI][cli]
:   Command-line interface to the GTD repository. The repository organization
    is intended to be simple enough that standard UNIX command-line tools are
    sufficient, but it is useful to add some specific commands to walk you
    through the processing phase or manage duplicated entries (when tracking an
    item in a next-actions context and a project folder, for example).

### com.jdblabs.gts.servlet

[GTDServlet][servlet]
:   Standard Java servlet to expose the repository via 

[book]: https://secure.davidco.com/store/catalog/GETTING-THINGS-DONE-PAPERBACK-p-16175.php
[cli]: jlp://gtd.jdb-labs.com/cli/GTDCLI
[servlet]: jlp://gtd.jdb-labs.com/servlet/GTDServlet
