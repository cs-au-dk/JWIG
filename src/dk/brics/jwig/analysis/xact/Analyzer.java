package dk.brics.jwig.analysis.xact;

import dk.brics.xact.analysis.XMLAnalysis;
import dk.brics.xmlgraph.Sharpener;
import org.apache.log4j.*;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Runs the XACT analysis on some JWIG classes.
 * 
 * @author Asger
 *
 */
public class Analyzer {
	public static void main(String[] args) {
		BasicConfigurator.configure(new ConsoleAppender(new SimpleLayout(), ConsoleAppender.SYSTEM_OUT));
		Logger.getRootLogger().setLevel(Level.INFO);
		Logger.getLogger(Sharpener.class.getName()).setLevel(Level.WARN); // sharpener tends to spam the INFO level
		
		String packageFilter = null;
		List<String> classes = new ArrayList<String>();
		for (String c : args) {
			if (c.startsWith("-package=")) {
				packageFilter = c.substring("-package=".length());
			}
		}
		for (String c : args) {
			if (c.startsWith("-dir=")) {
				addClassesFromDir("", new File(c.substring("-dir=".length())), true, packageFilter, classes);
			}
		}
		
		XMLAnalysis an = new XMLAnalysis(System.getProperty("soot-classpath"), classes);
		//an.setDiagnostics(new DumpfileDiagnostics("Analyzer", new File("out")).alwaysDump());
		an.setConfiguration(new JWIGConfiguration());
		an.analyze();
		
	}
	private static void addClassesFromDir(String prefix, File dir, boolean recursive, String packageFilter, Collection<String> classes) {
		File[] files = dir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory() || pathname.getName().endsWith(".class");
			}
		});
		if (packageFilter != null && !prefix.startsWith(packageFilter) && !packageFilter.startsWith(prefix)) {
			return; // nothing more to get here
		}
		for (File file : files) {
			if (file.isDirectory()) {
				if (recursive) {
					addClassesFromDir(prefix + file.getName() + ".", file, true, packageFilter, classes);
				}
			} else if (packageFilter == null || prefix.startsWith(packageFilter)) {
				String classname = file.getName().substring(0, file.getName().lastIndexOf('.'));
				String qname = prefix + classname;
				classes.add(qname);
			}
		}
	}
}
