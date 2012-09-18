//import dk.brics.jwig.analysis.GraphAnalyzer;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Analyze {
    public static void main(String[] args) {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        //new GraphAnalyzer().getStateMachine(Main.class);
    }
}
