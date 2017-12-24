System requirements
  Java 8 JRE/SDK

Parameter arguments

Parameters can be supplied like this:
  --x foo
or like this:
  --x=foo
Currently you cannot enforce one vs. the other, and the auto-generated docs don't mention the --x=foo syntax. FIXME

Multiple parameter arguments

Do I really need a pre-assembled fancy-shmancy argument parser?
In all honesty, probably not. Most command-line programs are simple affairs, and in other cases it's worth
asking first whether you are making things more complicated than they need to be. I did this mainly for
the sake of the challenge, but if it fulfills a legitimate need adequately for someone, all the better I guess.

What about environment variables and -Dblah=blah and yaml and json and having twenty different ways
to burpity blah blah blah...?
That's someone else's problem.