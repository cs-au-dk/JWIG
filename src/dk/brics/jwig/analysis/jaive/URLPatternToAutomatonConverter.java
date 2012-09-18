package dk.brics.jwig.analysis.jaive;

import java.util.Iterator;

import dk.brics.automaton.Automaton;
import dk.brics.jwig.analysis.jaive.MyPatternMatcher.ChoicePattern;
import dk.brics.jwig.analysis.jaive.MyPatternMatcher.ConstPattern;
import dk.brics.jwig.analysis.jaive.MyPatternMatcher.ParamPattern;
import dk.brics.jwig.analysis.jaive.MyPatternMatcher.PartPattern;
import dk.brics.jwig.analysis.jaive.MyPatternMatcher.Pattern;
import dk.brics.jwig.analysis.jaive.MyPatternMatcher.StarPattern;
import dk.brics.jwig.analysis.jaive.MyPatternMatcher.StarStarPattern;

public class URLPatternToAutomatonConverter {
    private final Automaton anyStringWithoutSlash;
    private final Automaton anyString;
    private final Automaton emptyString;

    public Automaton convert(String pattern) {
        return convert(new MyPatternMatcher(pattern, true));
    }

    private Automaton convert(MyPatternMatcher matcher) {
        Pattern pattern = matcher.getParsedpattern();
        Automaton choices = Automaton.makeEmpty();
        for (ChoicePattern choice : pattern.getChoices()) {
            choices = choices.union(convert(choice));
        }
        return choices;
    }

    Automaton convert(ChoicePattern choice) {
        Automaton parts = null;
        final Iterator<PartPattern> iterator = choice.getParts().iterator();
        while (iterator.hasNext()) {
            PartPattern part = iterator.next();
            if (parts == null)
                parts = convert(part);
            else
                parts = parts.concatenate(convert(part));
            boolean hasMoreParts = iterator.hasNext();
            if (hasMoreParts)
                parts = parts.concatenate(Automaton.makeChar('/'));

        }
        if (parts == null)
            throw new IllegalArgumentException(
                    "The ChoicePattern may not be empty!");
        return parts;
    }

    Automaton convert(PartPattern part) {
        Automaton automaton;
        if (part instanceof StarPattern) {
            automaton = anyStringWithoutSlash;
        } else if (part instanceof StarStarPattern) {
            automaton = anyString;
        } else if (part instanceof ConstPattern) {
            automaton = Automaton.makeString(((ConstPattern) part).getStr());
        } else if (part instanceof ParamPattern) {
            automaton = anyStringWithoutSlash.minus(emptyString);
        } else {
            throw new IllegalArgumentException("Class " + part.getClass()
                    + " not known.");
        }
        return automaton;
    }

    public URLPatternToAutomatonConverter() {
        this.anyString = Automaton.makeAnyString();
        emptyString = Automaton.makeEmptyString();
        Automaton slash = Automaton.makeChar('/');
        Automaton anyStringWithSlash = anyString.concatenate(slash)
                .concatenate(anyString);
        this.anyStringWithoutSlash = anyStringWithSlash.complement();
    }
}
