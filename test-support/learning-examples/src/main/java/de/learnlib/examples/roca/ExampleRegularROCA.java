package de.learnlib.examples.roca;

import de.learnlib.examples.DefaultLearningExample.DefaultROCALearningExample;
import net.automatalib.automata.oca.DefaultROCA;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.ROCALocation;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;

/**
 * An ROCA accepting a regular language.
 * 
 * This ROCA uses its counter value to reduce the state space.
 * 
 * @author Gaëtan Staquet
 */
public class ExampleRegularROCA extends DefaultROCALearningExample<Character> {

    public ExampleRegularROCA() {
        super(buildMachine());
    }

    public static ROCA<?, Character> buildMachine() {
        Alphabet<Character> alphabet = Alphabets.characters('a', 'c');
        DefaultROCA<Character> roca = new DefaultROCA<>(alphabet);

        ROCALocation q0 = roca.addInitialLocation(true);
        ROCALocation q1 = roca.addLocation(true);

        roca.setSuccessor(q0, 0, 'a', +1, q1);
        roca.setSuccessor(q0, 0, 'b', +1, q1);
        roca.setSuccessor(q0, 0, 'c', +1, q1);
        roca.setSuccessor(q0, 1, 'a', -1, q0);
        roca.setSuccessor(q0, 1, 'b', -1, q0);
        roca.setSuccessor(q0, 1, 'c', +1, q1);

        roca.setSuccessor(q1, 0, 'b', +1, q0);
        roca.setSuccessor(q1, 0, 'c', +1, q1);
        roca.setSuccessor(q1, 1, 'a', -1, q1);
        roca.setSuccessor(q1, 1, 'b', -1, q0);
        roca.setSuccessor(q1, 1, 'c', -1, q0);

        return roca;
    }
    
}
