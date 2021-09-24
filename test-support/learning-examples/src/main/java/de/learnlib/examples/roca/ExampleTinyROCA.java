package de.learnlib.examples.roca;

import de.learnlib.examples.DefaultLearningExample.DefaultROCALearningExample;
import net.automatalib.automata.oca.DefaultROCA;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.ROCALocation;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;

/**
 * An ROCA accepting {@code {a^n b^n | n >= 0}}.
 * 
 * @author Gaëtan Staquet
 */
public class ExampleTinyROCA extends DefaultROCALearningExample<Character> {

    public ExampleTinyROCA() {
        super(constructMachine());
    }

    /**
     * @return An ROCA accepting {a^n b^n | n >= 0}.
     */
    public static ROCA<?, Character> constructMachine() {
        Alphabet<Character> alphabet = Alphabets.characters('a', 'b');
        DefaultROCA<Character> roca = new DefaultROCA<>(alphabet);

        ROCALocation q0 = roca.addInitialLocation(false);
        ROCALocation q1 = roca.addLocation(true);

        roca.setSuccessor(q0, 0, 'a', +1, q0);
        roca.setSuccessor(q0, 1, 'a', +1, q0);
        roca.setSuccessor(q0, 1, 'b', -1, q1);

        roca.setSuccessor(q1, 1, 'b', -1, q1);

        return roca;
    }

    public static ExampleTinyROCA createExample() {
        return new ExampleTinyROCA();
    }
}
