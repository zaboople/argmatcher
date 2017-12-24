package test;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import util.Args;
import util.Args.Matcher;

public class Sample {
  public static void main(String[] args) throws Exception {

    // 1. Setup:
    Args parser=new Args()
      .setAllowParamDelimiterSkip()
      .setHelpProgramName("sample_program")
      .setHelp(
        "This is a sample program. It is sort of fun to play with, yet",
        "it does nothing useful at all."
      );

    Matcher<String> matchName=parser.add("--name", "-names", "-n")
      .setRequiredAndMultiParam()
      .setParamSample("name")
      .setHelp("All the names of people we don't like very much.");

    Matcher<?> matchIgnoreCase=parser.add("-i", "i")
      .setHelp("Ignore case as applies to file data");

    Matcher<?> matchMultiline=parser.add("-m", "m")
      .setHelp("Treat all lines of file text as a single string");

    Matcher<Integer> matchCount=parser.add(
        (string) -> {
          int i=Matcher.parseIntParam(string);
          if (i<1)
            throw new IllegalArgumentException(i+" is less than 1.");
          return i;
        },
        "--count", "-count"
      )
      .setRequiresParam()
      .setParamSample("number")
      .setHelp(
        "The number of times we want to yell at each person we don't like"
      );

    Matcher<File> matchFiles=parser.add(s->new java.io.File(s))
      .setParamSample("filename")
      .setMultiParam()
      .setHelp("Some files to mangle into tiny bits, or none at all",
      "because it doesn't make any difference anyhow.");

    Matcher<?> matchHelp=parser.add("--help", "-h")
      .setHelp("Prints this help text");

    // 2. Note how we check matchHelp before checking for errors, because
    //    required arguments will cause errors when missing. Of course many
    //    applications don't have required arguments.
    parser.match(args);
    if (matchHelp.found()) {
      parser.help(System.out);
      return;
    }

    // 3. Check validation. It's up to us if we want to automatically print help
    //    or prompt the user to go do it themselves.
    if (parser.hasUserErrors()) {
      System.out.println("User error (invoke with --help for usage instructions): ");
      parser.getUserErrors(System.out);
      System.exit(1);
      return;
    }

    // 4. Finally obtain all the data:
    List<String> names=matchName.getParams();
    int count=matchCount.getParam(0);
    List<File> files=matchFiles.getParams();
    boolean ignoreCase=matchIgnoreCase.found();
    boolean multiline=matchMultiline.found();
    System.out.println("Names: "+names);
    System.out.println("Count: "+count);
    System.out.println("Files: "+files);
    System.out.println("Ignore case: "+ignoreCase);
    System.out.println("Multi-line: "+multiline);

  }
}
