# Argmatcher

Argmatcher is a concise, flexible and (relatively) straightforward command-line argument parser for Java programs.

It allows you to support even the most arcane & confusing of classic Unix/Linux syntax, like:

    ps -aux
    ps aux
    ps -a -u -x
    grep expression file1 file2 file3
    tar -xvf -
    gawk -F~ '{print $2}'

\- as well as slightly more modern, stuffy variations like:

    command --very-long-thing=blah

Argmatcher also provides man-page-style help documentation & generic-typed parameter conversion stuff.

Best of all, it uses no reflection, annotations, byte-code-rewriting-aspect-oriented-blah-blah and so forth.

Even more best of all, the whole thing fits in a single file, so you can just
[download it](./java/prod/util/Args.java), stuff it in your own repo,
and rewrite it to work _your_ way because my way sucks and so forth (plus
I'm too lazy to publish jar files to all the maven repos)

# Documentation:
[Here is a nice example implementation](./java/test/Sample.java)

[Online javadocs](https://zaboople.github.io/javadoc/argmatcher/)

# Download:
[Download jar & javadocs](https://zaboople.github.io/downloads/argmatcher-1.0.zip)

# System requirements:

Java 8 SDK/JRE
