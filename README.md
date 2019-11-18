TinyJukebox
========

TinyJukebox is a Web server that allows to queue up music to be played.

How to use
====
First launch
-----
Open a terminal inside the root of the project and type `./gradlew shadowJar` if on MacOSX/Linux, `gradlew.bat shadowJar` on Windows.

The executable jar is generated inside `build/libs/` as `tinyjukebox-<version>.jar`

Following launches
----
From the root of the project, type `java -jar build/libs/tinyjukebox-<version>-all.jar` to run TinyJukebox

Creating a admin account
----
From the root of the project, type `java -cp build/libs/tinyjukebox-<version>-all.jar org.jglrxavpok.tinyjukebox.CLIKt newadmin`
The program will then prompt you to type a username and a password for the account.
Informations will be saved inside `auth/<username>`. The password will be stored as a SHA-256 crypted hexadecimal string inside this file.

Configuration
----
TODO