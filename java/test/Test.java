package test;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import util.Args;
import util.Args.Matcher;

public class Test {

  public static void main(String[] args) throws Exception {
    TestParsing.test();
    TestHelp.test();
  }

}
