package test;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import util.Args;
import util.Args.Matcher;


public class TestParsing {

  public static void main(String[] args) throws Exception {
    test();
  }

  public static void test() throws Exception {
    Tester tester=new Tester();
    {
      prelude("General:");
      tester.reset();
      Matcher<?>
        allow=tester.add("--allow", "-a").setAllowsParam()
          .setParamSample("aaa")
          .setHelp("Allows line 1", "Allows line 2"),
        req=tester.add("--req", "-r").setRequiresParam()
          .setParamSample("rrr"),
        multi=tester.add("--multi", "-m").setRequiresMultiParam()
          .setHelp("Optional but allows more than one"),
        multireq=tester.add("--multireq", "-mrq").setRequiredAndMultiParam();
      tester
        .matchForFail("-a", "--multi", "m1", "m2", "m3", "-r", "reqset")
          .expectFail("Missing argument: --multireq")
        .matchForFail("-a", "--multi", "m1", "m2", "m3", "-r")
          .expectFail("Argument -r requires parameter", "Missing argument: --multireq")
        .matchForFail("-a", "--multi", "-r", "reqset")
          .expectFail("Argument --multi requires parameter", "Missing argument: --multireq")
        .match(
            "--multi", "m1", "m2", "m3", "-r", "reqset", "-mrq", "mrq1"
          )
          .expectNone(allow)
          .expect(req, "-r", "reqset")
          .expectMulti(multi, "--multi", "m1", "m2", "m3")
          .expectMulti(multireq, "-mrq", "mrq1")
        .match(
            "--multi", "m1", "m2", "m3", "-r", "reqset", "-mrq", "mrq1", "-a"
          )
          .expect(allow, "-a")
          .expect(req, "-r", "reqset")
          .expectMulti(multi, "--multi", "m1", "m2", "m3")
          .expectMulti(multireq, "-mrq", "mrq1")
        .expectSynopsisLine("Synopsis: [--allow[=aaa]] [--req=<rrr>] [--multi=<value>] <--multireq=<value>>\n");
    }

    {
      prelude("Wildcard:");
      Matcher abc1=tester.reset().add().setRequiredAndMultiParam().setParamSample("thing");
      tester
        .match("a", "b", "c").expectWild(abc1, "a", "b", "c")
        .matchExpectFail("Missing argument: <thing(s)>");

      Matcher abc2=tester.reset().add().setMultiParam();
      tester
        .match().expectNone(abc2)
        .match("a", "b", "c").expectWild(abc2, "a", "b", "c");

      Matcher abc3=tester.reset().add();
      tester
        .match().expectNone(abc3)
        .matchExpectFail(
          "Invalid argument: \"b\"",
          "a", "b"
        );
    }

    {
      prelude("Multiple single-param wildcards:");
      tester.reset();
      Matcher a1=tester.add(), a2=tester.add(), a3=tester.add("-a3");
      tester
        .match().expectNone(a1, a2)
        .match("hello", "world", "-a3")
          .expectWild(a1, "hello")
          .expectWild(a2, "world")
          .expect(a3, "-a3")
        .match("hello", "-a3", "world")
          .expectWild(a1, "hello")
          .expectWild(a2, "world")
          .expect(a3, "-a3")
        .match("-a3", "hello", "world")
          .expectWild(a1, "hello")
          .expectWild(a2, "world")
          .expect(a3, "-a3")
        ;
    }

    {
      prelude("Multiple multi-param wildcards:");
      tester.reset();
      Matcher
        a1=tester.add().setMultiParam(),
        a2=tester.add().setMultiParam(),
        a3=tester.add("-a3");
      tester
        .match().expectNone(a1, a2)
        .match("hello", "-a3", "world")
          .expectWild(a1, asList("hello"))
          .expectWild(a2, asList("world"))
          .expect(a3, "-a3")
        .match("hello", "world", "-a3")
          .expectWild(a1, asList("hello", "world"))
          .expectWild(a2, asList())
          .expect(a3, "-a3")
        .match("-a3", "hello", "world")
          .expectWild(a1, asList("hello", "world"))
          .expectWild(a2, asList())
          .expect(a3, "-a3")
        ;
    }

    {
      prelude("Allows vs. Required:");
      tester.reset();
      Matcher
        allow=tester.add("--allow", "-a").setAllowsParam(),
        req=tester.add("--req", "-r").setRequired();
      tester
        .matchExpectFail("Missing argument: --req", "--allow")
        .matchExpectFail("Argument --req requires parameter", "--req")
        .match("-a", "a1", "-r", "r1")
          .expect(allow, "-a", "a1")
          .expect(req, "-r", "r1")
        .match("--allow=", "--req=r")
          .expect(allow, "--allow", null)
          .expectGet(allow, "mydefault", ()->allow.getParam("mydefault"))
        .matchExpectFail("Argument --req requires parameter", "--req=")
        ;
    }

    {
      prelude("An onlyIf() test:");
      tester.reset();
      Matcher<?>
        a1=tester.add("--a1", "-a1").setAllowsParam(),
        a2=tester.add("--a2", "-a2").onlyIf(a1);
      tester
        .expectSynopsisLine("Synopsis: [--a1[=value] [--a2]]\n")
        .match()
          .expectNone(a1)
          .expectNone(a2)
        .match("--a1")
          .expect(a1, "--a1")
          .expectNone(a2)
        .match("--a1", "-a2")
          .expect(a1, "--a1")
          .expect(a2, "-a2")
        .matchExpectFail("Argument -a2 only valid if --a1 present", "-a2");
    }

    {
      prelude("Another onlyIf() test:");
      tester.reset();
      Matcher
        z1=tester.add("--z1", "-z1"),
        z2=tester.add("--z2", "-z2").setRequired().onlyIf(z1),
        z3=tester.add("--z3", "-z3").setAllowsParam().onlyIf(z1)
        ;
      tester
        .expectSynopsisLine("Synopsis: [--z1 <--z2=<value>> [--z3[=value]]]\n")
        .match().expectNone(z1, z2, z3)
        .matchExpectFail("Missing argument: --z2", "-z1");
    }

    {
      prelude("Test defaults on getParam/getParams:");
      tester.reset();
      Matcher<String>
        z1=tester.args.add("--z1").setAllowsParam(),
        z2=tester.args.add("--z2").setMultiParam()
        ;
      tester
        .match()
          .expectGet(z1, "hello", ()->z1.getParam("hello"))
          .expectGet(
            z2,
            asList("world", "again"),
            ()->z2.getParams(asList("world", "again"))
          )
        .match("--z1", "a", "--z2", "b")
          .expectGet(z1, "a", ()->z1.getParam("hello"))
          .expectGet(
            z2,
            asList("b"),
            ()->z2.getParams(asList("world", "again"))
          )
          ;
    }

    {
      prelude("Int tests - note that negative numbers require we only use --param not -param");
      tester.reset();
      Matcher<Integer>
        a1=tester.args.add(Matcher::parseIntParam, "--a1"),
        b1=tester.args.add(Matcher::parseIntParam, "--b1").setMultiParam();
      tester
        .match("--a1", "12345", "--b1", "-222", "44")
          .expectInt(a1, 12345)
          .expectInts(b1, asList(-222, 44))
        .match("--b1")
          .expectInt(a1, null)
          .expectGet(a1, 0, ()->a1.getParam(0))
          .expectInts(b1, new ArrayList<Integer>())
          .expectGet(b1, asList(1), ()->b1.getParams(asList(1)))
        .match("--b1=1")
          .expectInt(a1, null)
          .expectGet(a1, 0, ()->a1.getParam(0))
          .expectGet(b1, asList(1), ()->b1.getParams(asList(0)))
        .match("--a1=1")
          .expectInt(a1, 1)
          .expectGet(a1, 1, ()->a1.getParam(0))
        .matchExpectFail("Argument --b1: Invalid parameter \"ll\"", "--b1", "1", "ll")
        ;
    }

    prelude("Making sure similar names don't cross each other up:");
    {
      tester.reset();
      Matcher a=tester.add("-a");
      Matcher ab=tester.add("-ab");
      Matcher abc=tester.add("-abc");
      tester
        .expectNone(a, ab, abc)
        .match("-abc")
        .expectNone(a, ab).expect(abc, "-abc")
        ;
    }
    {
      tester.reset();
      Matcher a2=tester.add("-a").setMultiParam();
      Matcher ab2=tester.add("-ab").setAllowsParam();
      Matcher abc2=tester.add("-abc").setAllowsParam();
      tester
        .match("-abc=3", "-ab=2", "-a=1", "-a=11")
        .expectMulti(a2, "-a", "1", "11")
        .expect(ab2, "-ab", "2")
        .expect(abc2, "-abc", "3")
        ;
    }

    prelude("Mashing arguments together:");
    {
      tester.reset();
      Matcher
        a=tester.add("-a"),
        b=tester.add("-b"),
        c=tester.add("-c"),
        d=tester.add("-d").setAllowsParam()
        ;
      tester
        .match("-abc")
          .expect(a, "-a")
          .expect(b, "-b")
          .expect(c, "-c")
        .match("-cabd", "phlegm")
          .expect(a, "-a")
          .expect(b, "-b")
          .expect(c, "-c")
          .expect(d, "-d", "phlegm")
        .matchExpectFail("Invalid argument: \"-abce\"", "-abce")
        ;
    }

    prelude("Mashing arguments together without dashes:");
    {
      tester.reset();
      Matcher
        a=tester.add("-a", "a"),
        b=tester.add("-u", "u"),
        c=tester.add("-x", "x"),
        d=tester.add("-d", "d").setAllowsParam()
        ;
      tester
        .match("aux")
          .expect(a, "a")
          .expect(b, "u")
          .expect(c, "x")
        .match("uxad", "phlegm")
          .expect(a, "a")
          .expect(b, "u")
          .expect(c, "x")
          .expect(d, "d", "phlegm")
        .matchExpectFail("Invalid argument: \"auxe\"", "auxe")
        ;
    }

    prelude("Verify: \"-a xxx -a=yyy -a zzz\" gives the same result as \"-a xxx yyy zzz\"");
    {
      tester.reset();
      Matcher
        a=tester.add("-a").setMultiParam(),
        b=tester.add("-b").setAllowsParam(),
        c=tester.add("-c")
      ;
      tester
        .match("-a", "1",  "-a", "2",  "-a", "3")
          .expectMulti(a, "-a", "1", "2", "3")
        .match("-a=1",  "-a=2",  "-a=3")
          .expectMulti(a, "-a", "1", "2", "3")
        .matchForFail("-b", "1",  "-b", "2",  "-b", "3")
          .expectFail(
            "-b: Cannot supply multiple values; caused by: 2",
            "-b: Cannot supply multiple values; caused by: 3"
          )
        .matchForFail("-b=1",  "-b=2",  "-b=3")
          .expectFail(
            "-b: Cannot supply multiple values; caused by: 2",
            "-b: Cannot supply multiple values; caused by: 3"
          )
      ;
    }

    prelude("Verify -abc~ is same as -a -b -c ~");
    {
      tester.reset()
        .args.setAllowParamDelimiterSkip();
      Matcher
        a=tester.add("-a").setMultiParam(),
        b=tester.add("-b").setAllowsParam(),
        c=tester.add("-c").setAllowsParam()
        ;
      tester.match("-abc~")
        .expect(a, "-a")
        .expect(b, "-b")
        .expect(c, "-c", "~")
        ;
    }

    prelude("Verify -abc~ is same as -a bc~ when -a requires param");
    {
      tester.reset()
        .args.setAllowParamDelimiterSkip();
      Matcher
        a=tester.add("-a").setRequiresParam(),
        b=tester.add("-b").setAllowsParam(),
        c=tester.add("-c").setAllowsParam()
        ;
      tester.match("-abc~")
        .expect(a, "-a", "bc~")
        .expectNone(b)
        .expectNone(c)
        ;
    }

    prelude("Verify setErrors()");
    {
      tester.reset()
        .args.setAllowParamDelimiterSkip();
      Matcher
        a=tester.add("-a").setRequiresParam(),
        b=tester.add("-b").setAllowsParam(),
        c=tester.add("-c").setAllowsParam()
        ;
      tester.match("-abc~")
        .expect(a, "-a", "bc~")
        .expectNone(b)
        .expectNone(c)
        .addError(b, "not good")
        .expectFail("not good")
      ;
      if (!b.failed()) throw new RuntimeException();
    }

  }


  ///////////////////////
  //                   //
  // TESTING UTILTIES: //
  //                   //
  ///////////////////////

  private static void prelude(String description) {
    System.out.append("\n");
    for (int i=0; i<description.length()+10; i++)
      System.out.append("_");
    System.out
      .append("\n**** ")
      .append(description)
      .append(" ****\n");
  }

  private static class Tester {
    Args args=new Args();
    public Tester reset() {
      this.args=new Args();
      return this;
    }
    public Matcher<?> add() {
      return args.add();
    }
    public Matcher<?> add(String... names) {
      return args.add(names);
    }
    public Tester addError(Matcher<?> m, String s) {
      args.addError(m, s);
      return this;
    }
    public Tester matchExpectFail(String expectError, String... s) {
      matchForFail(s);
      expectFail(expectError);
      return this;
    }
    public Tester matchForFail(String... s) {
      clear();
      args.match(s);
      return this;
    }
    public Tester match(String... s) {
      clear();
      args.match(s);
      if (args.hasUserErrors())
        throw new IllegalStateException(args.getUserErrors().toString());
      return this;
    }
    public Tester expectSynopsisLine(String expected) {
      StringBuilder sb=new StringBuilder();
      args.helpSynopsisLine(sb);
      String actual=sb.toString();
      if (!actual.equals(expected))
        throw new IllegalStateException(
          "Usage mismatch: \n"+
          "Expected: \""+expected+"\"\n"+
          "Got:      \""+actual+"\"\n"
        );
      System.out.println("Success: "+actual);
      return this;
    }
    public Tester expectFail(String... expect) {
      return expectFail(asList(expect));
    }
    public Tester expectFail(List<String> expect) {
      List<String> got=args.getUserErrors();
      if (neq(expect, got))
        throw new IllegalStateException("Mismatched user errors:\n"+
          "Expected: ->"+expect+"\n"+
          "Got:      ->"+got
        );
      System.out.println("Success: -> "+got);
      return this;
    }
    public Tester expectGet(Matcher<?> matcher, Object expect, Supplier<?> has) {
      Object got=has.get();
      if (neq(got, expect))
        throw new IllegalStateException(matcher+" -> Got: "+got+" != Expect: "+expect);
      System.out.println("Success: "+matcher+" -> "+got);
      return this;
    }
    public Tester expectMulti(Matcher matcher, String name, String... args) {
      return expect(matcher, name, null, args);
    }
    public Tester expectWild(Matcher matcher, String param) {
      return expect(matcher, null, param);
    }
    public Tester expectWild(Matcher matcher, String... params) {
      return expect(matcher, params.length>0, null, null, params);
    }
    public Tester expectWild(Matcher matcher, List<String> params) {
      return expect(matcher, params.size()>0, null, null, params);
    }
    public Tester expectNone(Matcher<?>... ms) {
      for (Matcher<?> m: ms)
        expect(m, false, null, null);
      return this;
    }
    public Tester expectInt(Matcher<Integer> matcher, Integer expect) {
      return expectGet(matcher, expect, ()->matcher.getParam());
    }
    public Tester expectInts(Matcher<Integer> matcher, List<Integer> expect) {
      return expectGet(matcher, expect, ()->matcher.getParams());
    }
    public Tester expect(Matcher<?> matcher, String name) {
      return expect(matcher, name, null);
    }
    public Tester expect(Matcher<?> matcher, String name, String param) {
      return expect(matcher, true, name, param);
    }
    public Tester expect(Matcher<?> matcher, String name, String param, String... params) {
      return expect(matcher, true, name, param, params);
    }
    public Tester expect(Matcher<?> am, boolean found, String name, String param, String... params) {
      return expect(am, found, name, param, asList(params));
    }
    public Tester expect(Matcher<?> am, boolean found, String name, String param, List<String> params) {
      if (param==null && params!=null && params.size()>0) param=params.get(0);
      List<String> errors=null;
      errors=neq(errors, "Found", am.found(), found);
      errors=neq(errors, "Name", am.getName(), name);
      errors=neq(errors, "Param", am.getParam(), param);
      List<?> gotParams=am.getParams();
      if (gotParams!=null || (params!=null && params.size()>0))
        errors=neq(errors, "Params", gotParams, params);
      if (errors!=null)
        throw new IllegalStateException("Mismatches for "+am+" -> "+errors.stream().collect(Collectors.joining(", ")));
      System.out.println("Success: "+am);
      return this;
    }

    private List<String> neq(List<String> errors, String field, Object a, Object b) {
      if (neq(a,b)) {
        if (errors==null) errors=new ArrayList<>();
        errors.add(field+": Got "+a+" != Expected "+b);
      }
      return errors;
    }
    private boolean neq(Object a, Object b) {
      if (a!=null) return !a.equals(b);
      return b!=null;
    }
    private List<Object> toList(Object[] a) {
      return asList(Optional.ofNullable(a).orElse(new Object[]{}));
    }
    private void clear() {
      args.clear();
      System.out.println();
    }
  }

  @SafeVarargs
  private static <T> List<T> asList(T... args) {
    return Arrays.asList(args);
  }

}
