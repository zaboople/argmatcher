
package test;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import util.Args;
import util.Args.Matcher;


public class TestHelp {

  public static void main(String[] args) throws Exception {
    test();
  }

  public static void test() throws Exception {
    {
      Args args=new Args();
      args.setHelp("Line 1 of general help", "Line 2 of general help");
      args.add("--allow", "-a")
        .setAllowsParam()
        .setHelp("Allows line 1", "Allows line 2");
      args.add("--req", "-r")
        .setRequiresParam();
      args.add("--multi", "-m")
        .setRequiresMultiParam()
        .setHelp("Optional but allows more than one");
      args.add("--multireq", "-mrq")
        .setRequiredAndMultiParam();
      args.add()
        .setRequired()
        .setParamSample("filename")
        .setHelp("A file to read data from.");
      compare(args, "/test/TestHelp1.txt");
    }
    {
      Args args=new Args()
        .setAllowParamDelimiterSkip()
        .setHelpProgramName("sample_program")
        .setHelp(
          "This is a sample program. It is sort of fun to play with, yet",
          "it does nothing useful at all."
        );

      Matcher<String> matchName=args.add("--name", "-names", "-n")
        .setRequiredAndMultiParam()
        .setParamSample("name")
        .setHelp("All the names of people we don't like very much.");

      Matcher<String> matchIgnoreCase=args.add("-i", "i")
        .setHelp("Ignore case as applies to file data");

      Matcher<String> matchMultiline=args.add("-m", "m")
        .setHelp("Treat all lines of file text as a single string");

      Matcher<Integer> matchCount=args.add(
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

      Matcher<File> matchFiles=args.add(s->new java.io.File(s))
        .setParamSample("filename")
        .setMultiParam()
        .setHelp("Some files to mangle into tiny bits, or none at all",
        "because it doesn't make any difference anyhow.");

      Matcher<?> matchHelp=args.add("--help", "-h")
        .setHelp("Prints this help text");

      compare(args, "/test/TestHelp2.txt");
    }
  }



  private static void compare(Args args, String compareToFile) throws Exception {

    StringBuilder
      hasText=new StringBuilder(),
      expectText=new StringBuilder();

    // Generate help & read in expected input:
    args.help(hasText);
    {
      InputStream instr=TestHelp.class.getResourceAsStream(compareToFile);
      if (instr==null) throw new IllegalArgumentException("Not found: "+compareToFile);
      char[] chars=new char[1024];
      int readLen;
      try (Reader reader = new InputStreamReader(instr, "UTF8")) {
        while ((readLen=reader.read(chars, 0, chars.length))>-1)
          expectText.append(chars, 0, readLen);
      }
    }

    // Compare the two:
    final int expectSize=expectText.length(), hasSize=hasText.length();
    final int max=Math.max(expectSize, hasSize);
    final Set<Character> badFeed=new HashSet<>(Arrays.asList('\r', '\n'));
    StringBuilder expectLine=new StringBuilder(), hasLine=new StringBuilder();
    int line=1;
    for (int i=0; i<max; i++) {
      Character
        expect=i<expectSize ?expectText.charAt(i) :null,
        has   =i<hasSize    ?hasText.charAt(i)    :null;
      if (expect==null) {
        System.out.append(hasLine).append(has);
        System.out.flush();
        throw new IllegalStateException(
          "Expected output ended at line "+(line)+": \n"
          +expectLine
        );
      }
      else
      if (has==null) {
        System.out.append(expectLine).append(expect);
        System.out.flush();
        throw new IllegalStateException(
          "Generated output ended at line "+(line)+": \n"
          +hasLine
        );
      }
      else
      if (expect!=has) {
        if (badFeed.contains(expect) || badFeed.contains(has))
          System.err.println("WARNING: Line ending issues...");
        throw new IllegalStateException(
          "Mismatch at line "+(line)+";\n"+
          "Expected: ->"+expectLine+expect+"\n"+
          "Has:      ->"+hasLine+has
        );
      }
      else
      if (has=='\n' || i==max-1) {
        System.out.append(hasLine).append(has); System.out.flush();
        expectLine.setLength(0);
        hasLine.setLength(0);
        line++;
      }
      else {
        expectLine.append(expect);
        hasLine.append(has);
      }
    }
  }

}
