package util;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * A single-file (as in, just one java file) utility for parsing &amp; validating command-line arguments,
 * as well as printing detailed help and error messages for users.
 *
 * <p>
 * About our terminology:
 * <ul>
 *   <li> When we say "argument" and "parameter", in the case of "--arg=param" or "-arg param", the
 *        "argument" is "--arg" and the argument's parameter is "param".
 *   <li> When we say "unnamed wildcard", in the case of "grep phrase filename",
 *        both "phrase" and "filename" are unnamed wildcard parameters; they don't need
 *        a -expr and -files in front of them. The argument is implied but not explicit.
 * </ul>
 *
 * <p>
 * A nigh-ridiculous variety of syntax for argument parameters is supported:
 * <ul>
 *   <li>As noted above, unnamed parameters e.g. "grep 'hello world' file1 file2 file3",
 *   <li>"--arg" as well as "-arg"
 *   <li>"--arg=param" and "-arg param" (... as well as the non-idiomatic "--arg param", "-arg=param",
 *       because hey we know what you meant...)
 *   <li>"-abc param" as a shortcut to "-a -b -c param"
 *   <li>...better yet, dashes can be optional, e.g. the classic "ps aux" as a shortcut
 *       to "ps -aux" as a shortcut to "ps -a -u -x"
 *   <li>"-x1" as a shortcut to "-x 1" and even "-abc1" as a shortcut to "-a -b -c 1"
 *       (must be enabled via <code>setAllowParamDelimiterSkip()</code>)
 *   <li>For multi-parameter arguments, "-a xxx -a yyy -a zzz" gives the same result as "-a xxx yyy zzz".
 *       In the case of the popular double-dashed-equals idiom, "--aa=xxx --aa=yyy --aa=zzz" can be used.
 *   <li>And of course arguments and their parameters can be optional or required.
 * </ul>
 * In short, you should be able to implement the most incorrigibly obfuscated, occasionally ambiguous
 * yet concise syntax imaginable, just like your favorite Unix/Linux commands (or at least nearly so)... or
 * you can be the fussy type who insists strictly on --lots-of-words=duh etc. just because.
 *
 * <p>
 * Validated, generic-typed arguments (such as dates, numbers, etc.) are supported, although
 * you still have to do some of the necessary dirty work yourself.
 *
 * <p>
 * Man-page-style documentation output is available. It is impossible to please everyone all
 * of the time when it comes to such, so you may wish to reimplement per your own desires. Args uses
 * a typical pseudo-BNF syntax where angle brackets (&lt; &gt;) mean "required" and square brackets ([ ])
 * mean "optional" (support for the common TTY-oriented underscore-means-required idiom is doable but we
 * never bothered).
 *
 * <p>
 * We tend to err on behalf of convenience in spite of (perhaps to spite) common dogma, so:
 * <ul>
 *   <li>Many Args/Matcher.setXXXX() methods return the object itself, but this is not meant to imply these classes
 * are immutable; it is only to allow chained configuration calls in a single statement.
 *   <li>Boolean versions of such methods don't take a boolean argument because "true" is the only useful variation.
 * </ul>
 */

public class Args {

  /**
   * ThrowIO & quietly() reduce the hassle of unlikely IOExceptions from Appendable.append().
   */
  @FunctionalInterface
  private static interface ThrowIO {
    public void run() throws IOException;
  }
  private static void quietly(ThrowIO r) {
    try {
      r.run();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /////////////////////////
  //                     //
  // INSTANCE VARIABLES: //
  //                     //
  /////////////////////////

  // Matcher info:
  private List<Matcher<?>> matchers=new ArrayList<>();
  private boolean allowParamDelimiterSkip=false;
  private Map<Character,Matcher<?>> charToMatcher=new HashMap<>();
  private Set<String> allNames=new HashSet<>();
  private String paramNoDashes=null;
  private List<String> errors;

  // Help things:
  private String helpIndent="  ";
  private String helpProgramName=null;
  private List<String> helpIntro=null;

  ////////////////////////////
  //                        //
  // CONFIGURATION METHODS: //
  //                        //
  ////////////////////////////

  /** Creates a new instance of Args, ready for configuration. */
  public Args() {}

  /**
   * <p>
   * Creates a Matcher for a "named" argument. While it is simplest to give one name,
   * additional aliases can be given for convenience.
   * <p>
   * Names should include or exclude dashes according to what the user is expected to type; e.g.
     <pre>
        Args.add("--ignore-case", "-i")
        Args.add("--multi-line", "-m")</pre>
   * The above example gives two naming options per argument; in fact, the
   * second in each allows a user to enter "-im" as a shortcut. We
   * can even add a <em>third</em> name:
     <pre>
        Args.add("--ignore-case", "-i", "i")
        Args.add("--multi-line", "-m", "m")</pre>
   * This gives the additional shortcut of "im", i.e. no dashes required.
   *
   * @return The new Matcher, which can be further configured via its own methods.
   */
  public Matcher<String> add(String... args) {
    return add(new Matcher<String>(Function.identity(), args));
  }

  /**
   * <p>
   * An enhanced version of <code>add(String...)</code> allowing validation and conversion
   * of parameters from String to other types.
   * <p>
   * For invalid inputs, <code>converter</code> should throw an IllegalArgumentException with a description
   * of what's wrong with the input. The presence of converter also causes Matcher.setAllowsParam()
   * to be automatically invoked. For parameter-less arguments and simple
   * parameters where no conversion or validation is necessary, use Args.add(String...) instead.
   * <p>
   * Note that since a converter is provided, <code>Matcher.setAllowsParam()</code> is automatically
   * invoked against the resulting Matcher.
   */
  public <T> Matcher<T> add(Function<String, T> converter, String... names) {
    return add(new Matcher<T>(converter, names).setAllowsParam());
  }

  /**
   * <p>
   * Creates a "wildcard" Matcher for unnamed parameters; the classic use case is "grep expr file1 file2 fileN", which lets
   * you provide a search expression as well as files to search through without something like "-expr" and "-files"
   * preceding them. One would use two separate wildcard Matchers for such:
     <pre>
      Args args=new Args();
      Matcher<String> regex=args.add();
      Matcher<String> files=args.add().setMultiParam();
      ...</pre>
   * @return The new Matcher, which can be further configured via its own methods.
   */
  public Matcher<String> add() {
    return add(Function.identity());
  }

  /**
   * <p>
   * An enhanced version of <code>add()</code> allowing validation and conversion of unnamed
   * parameters from String to other types.
   * <p>
   * The converter follows the same rules described for <code>Args.add(converter, names)</code>.
   */
  public <T> Matcher<T> add(Function<String, T> converter) {
    return add(new Matcher<>(converter));
  }

  private <T> Matcher<T> add(Matcher<T> matcher) {
    if (matcher.names!=null)
      for (String name: matcher.names) {

        // 1. No duplicates:
        if (allNames.contains(name))
          throw new IllegalArgumentException("Duplicate argument name: \""+name+"\"");
        allNames.add(name);

        // 2. Populate charToMatcher for shorcutting -a -b -c as -abc (and abc)
        int nameLen=name.length();
        if (nameLen<=2)
          charToMatcher.put(name.charAt(nameLen-1), matcher);

        // 3. Dumb paramNoDashes thing:
        boolean doubleDash=name.startsWith("--");
        boolean singleDash=!doubleDash && name.startsWith("-");
        if (paramNoDashes==null && doubleDash)
          paramNoDashes="--";
        else
        if ((paramNoDashes==null || "--".equals(paramNoDashes)) && singleDash)
          paramNoDashes="-";

      }
    matchers.add(matcher);
    return matcher;
  }

  /**
   * Sets the detailed, introductory help information for the program. This will be printed
   * between the "Synopsis" and "Options" sections.
   *
   * @param helpIntro You are responsible for splitting the text up into lines; there is no
   *   auto-wrap feature.
   */
  public Args setHelp(String... helpIntro) {
    return setHelp(Arrays.asList(helpIntro));
  }

  /** Same as setHelp(String...) but using List<String> instead.*/
  public Args setHelp(List<String> helpIntro) {
    this.helpIntro=helpIntro;
    return this;
  }

  /**
   * <p>
   * Sets the name of the program printed in the "Synopsis" section of help, e.g.
   * <br>
   * "Synopsis: myprogram [-x <value>] [-y] [-z]".
   * <p>
   * If no name is given, none will be printed, e.g.:
   * <br>
   * "Synopsis: [-x <value>] [-y] [-z]".
   */
  public Args setHelpProgramName(String helpProgramName) {
    this.helpProgramName=helpProgramName;
    return this;
  }

  /**
   * Allows syntax like "-F~" as a shortcut to "-F ~". Only applies
   * to single-dash-single-character arguments (e.g. "--count1010" won't work).
   * Turned off by default because this might cause confusion in some cases, although
   * every effort is made to guess the user's intentions correctly.
   */
  public Args setAllowParamDelimiterSkip() {
    allowParamDelimiterSkip=true;
    return this;
  }

  /**
   * Just a way to add arbitrary user errors (such as special validation cases
   * beyond the scope of Args itself) while keeping errors centralized.
   *
   * @param matcher Null is permitted. Only sets the failed flag on the given matcher.
   */
  public Args addError(Matcher<?> matcher, String err) {
    addUserError(matcher, err);
    return this;
  }

  /**
   * Exposed mainly for use with addError() and custom validation.
   * Use this to obtain the name of the argument used in cases
   * where a user can choose between, say, "-n" or "--name". Also gives
   * a sample value(s) for wildcards.
   */
  public String identify(Matcher<?> ma) {
    return ma.wildcard
      ?helpParam(new StringBuilder(), ma, null).toString()
      :(ma.name!=null ?ma.name :ma.names[0]);
  }

  //////////////////////////////
  //                          //
  // HELP GENERATION METHODS: //
  //                          //
  //////////////////////////////

  /**
   * Prints a complete set of man-page-like help documentation based on configuration,
   * including three sections, in order: synopsis, summary & options.
   * <p>
   * For named arguments, only the first name provided to Args.add() will be printed in
   * the Synopsis; the more detailed Options section will list any extra names provided
   * as alternates.
   * <br>
   * Note: Normally Appendable.append() throws IOException. For convenience, we wrap it
   * in a RuntimeException to cut back on needless catching/declaring.
   */
  public void help(Appendable app) {
    quietly(()->{
      helpSynopsisLine(app);
      if (helpIntro!=null)
        for (String s: helpIntro)
          app.append("\n").append(helpIndent).append(s);
      helpOptions(app);
    });
  }

  /**
   * Prints the "Synopsis" line that appears at the top of help() output. This is
   * done in a semi-typical approximation of BNF.
   */
  public void helpSynopsisLine(Appendable app) {
    quietly(()->{
      app.append("Synopsis:");
      if (helpProgramName!=null)
        app.append(" ").append(helpProgramName);
      for (Matcher<?> matcher: matchers)
        if (matcher.onlyIf==null)
          helpSynopsis(app, matcher);
      app.append("\n");
    });
  }
  private void helpSynopsis(Appendable app, Matcher<?> mat) throws IOException {
    app.append(" ");
    if (!mat.wildcard)
      app.append(mat.required ?"<" :"[");
    helpParam(app, mat, mat.wildcard ?null :mat.names[0]);
    if (mat.onlyIfMe!=null)
      for (Matcher<?> depender: mat.onlyIfMe)
        helpSynopsis(app, depender);
    if (!mat.wildcard)
      app.append(mat.required ?">" :"]");
  }

  /** Prints the "Options" section that appears last in help()'s output */
  public void helpOptions(Appendable app) {
    quietly(()->{
      app.append("\n\n").append(helpIndent).append("Options:");
      for (Matcher<?> m: matchers)
        helpOption(app, m);
    });
  }
  private void helpOption(Appendable app, Matcher<?> mat) throws IOException {
    final String indent="\n"+helpIndent;
    if (!mat.wildcard)
      for (int i=0; i<mat.names.length; i++) {
        app.append(indent);
        helpParam(app, mat, mat.names[i]);
        if (mat.multiParam && !mat.wildcard && mat.names[i].startsWith("--"))
          app.append(" (repeatable)");
      }
    else {
      app.append(indent);
      helpParam(app, mat, null);
    }
    if (mat.required)
      app.append(indent).append("  * Required");
    if (mat.help!=null)
      for (String line: mat.help)
        app.append(indent).append("  ").append(line);
    app.append("\n");
  }

  private Appendable helpParam(Appendable app, Matcher<?> mat, String name) {
    // Warning: Used in a variety of strange places:
    // 1. helpOption() and helpSynopsis() use it very similarly
    // 2. identify() uses it weirdly but only for wildcards
    quietly(()->{
      if (name!=null) app.append(name);

      if (mat.allowsParam || mat.wildcard) {
        boolean angles=mat.requiresParam || (mat.wildcard && mat.required);
        boolean doubleDash=name!=null && name.startsWith("--");

        if (!mat.wildcard && doubleDash)
          app.append(angles ?"=<" :"[=");
        else
          app.append(name!=null ?" " :"")
            .append(angles ?"<" :"[");

        app.append(
            mat.paramSample!=null ?mat.paramSample :"value"
          )
          .append(mat.multiParam && !doubleDash ?"(s)" :"")
          .append(angles ?">" :"]");
      }
    });
    return app;
  }

  ////////////////////
  //                //
  // MATCH METHODS: //
  //                //
  ////////////////////

  private static final int MATCH_NAME=1, MATCH_NAME_AND_PARAM=2, MATCH_WILDCARD=3;
  private static class MatchCapture {
    int matchType;
    Matcher<?> matcher;
    public MatchCapture(int matchType, Matcher<?> matcher) {
      this.matchType=matchType;
      this.matcher=matcher;
    }
  }

  /**
   * Compares Matchers to a typical <code>String[]</code> array from <code>public static void main()</code>. Results will be
   * stored in the original Matcher objects (results can be cleared using <code>clear()</code>, for whatever reasons).
   * <p>
   * On a successful match:
   * <ul>
   *   <li>Matcher.found() will return true.
   *   <li>Matcher.getName() will give the argument name the user provided (be it the "canonical"
   *      name or an alias) or null for unnamed/wildcard arguments.
   *   <li>Matcher.getParam() will give a parameter value for single-valued arguments;
   *   <li>Matcher.getParams() will give parameter values for multi-valued arguments.
   * </ul>
   * <p>
   * If instead matching fails because of validation errors, Args.hasUserErrors() will return true.
   * Use Args.getUsersErrors() to get detailed information about the errors.
   * <p>
   * Values in args[] are tested against matchers in the order matchers were add()'ed; thus in the case
   * of "grep regex file1 file2 file3", we need to add single-parameter wildcard matcher for our regex and
   * a multi-parameter wildcard matcher for our files after that - not the other way around.
   * <p>
   * Obviously there is potential for confusion when a value begins with a "-". So:
   * <ul>
   *   <li>If any Matcher names start with "--", then any member of args[] prefixed with "--" is assumed to be
   *   a named argument and not a parameter (wildcard or otherwise); if it doesn't match any argument names, a
   *   validation error is created.
   *   <li>If _any_ Matcher names start with a single "-" only (as in "-a"), then any value starting with just
   *   "-" carries the same assumption as above.
   *   <li>So if no Matchers have dashed names, "-" and "--" are permitted as parameter prefixes.
   * </ul>
   * <p>
   * Of course the simple way to avoid confusion is to use double-dashed argument names, e.g.
   * <pre>
   *    Args.add("--index").setAllowsParam()
   * </pre>
   * - which allows the user to type "--index=-1" without any ambiguity.
   */
  public void match(String[] args) {

    // Loop thru arguments and do matches; it would be more "performant" to
    // use Map<String,Matcher> kinds of things instead of looping but we didn't
    // do that so oh well:
    outer: for (int i=0; i<args.length; i++) {
      for (Matcher<?> matcher: matchers) {
        MatchCapture matched=match(matcher, args[i]);
        if (matched!=null) {
          if (matched.matchType==MATCH_NAME && matched.matcher.allowsParam) {
            if (notArgNext(i, args))
              i=addParamValues(matched.matcher, ++i, args);
            else
            if (matched.matcher.requiresParam)
              addMissingParamError(matcher);
          }
          else
          if (matched.matchType==MATCH_WILDCARD)
            i=addParamValues(matched.matcher, i, args);
          continue outer;
        }
      }
      addUserError(null, "Invalid argument: \""+args[i]+"\"");
    }

    // Validate that all required are found:
    for (Matcher<?> matcher: matchers)
      if (!matcher.failed) {
        if (matcher.required && !matcher.found && (matcher.onlyIf==null || matcher.onlyIf.found))
          addUserError(matcher, "Missing argument: "+identify(matcher));
        else
        if (matcher.found && matcher.onlyIf!=null && !matcher.onlyIf.found)
          addUserError(
            matcher,
            "Argument "
            +identify(matcher)+" only valid if "
            +identify(matcher.onlyIf)+" present"
          );
      }
  }

  /**
    * This looks at a single matcher & argument string, and whether a "capture" is possible.
    * If the string represents both an argument & parameter combined  (e.g. --xxx=foo or -Xfoo).
    * But it could also be "-abc" as a way of saying, "-a -b -c", in which case we have to check
    * other Matchers as well using the charToMatcher map.
    */
  private MatchCapture match(Matcher<?> matcher, String arg) {
    if (matcher.wildcard) {
      if (notArg(arg) && !matcher.found) {
        matcher.found=true;
        return new MatchCapture(MATCH_WILDCARD, matcher);
      }
    }
    else for (String maybeName: matcher.names) {
      final int maybeLen=maybeName.length();
      if (arg.equals(maybeName)) {
        matcher.found=true;
        matcher.name=maybeName;
        return new MatchCapture(MATCH_NAME, matcher);
      }
      else
      if (matcher.allowsParam && arg.startsWith(maybeName+"=")) {
        matcher.found=true;
        matcher.name=maybeName;
        String p=arg.substring(maybeLen+1);
        if (p.equals("")) p=null;
        if (p==null && matcher.requiresParam)
          addMissingParamError(matcher);
        addParamValue(matcher, p);
        return new MatchCapture(MATCH_NAME_AND_PARAM, matcher);
      }
      else
      if (maybeLen<=2 && arg.startsWith(maybeName)) {
        // Shortcuts:
        //   "-abc" for -a -b -c
        //   "-c~~" instead of "-c ~~" or "-c=~~",
        //   "-abc~~" instead of "-a -b -c ~~" or "-a -b -c=~~"
        //   And the "-" in "-abc" is optional
        int lastIndex=maybeLen-1;
        int argLen=arg.length();
        Matcher<?>[] all=new Matcher<?>[argLen];
        Matcher<?> lastMatcher=all[lastIndex]=matcher;
        boolean success=true;
        for (int i=lastIndex+1; success && i<argLen; i++) {
          Matcher<?> other=charToMatcher.get(arg.charAt(i));
          if (success=(other!=null && !lastMatcher.requiresParam))
            lastMatcher=all[lastIndex=i]=other;
        }

        if (success) {
          // The whole string is args:
          setFound(all, arg, argLen-1, maybeLen>1);
          return new MatchCapture(MATCH_NAME, lastMatcher);
        }
        else
        if (allowParamDelimiterSkip && lastMatcher.allowsParam && !arg.substring(lastIndex+1).startsWith(" ")) {
          // Combined args + a parameter for the last one:
          setFound(all, arg, lastIndex, maybeLen>1);
          addParamValue(lastMatcher, arg.substring(lastIndex+1));
          return new MatchCapture(MATCH_NAME_AND_PARAM, lastMatcher);
        }
      }
    }
    return null; // No match
  }

  private void setFound(Matcher<?>[] all, String arg, int last, boolean dashed) {
    for (int i=0; i<=last; i++)
      if (all[i]!=null) {
        all[i].found=true;
        all[i].name=(dashed ?"-" :"")+arg.charAt(i);
      }
  }

  private boolean notArgNext(int i, String[] args) {
    return i<args.length -1 && notArg(args[i+1]);
  }

  private boolean notArg(String value) {
    boolean noDash=paramNoDashes==null || !value.startsWith(paramNoDashes);
    return noDash || !allNames.contains(value);
  }

  private int addParamValues(Matcher<?> matcher, int i, String[] args) {
    addParamValue(matcher, args[i]);
    while (matcher.multiParam && notArgNext(i, args))
      addParamValue(matcher, args[++i]);
    return i;
  }

  private void addParamValue(Matcher<?> matcher, String value) {
    if (matcher.param!=null && !matcher.multiParam)
      addUserError(
        matcher,
        identify(matcher)+": Cannot supply multiple values; caused by: "+value
      );
    else
      try {
        matcher.convert(value);
      } catch (IllegalArgumentException e) {
        StringBuilder sb=new StringBuilder();
        if (!matcher.wildcard)
          sb.append("Argument ")
            .append(identify(matcher))
            .append(": ");
        sb.append(e.getMessage());
        addUserError(matcher, sb.toString());
      }
  }

  private void addMissingParamError(Matcher<?> matcher) {
    addUserError(matcher, "Argument "+identify(matcher)+" requires parameter");
  }

  private void addUserError(Matcher<?> matcher, String error) {
    if (matcher!=null) matcher.failed=true;
    if (errors==null) errors=new ArrayList<>();
    errors.add(error);
  }


  /** Finds out if we have validation errors. */
  public boolean hasUserErrors() {
    return errors!=null;
  }

  /**
   * Gets all the validation errors that happened during parsing.
   */
  public List<String> getUserErrors() {
    return errors;
  }

  /**
   * Pretty-prints validation errors, one per line.
   */
  public void getUserErrors(Appendable out) {
    quietly(()->{
      if (errors!=null)
        for (String e: errors)
          out.append(e).append("\n");
    });
  }

  /** Clears out arguments data set by match() */
  public Args clear() {
    for (Matcher<?> am: matchers) {
      am.found=false;
      am.failed=false;
      am.name=null;
      am.param=null;
      am.params   =am.params!=null    ?new ArrayList<>() :null;
    }
    if (errors!=null) errors=null;
    return this;
  }

  //////////////
  //          //
  // MATCHER: //
  //          //
  //////////////


  /**
   * Individual arguments &amp; their parameters are captured by ArgMatcher.
   */
  public static class Matcher<T> {
    // Note: OOP-wise, Matcher has been dumbed down quite a bit from separating logic "the right way"
    // into Args & Matcher parts. The logical leaping back & forth and each grabbing instance variables
    // from the other was getting nasty, however, so now Args is rather "top-heavy". At least I can assert
    // that if you've made it this far, there's nothing complicated from here down, especially since Matcher
    // is a *static* inner class.

    // Configuration:
    private final String[] names;
    private boolean wildcard, required, allowsParam, requiresParam, multiParam, isIntParam;
    private Matcher<?> onlyIf=null;
    private List<Matcher<?>> onlyIfMe=null;
    private Function<String, T> converter;

    // Configuration for documentation-only:
    private String paramSample=null;
    private String[] help;

    // Output:
    private boolean failed=false;
    private boolean found=false;
    private String name=null;
    private T param=null;
    private List<T> params=null;


    ////////////////////////////
    //                        //
    // CONFIGURATION METHODS: //
    //                        //
    ////////////////////////////

    private Matcher(Function<String, T> converter) {
      this.wildcard=true;
      this.converter=converter;
      this.names=null;
    }
    private Matcher(Function<String, T> converter, String... names) {
      this.wildcard=false;
      this.converter=converter;
      this.names=names;
    }

    /**
     * Allows one to provide detailed help for a given argument; this will appear in the
     * "Options" section for that argument. As with Args.setHelp(), you are
     * responsible for breaking the information up into multiple lines as necessary.
     */
    public Matcher<T> setHelp(String... s) {
      help=s;
      return this;
    }

    /**
     * For printing sample usage in documentation output, e.g. the "number" in "--count=&lt;number&gt;".
     * <p>
     * The default parameter sample is "value". Note that this automatically call setAllowsParam()
     * since such is implied.
     */
    public Matcher<T> setParamSample(String s) {
      paramSample=s;
      setAllowsParam();
      return this;
    }

    /**
     * <p>
     * Asserts that the argument is only valid when "other"
     * is also present (order of arguments does not matter). This affects both validation
     * and documentation. For example, in the help synopsis, the user will see something like
     * <pre>... [-other [-this]] ...</pre>
     * instead of
     * <pre>... [-other] [-this] ...</pre>
     * - and the presence of "-this" as input without "-other" will cause a validation failure.
     * <p>
     * Finally, if this argument is designated as required via <code>Matcher.setRequired()</code>, the
     * requirement is only checked if "other" is also present.
     */
    public Matcher<T> onlyIf(Matcher<?> other) {
      this.onlyIf=other;
      if (other.onlyIfMe==null)
        other.onlyIfMe=new ArrayList<>();
      other.onlyIfMe.add(this);
      return this;
    }

    /**
     * Indicates that the argument is required. For named (non-wildcard) arguments
     * it only makes sense that a parameter is required (why else would you require the argument),
     * so setRequiresParam() is automatically invoked as well.
     */
    public Matcher<T> setRequired() {
      this.required=true;
      setRequiresParam();
      return this;
    }

    /**
     * Indicates a named argument allows an optional parameter value. However, this will NOT
     * negate a previous call to setRequiresParam() (it effectively becomes a no-op).
     * <p>
     * This method call is unnecessary for unnamed wildcard Matchers, and is automatically invoked
     * by any method that implies parameterization for a named Matcher.
     */
    public Matcher<T> setAllowsParam() {
      this.allowsParam=true;
      this.requiresParam |= this.required;
      return this;
    }

    /**
     * Indicates a named argument requires a parameter value. It is not necessary to
     * call <code>setAllowsParam()</code> as well. This does NOT make the argument required, only
     * the parameter(s) for the argument.
     */
    public Matcher<T> setRequiresParam() {
      this.requiresParam=this.allowsParam=true;
      return this;
    }

    /**
     * Indicates this argument allows multiple parameter values, e.g. "--xxx hello world today".
     * For unnamed/wildcard arguments, this means we can match multiple values in sequence. When
     * combined with <code>setRequiresParam()</code>, a minimum of one parameter is required.
     */
    public Matcher<T> setMultiParam() {
      this.allowsParam=true;
      this.multiParam=true;
      this.params=new ArrayList<>();
      return this;
    }

    /**
     * A shortcut to <code>setRequiresParam().setMultiParam()</code>.
     */
    public Matcher<T> setRequiresMultiParam() {
      return setRequiresParam().setMultiParam();
    }

    /**
     * A shortcut to setRequired().setMultiParam(); the argument AND at least one parameter value are
     * required (for wildcards, the parameters are the argument itself, so the call is still valid
     * and yields sensible results, i.e. at least one value is required).
     */
    public Matcher<T> setRequiredAndMultiParam() {
      setRequired().setMultiParam();
      return this;
    }

    /** Mainly for debugging match() results. */
    public String toString() {
      return (names==null ?"<wildcard>" : "Names: "+Arrays.asList(names).toString())
        +" Found: "+found
        +(name==null ?"" :" Name: "+name)
        +(param==null ?"" :" Param: "+param)
        +(params==null || params.size()==0 ?"" :" Params: "+params);
    }

    private void convert(String value) {
      T t=converter.apply(value);
      if (param==null)
        param=t;
      if (multiParam && t!=null)
        params.add(t);
    }


    /////////////////////
    //                 //
    // OUTPUT METHODS: //
    //                 //
    /////////////////////

    /**
     * Indicates whether the argument was found in the user input. Warning: A Matcher
     * can return true for both found() <em>and</em> failed() when invalid input is captured.
     *
     * @return true if a matching argument was found i.e. "captured".
     */
    public boolean found() {
      return found;
    }

    /**
     * Indicates whether there were user errors when validating this Matcher.
     */
    public boolean failed() {
      return failed;
    }

    /**
     * Obtains the parameter value.
     * @param defaultValue This will be returned if the argument was not found,
     *   or if it was found but the parameter was optional and not provided.
     *   Note that for the case of "--arg=" defaultValue will be returned.
     *   Finally, if this Matcher is configured as a multi-parameter argument
     *   an IllegalStateException will be thrown.
     */
    public T getParam(T defaultValue) {
      T p=getParam();
      if (!found || p==null) return defaultValue;
      return p;
    }

    /**
     * Same as getParam(T) but returns null when no parameter was provided by the user.
     */
    public T getParam() {
      if (multiParam)
        throw new IllegalStateException("Not a single-parameter argument");
      return multiParam ?null :param;
    }

    /**
     * Obtains a list of values for multi-parameter arguments.
     * @param defaultValues A list of default values. Null is permitted.
     * @return The user-input values if any were provided; otherwise defaultValues.
     *    Note that for the case of "--arg= " defaultValues will be returned.
     */
    public List<T> getParams(List<T> defaultValues) {
      List<T> p=getParams();
      if (!found || p.isEmpty()) return defaultValues;
      return p;
    }

    /**
     * Obtains a list of values for multi-parameter arguments.
     * @return A non-null list if setMultiParam() was used; otherwise null.
     */
    public List<T> getParams() {
      return params;
    }

    /**
     * Obtains the name provided by the user for named (non-wildcard) arguments. Sometimes useful
     * when an argument allows more than one name.
     */
    public String getName() {
      return name;
    }

    /**
     * A convenience function for integer conversion of raw String values.
     * Example usage:
     * <pre>
        Matcher<Integer> matcher=args.add(Matcher::parseIntParam, "--number");
       </pre>
     */
    public static Integer parseIntParam(String raw) {
      try {
        StringBuilder sb=new StringBuilder();
        for (char c: raw.toCharArray())
          if (c!=',') sb.append(c);
        return Integer.parseInt(sb.toString());
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid parameter \""+raw+"\"");
      }
    }

  }
}
