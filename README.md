# Argmatcher

Argmatcher is a concise, flexible and (relatively) straightforward command-line argument parser for Java programs.

It allows you to support even the most arcane & confusing of classic Unix/Linux syntax, like

    ps -aux
    ps aux
    ps -a -u -x
    grep expression file1 file2 file3
    tar -xvf -
    gawk -F~

- as well as slightly more modern, stuffy variations like

    command --very-long-thing=blah

It also provides man-page-style help documentation & generic-typed parameter conversion stuff.

Best of all, it uses no reflection, annotations, byte-code-rewriting aspect-oriented blah-blah and so forth.

Even more best of all, the whole thing fits in a single file, so you can just download it, stuff it in your own repo,
and rewrite it to work _your_ way because my way sucks and so forth:



## System requirements
Java 8 SDK/JRE
