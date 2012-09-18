package micro;

import java.net.URL;

import dk.brics.jwig.*;

public class Params extends WebApp {
	
	@URLPattern("")
	public URL index() {
		return makeURL("micro.Params.div2", 10, 2);
	}
	
	public String div(int x, int y) {
		return x + " / " + y + " = " + (x/y);
	}
	
	@URLPattern("div2/$x/$y")
	public String div2(int x, int y) {
		return x + " / " + y + " = " + (x/y);
	}
	
	public String addArray(@ParamName("x") float[] x) {
		float res = 0f;
		for (float q : x)
			res += q;
		return Float.toString(res);
	}
	
	public String addVarArgs(@ParamName("x") float... x) {
		float res = 0f;
		for (float q : x)
			res += q;
		return Float.toString(res);
	}
}
