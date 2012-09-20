package micro;

import dk.brics.jwig.*;
import dk.brics.xact.*;

public class HelloWorld3 extends WebApp {
	@URLPattern("") public XML hello() {
return dk.brics.xact.XML.parseTemplate("<html><head><title>Hello World</title></head><body><form method=\"get\" action=[SUBMIT]>\n"+
"					Write \"æøåÆØÅ\": <input type=\"text\" name=\"t\" value=\"æøåÆØÅ\"/><input type=\"submit\" value=\"Submit\"/></form></body></html>").plug("SUBMIT", new SubmitHandler() {
			XML run(String t) {
info(("t=" + t));
				String s = "æøåÆØÅ";
				return dk.brics.xact.XML.parseTemplate("<html><head><title>Hello World</title></head><body>\n"+
"							Your text: <[0GENERATEDGAP0]><p/>\n"+
"							Equal <[0GENERATEDGAP1]>: <[0GENERATEDGAP2]><p/></body></html>").plug("0GENERATEDGAP0", t).plug("0GENERATEDGAP1", s).plug("0GENERATEDGAP2", new Boolean(s.equals(t)));
				
} 
			});
		
} 
	}

