package guessinggame;

import dk.brics.jwig.persistence.AbstractPersistable;
import dk.brics.jwig.persistence.HibernateQuerier;
import java.util.List;
import org.hibernate.Criteria;
import org.hibernate.Transaction;

public class GameState extends AbstractPersistable {
	private int plays = 0;
	private String holder = null;
	private int record = ;
	int getRecord() {
return ;
		
} 
	int getPlays() {
return ;
		
} 
	String getHolder() {
return ;
		
} 
	private void changed() {
save();
		
} 
	void incrementPlays() {
++;
		changed();
		
} 
	synchronized void setRecord(int guesses, String name) {
if (((guesses < getRecord()))) {
setHolder(name);
			setRecord(guesses);
			changed();
			
} 
		
		
} 
	public static GameState load() {
org.hibernate.classic.Session session = getFactory().getCurrentSession();
		Criteria c = session.createCriteria(GameState.class);
		List<?> list = c.list();
		GameState game = null;
		if (((list.size() > 0))) game  =  (GameState)list.get(0);
		
		if (((game == null))) game  =  new GameState();
		
		return game;
		
} 
	public void save() {
org.hibernate.classic.Session session = getFactory().getCurrentSession();
		Transaction transaction = session.beginTransaction();
		session.saveOrUpdate(this);
		transaction.commit();
		
} 
	void setPlays(int plays) {
this.plays  =  plays;
		
} 
	void setHolder(String holder) {
this.holder  =  holder;
		
} 
	void setRecord(int record) {
this.record  =  record;
		
} 
	}

