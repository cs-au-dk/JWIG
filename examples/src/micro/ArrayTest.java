package micro;

import dk.brics.jwig.ParamName;
import dk.brics.jwig.URLPattern;
import dk.brics.jwig.WebApp;

@URLPattern("arraytest")
public class ArrayTest extends WebApp {

	@URLPattern("1")
	public String run(@ParamName("x") Integer[] x) {
		StringBuilder b = new StringBuilder("array:\n");
		for (int s : x)
			b.append(s).append("\n");
		return b.toString();
	}

	@URLPattern("2")
	public String run(@ParamName("x") String[] x) {
		StringBuilder b = new StringBuilder("array:\n");
		for (String s : x)
			b.append(s).append("\n");
		return b.toString();
	}

	@URLPattern("3")
	public String run(Integer x) {
		return x.toString();
	}
	
	@URLPattern("4")
	public String run(String x) {
		return x;
	}

	/*
	@URLPattern("5")
	public String run(String x, Parameters args) {
		StringBuilder b = new StringBuilder();
		b.append("x=" + x).append("\n");
		for (Map.Entry<String,List<FormField>> e : args.getMap().entrySet()) {
			b.append(e.getKey() + "=[ ");
			for (FormField f : e.getValue())
				b.append(((TextField)f).getValue() + " ");
			b.append("]\n");
		}
		return b.toString();
	}
	*/
} 
