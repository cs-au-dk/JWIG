package dk.brics.jwig.analysis;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.ArrayType;
import soot.BooleanType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import dk.brics.jwig.WebContext;

public class MakeURLSignatureHandler {

    private static Map<String, Integer> makeURLSignatureMap;
    private static JwigResolver resolver;
    private static Set<String> mappedURLs;

    private static String createMakeURLSignature(List<Type> argList) {
        if (resolver == null)
            resolver = JwigResolver.get();
        final RefType urlType = resolver.getSootClass(URL.class).getType();
        final SootClass webContextType = resolver
                .getSootClass(WebContext.class);
        return SootMethod.getSignature(webContextType, "makeURL", argList,
                urlType);
    }

    /**
     * @return the map from makeURL signatures to name-parameters position
     */
    public static Map<String, Integer> getMakeURLSignatures() {
        if (makeURLSignatureMap == null)
            createMakeURLSignatures();
        return makeURLSignatureMap;
    }

    /**
     * Creates a map from method signatures to numbers, the number corresponds
     * to the position of a String argument in the signature which should be
     * analyzed.
     */
    private static void createMakeURLSignatures() {
        RefType object = Scene.v().getSootClass(Object.class.getName())
                .getType();
        ArrayType objectArray = ArrayType.v(object, 1);
        RefType string = Scene.v().getSootClass(String.class.getName())
                .getType();
        Type booleanT = BooleanType.v();
        RefType classClass = Scene.v().getSootClass(Class.class.getName())
                .getType();
        RefType mapClass = Scene.v()
                .getSootClass(java.util.Map.class.getName()).getType();

        LinkedList<Type> nameArgs = new LinkedList<Type>();
        nameArgs.add(string);
        nameArgs.add(objectArray);

        LinkedList<Type> boolNameArgs = new LinkedList<Type>(nameArgs);
        boolNameArgs.add(0, booleanT);

        LinkedList<Type> classNameArgs = new LinkedList<Type>(nameArgs);
        classNameArgs.add(0, classClass);

        LinkedList<Type> boolClassNameArgs = new LinkedList<Type>(classNameArgs);
        boolClassNameArgs.add(0, booleanT);

        LinkedList<Type> mapNameArgs = new LinkedList<Type>(classNameArgs);
        mapNameArgs.add(0, mapClass);

        LinkedList<Type> boolMapNameArgs = new LinkedList<Type>(mapNameArgs);
        boolMapNameArgs.add(0, booleanT);

        LinkedList<Type> boolClassMapNameArgs = new LinkedList<Type>(
                boolMapNameArgs);
        boolClassMapNameArgs.add(1, classClass);
        //
        // done constructing signatures
        //
        Map<String, Integer> signatureArgNumMap = new HashMap<String, Integer>();
        // the int value is the position of the String to be analysed, e.g. the
        // name parameter.
        signatureArgNumMap.put(createMakeURLSignature(nameArgs), 0);
        signatureArgNumMap.put(createMakeURLSignature(boolNameArgs), 1);
        signatureArgNumMap.put(createMakeURLSignature(classNameArgs), 1);
        signatureArgNumMap.put(createMakeURLSignature(boolClassNameArgs), 2);

        final String mapNameArgsSig = createMakeURLSignature(mapNameArgs);
        final String boolMapNameArgsSig = createMakeURLSignature(boolMapNameArgs);
        final String boolClassMapNameArgsSig = createMakeURLSignature(boolClassMapNameArgs);

        mappedURLs = new HashSet<String>();
        mappedURLs.add(mapNameArgsSig);
        mappedURLs.add(boolMapNameArgsSig);
        mappedURLs.add(boolClassMapNameArgsSig);

        signatureArgNumMap.put(mapNameArgsSig, 1);
        signatureArgNumMap.put(boolMapNameArgsSig, 2);
        signatureArgNumMap.put(boolClassMapNameArgsSig, 3);

        makeURLSignatureMap = signatureArgNumMap;
    }

    public static boolean isMappedMakeURL(String signature) {
        if (mappedURLs == null)
            createMakeURLSignatures();
        return mappedURLs.contains(signature);
    }
}
