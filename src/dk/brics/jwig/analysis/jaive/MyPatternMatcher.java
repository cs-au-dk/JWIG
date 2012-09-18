package dk.brics.jwig.analysis.jaive;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import dk.brics.jwig.JWIGException;
import dk.brics.jwig.Priority;
import dk.brics.jwig.URLPattern;
import dk.brics.jwig.util.URLEncoding;

/**
 * Pattern matcher for {@link URLPattern}.
 */
public class MyPatternMatcher {
    // TODO refactor PatternMatcher to avoid this code duplication.
    private final String patternstring;

    private final Pattern parsedpattern;

    /**
     * Constructs a new pattern matcher. The pattern syntax is described
     * {@linkplain URLPattern here}.
     * 
     * @param pattern
     *            pattern string
     * @param full
     *            if false, forbid <tt>*</tt> and <tt>**</tt>
     * @throws IllegalArgumentException
     *             if the pattern has syntax errors
     */
    public MyPatternMatcher(String pattern, boolean full)
            throws IllegalArgumentException {
        patternstring = pattern;
        parsedpattern = Pattern.parse(pattern, full);
    }

    /**
     * @return the parsedpattern
     */
    public Pattern getParsedpattern() {
        return parsedpattern;
    }

    /**
     * Returns the pattern of this pattern matcher.
     */
    public String getPattern() {
        return patternstring;
    }

    /**
     * Checks whether the given string matches the pattern.
     * 
     * @param string
     *            string
     * @param params
     *            if non-null, parameters are collected in this name-value map
     * @return true if match
     */
    public boolean match(String string, Map<String, String> params) {
        if (string.length() == 0)
            string = "/";
        return matchPrefix(string, params) == string.length();
    }

    /**
     * Checks whether a prefix of the given string matches the pattern.
     * 
     * @param string
     *            string
     * @param params
     *            if non-null, parameters are collected in this name-value map
     * @return length of longest prefix of the string that matches the pattern,
     *         -1 if no match
     */
    public int matchPrefix(String string, Map<String, String> params) {
        return parsedpattern.matchPrefix(string, params);
    }

    /**
     * Returns the default priority of this pattern.
     */
    public float computeDefaultPriority() {
        return parsedpattern.computeDefaultPriority();
    }

    /**
     * Builds a string that matches this pattern, using the given arguments.
     * 
     * @param argmap
     *            map from argument names to values
     * @param full
     *            if true, add arguments that are not used in the pattern as a
     *            querystring
     */
    public String makeURL(Map<String, String[]> argmap, boolean full) {
        StringBuilder b = new StringBuilder();
        parsedpattern.makeURL(b, argmap);
        if (full && argmap != null && !argmap.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String[]> e : argmap.entrySet())
                for (String v : e.getValue()) {
                    if (v != null) {
                        if (first) {
                            b.append('?');
                            first = false;
                        } else {
                            b.append('&');
                        }
                        b.append(URLEncoding.encode(e.getKey())).append('=')
                                .append(URLEncoding.encode(v));
                    }
                }
        }
        return b.toString();
    }

    public List<String> getParameters() {
        return parsedpattern.getParameterNames();
    }

    public static class Pattern {

        List<ChoicePattern> choices;

        /**
         * @return the choices
         */
        public List<ChoicePattern> getChoices() {
            return choices;
        }

        /**
         * @param choices
         *            the choices to set
         */
        public void setChoices(List<ChoicePattern> choices) {
            this.choices = choices;
        }

        Pattern(List<ChoicePattern> choices) {
            this.choices = choices;
        }

        static Pattern parse(String pattern, boolean full) {
            List<ChoicePattern> choices = new ArrayList<ChoicePattern>();
            for (String s : pattern.split("\\|"))
                choices.add(ChoicePattern.parse(s, full));
            return new Pattern(choices);
        }

        int matchPrefix(String string, Map<String, String> params) {
            for (ChoicePattern c : choices) {
                int i = c.matchPrefix(string, params);
                if (i != -1)
                    return i;
            }
            return -1;
        }

        public float computeDefaultPriority() {
            float x = 0;
            for (ChoicePattern p : choices)
                x += p.computeDefaultPriority();
            return x;
        }

        void makeURL(StringBuilder b, Map<String, String[]> argmap) {
            choices.get(0).makeURL(b, argmap);
        }

        public List<String> getParameterNames() {
            return choices.get(0).getParameterNames();
        }

    }

    public static class ChoicePattern {

        List<PartPattern> parts;

        ChoicePattern(List<PartPattern> parts) {
            this.parts = parts;
        }

        /**
         * @return the parts
         */
        public List<PartPattern> getParts() {
            return parts;
        }

        static ChoicePattern parse(String pattern, boolean full) {
            List<PartPattern> parts = new ArrayList<PartPattern>();
            for (String s : pattern.split("\\/"))
                parts.add(PartPattern.parse(s, full));
            for (int i = 0; i + 1 < parts.size(); i++)
                if (parts.get(i) instanceof StarStarPattern)
                    invalidPattern();
            return new ChoicePattern(parts);
        }

        int matchPrefix(String string, Map<String, String> params) {
            int from = 0;
            for (PartPattern p : parts) {
                if (string.length() <= from || string.charAt(from) != '/')
                    return -1;
                int i = p.matchPrefix(string, from + 1, params);
                if (i == -1)
                    return -1;
                from = i;
            }
            return from;
        }

        float computeDefaultPriority() {
            float x = 0;
            for (PartPattern p : parts)
                x += p.computeDefaultPriority();
            return x;
        }

        void makeURL(StringBuilder b, Map<String, String[]> argmap) {
            boolean first = true;
            for (PartPattern p : parts) {
                if (first)
                    first = false;
                else
                    b.append('/');
                p.makeURL(b, argmap);
            }
        }

        public List<String> getParameterNames() {
            ArrayList<String> list = new ArrayList<String>();
            for (PartPattern p : parts) {
                list.addAll(p.getParameterNames());
            }
            return list;
        }

    }

    public static abstract class PartPattern {

        static PartPattern parse(String s, boolean full) {
            if (s.equals("*")) {
                if (!full)
                    invalidPattern();
                return new StarPattern();
            } else if (s.equals("**")) {
                if (!full)
                    invalidPattern();
                return new StarStarPattern();
            } else if (s.startsWith("$")) {
                check(s.substring(1));
                return new ParamPattern(s.substring(1));
            } else {
                check(s);
                return new ConstPattern(s);
            }
        }

        static void check(String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ("|/*$".indexOf(c) != -1)
                    invalidPattern();
            }
        }

        abstract int matchPrefix(String string, int from,
                Map<String, String> params);

        abstract float computeDefaultPriority();

        static int findNextPart(String string, int from) {
            int i = string.indexOf('/', from);
            if (i == -1 || i > string.length())
                i = string.length();
            return i;
        }

        abstract void makeURL(StringBuilder b, Map<String, String[]> argmap);

        abstract List<String> getParameterNames();
    }

    public static class ConstPattern extends PartPattern {

        /**
         * @return the str
         */
        public String getStr() {
            return str;
        }

        String str;

        ConstPattern(String str) {
            this.str = str;
        }

        @Override
        int matchPrefix(String string, int from, Map<String, String> params) {
            int i = findNextPart(string, from);
            if (str.equals(string.substring(from, i)))
                return i;
            else
                return -1;
        }

        @Override
        float computeDefaultPriority() {
            return 0;
        }

        @Override
        void makeURL(StringBuilder b, Map<String, String[]> argmap) {
            b.append(URLEncoding.encode(str));
        }

        @Override
        List<String> getParameterNames() {
            return Collections.emptyList();
        }
    }

    public static class ParamPattern extends PartPattern {

        String param;

        ParamPattern(String param) {
            this.param = param;
        }

        @Override
        int matchPrefix(String string, int from, Map<String, String> params) {
            int i = findNextPart(string, from);
            if (params != null)
                params.put(param, string.substring(from, i));
            return i;
        }

        @Override
        float computeDefaultPriority() {
            return 1f;
        }

        @Override
        void makeURL(StringBuilder b, Map<String, String[]> argmap) {
            if (argmap == null)
                throw new JWIGException("Parameters expected");
            String[] values = argmap.get(param);
            if (values == null)
                throw new JWIGException("Parameter expected: " + param);
            if (values.length != 1)
                throw new JWIGException("One parameter expected: " + param);
            b.append(URLEncoding.encode(values[0]));
            argmap.remove(param);
        }

        @Override
        List<String> getParameterNames() {
            return Arrays.asList(param);
        }
    }

    public static class StarPattern extends PartPattern {
        @Override
        int matchPrefix(String string, int from, Map<String, String> params) {
            return findNextPart(string, from);
        }

        @Override
        float computeDefaultPriority() {
            return 1f;
        }

        @Override
        void makeURL(StringBuilder b, Map<String, String[]> argmap) {
        }

        @Override
        List<String> getParameterNames() {
            return Collections.emptyList();
        }
    }

    public static class StarStarPattern extends PartPattern {
        @Override
        int matchPrefix(String string, int from, Map<String, String> params) {
            return string.length();
        }

        @Override
        float computeDefaultPriority() {
            return 10f;
        }

        @Override
        void makeURL(StringBuilder b, Map<String, String[]> argmap) {
        }

        @Override
        List<String> getParameterNames() {
            return Collections.emptyList();
        }
    }

    private static void invalidPattern() {
        throw new IllegalArgumentException("invalid URL pattern");
    }

    // added by esbena
    public static int computeDefaultPriority(String pattern) {
        return (int) new MyPatternMatcher(pattern, true)
                .computeDefaultPriority();
    }

    // added by esbena
    public static boolean isDefaultPriority(Method method) {
        Priority priorityAnnotation = method.getAnnotation(Priority.class);
        return priorityAnnotation == null;
    }

    // added by esbena
    public static int getPriority(Method method) {
        Priority priorityAnnotation = method.getAnnotation(Priority.class);
        final int priority;
        if (priorityAnnotation == null) {
            URLPattern patternAnnotation = method
                    .getAnnotation(URLPattern.class);
            final String pattern;
            if (patternAnnotation != null)
                pattern = patternAnnotation.value();
            else
                pattern = method.getName();
            priority = MyPatternMatcher.computeDefaultPriority(pattern);
        } else
            priority = priorityAnnotation.value();
        return priority;
    }
}