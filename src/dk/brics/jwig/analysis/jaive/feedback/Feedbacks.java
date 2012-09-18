package dk.brics.jwig.analysis.jaive.feedback;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Feedback managing class. To be used by the different phases for reporting all
 * kinds of info relevant to the end user of the tool.
 */
public class Feedbacks {
    private static Set<Feedback> feedbacks = new HashSet<Feedback>();

    public static void add(Feedback feedback) {
        feedbacks.add(feedback);
    }

    public static Set<Feedback> get() {
        return feedbacks;
    }

    public static void reset() {
        feedbacks.clear();
    }

    public static String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        Set<Feedback> feedbacks = Feedbacks.get();
        Set<Feedback> warnings = new HashSet<Feedback>();
        Set<Feedback> errors = new HashSet<Feedback>();
        for (Feedback f : feedbacks) {
            switch (f.getType()) {
            case ERROR:
                errors.add(f);
                break;
            case WARNING:
                warnings.add(f);
            }
        }
        sb.append("===\n");
        sb.append("Warnings: " + warnings.size() + "\n");
        sb.append("===\n");
        sb.append(prettyPrint(warnings));

        sb.append("===\n");
        sb.append("Errors: " + errors.size() + "\n");
        sb.append("===\n");
        sb.append(prettyPrint(errors));
        return sb.toString();
    }

    private static String prettyPrint(Collection<Feedback> feedbacks) {
        StringBuilder sb = new StringBuilder();
        Map<Class<? extends Feedback>, Integer> typeCount = new HashMap<Class<? extends Feedback>, Integer>();
        for (Feedback f : feedbacks) {
            final Class<? extends Feedback> type = f.getClass();
            sb.append(type.getSimpleName() + ": " + f.getMessage());
            sb.append("\n-----\n");
            if (!typeCount.containsKey(type))
                typeCount.put(type, 0);
            typeCount.put(type, typeCount.get(type) + 1);
        }
        sb.append("Summary:\n");
        for (Entry<Class<? extends Feedback>, Integer> entry : typeCount
                .entrySet()) {
            sb.append(entry.getValue() + " " + entry.getKey().getSimpleName()
                    + ", ");
        }
        sb.append("\n");
        return sb.toString();
    }
}
