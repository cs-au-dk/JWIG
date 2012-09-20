package guessinggame;

import java.net.URL;
import java.util.*;
import dk.brics.jwig.*;
import dk.brics.xact.*;
import dk.brics.jwig.persistence.HibernateQuerier;

@URLPattern("guessinggameses") public class GuessingGame extends WebApp {
	public GuessingGame() {
super();
		getConfig().addClass(GameState.class);
		
} 
	XML wrapper = dk.brics.xact.XML.parseTemplate("<html><head><title>The Guessing Game</title></head><body bgcolor=\"aqua\"><[BODY]></body></html>");
	Random rnd = new Random();
	class UserState extends Session {
		int number;
		int guesses;
		XML page;
		UserState() {
super();
			  =  (nextInt(100) + 1);
			  =  0;
			  =  plug("BODY", dk.brics.xact.XML.parseTemplate("<div>Please guess a number between 1 and 100:</div><form method=\"post\" action=[GUESS]><input name=\"guess\" type=\"text\" size=\"3\" maxlength=\"3\"/><input type=\"submit\" value=\"continue\"/></form>")).plug("GUESS", new SubmitHandler() {
				void run(int guess) {
++;
					if ((guess != )) {
  =  setContent("/xhtml:html/xhtml:body/xhtml:div", dk.brics.xact.XML.parseTemplate("\n"+
"                That is not correct. Try a \n"+
"                <b><[0GENERATEDGAP0]></b> number:\n"+
"              ").plug("0GENERATEDGAP0", (((guess > ))) ? ("lower") : ("higher")));
						
} 
					else {
  =  setContent("/xhtml:html/xhtml:body", dk.brics.xact.XML.parseTemplate("\n"+
"	              You got it, using <b><[0GENERATEDGAP0]></b> guesses.<p/>").plug("0GENERATEDGAP0", ));
						final XML thanks = dk.brics.xact.XML.parseTemplate("Thank you for playing this exciting game!");
						GameState game = load();
						if (((game.getRecord() > 0) && ( >= game.getRecord())))   =  appendContent("/xhtml:html/xhtml:body", thanks);
						else   =  appendContent("/xhtml:html/xhtml:body", dk.brics.xact.XML.parseTemplate("\n"+
"                  That makes you the new record holder!<p/>\n"+
"                  Please enter your name for the hi-score list:\n"+
"                  <form method=\"post\" action=[RECORD]><input name=\"name\" type=\"text\" size=\"20\"/><input type=\"submit\" value=\"continue\"/></form>")).plug("RECORD", new SubmitHandler() {
							void run(String name) {
synchronized(GuessingGame.class) {
GameState game = load();
									if (( < game.getRecord())) game.setRecord(, name);
									
									
} 
								  =  setContent("/xhtml:html/xhtml:body", thanks);
								update(UserState.this);
								
} 
							});
						
						
} 
					
					update(UserState.this);
					
} 
				});
			
} 
		}
	public URL start() {
load().incrementPlays();
		return makeURL("play", new UserState(GuessingGame.this));
		
} 
	public XML play(UserState s) {
return s.page;
		
} 
	public XML record() {
final GameState game = load();
		return plug("BODY", new XMLProducer() {
			XML run() {
synchronized(game) {
if ((game.getHolder() != null)) return dk.brics.xact.XML.parseTemplate("\n"+
"              In <[0GENERATEDGAP0]> plays of this game, \n"+
"              the record holder is <b><[0GENERATEDGAP1]></b> with \n"+
"              <b><[0GENERATEDGAP2]></b> guesses.\n"+
"            ").plug("0GENERATEDGAP0", game.getPlays()).plug("0GENERATEDGAP1", game.getHolder()).plug("0GENERATEDGAP2", game.getRecord());
					else return dk.brics.xact.XML.parseTemplate("<[0GENERATEDGAP0]> plays started. No players finished yet.").plug("0GENERATEDGAP0", game.getPlays());
					
					
} 
				
} 
			});
		
} 
	}

