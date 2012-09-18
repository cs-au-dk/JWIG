package dk.brics.jwig.analysis.jaive.feedback;

import java.util.List;

import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.tagkit.LineNumberTag;
import soot.tagkit.SourceFileTag;
import soot.tagkit.Tag;
import dk.brics.misc.Origin;

public class SourceUtil {
    /**
     * Creates an {@link Origin} from the location of the given statement. If no
     * line number information is found, -1 is used, symbolising unknown.
     * 
     * @param method
     *            the {@link SootMethod} containing the statement
     * @param statement
     *            as the {@link Stmt} which location we want
     * 
     * @return the {@link Origin} (if known) of the statement
     */
    public static Origin getLocation(SootMethod method, Stmt statement) {
        List<Tag> tags = statement.getTags();
        int lineNumber = -1;
        for (Tag tag : tags) {
            if (tag instanceof LineNumberTag) {
                lineNumber = ((LineNumberTag) tag).getLineNumber();
            }
        }
        return new Origin(getSourceFileName(method.getDeclaringClass()),
                lineNumber, 0);
    }

    /**
     * Finds the source file name of a {@link SootClass} If no information is
     * found, the qualified name of the class is used.
     * 
     * @param classs
     *            as the class to find the source file name for
     * @return the source file name of the class (if any)
     */
    private static String getSourceFileName(SootClass classs) {
        List<Tag> tags = classs.getTags();
        String fileName = classs.getName();
        for (Tag tag : tags) {
            if (tag instanceof SourceFileTag) {
                SourceFileTag sourceFileTag = (SourceFileTag) tag;
                fileName = sourceFileTag.getSourceFile();
            }
        }
        return fileName;
    }
}
