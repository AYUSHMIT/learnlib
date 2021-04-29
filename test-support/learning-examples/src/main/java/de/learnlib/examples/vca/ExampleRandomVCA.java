package de.learnlib.examples.vca;

import java.util.Random;

import de.learnlib.examples.DefaultLearningExample;
import net.automatalib.automata.oca.VCA;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.VPDAlphabet;

/**
 * Example generating a random m-VCA
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
public class ExampleRandomVCA<I> extends DefaultLearningExample.DefaultVCALearningExample<I> {

    /**
     * Constructs an example generating a random m-VCA.
     * 
     * A m-VCA has (m+1) transition functions
     * 
     * @param alphabet       The alphabet
     * @param size           The number of states
     * @param m              The number of transition functions is (m+1)
     * @param acceptanceProb The probability that a state is accepting
     */
    public ExampleRandomVCA(VPDAlphabet<I> alphabet, int size, int m, double acceptanceProb) {
        this(new Random(), alphabet, size, m, acceptanceProb);
    }

    /**
     * Constructs an example generating a random m-VCA.
     * 
     * A m-VCA has (m+1) transition functions
     * 
     * @param rand           The random number generator
     * @param alphabet       The alphabet
     * @param size           The number of states
     * @param m              The number of transition functions is (m+1)
     * @param acceptanceProb The probability that a state is accepting
     */
    public ExampleRandomVCA(Random rand, VPDAlphabet<I> alphabet, int size, int m, double acceptanceProb) {
        super(alphabet, constructMachine(rand, alphabet, size, m, acceptanceProb));
    }

    /**
     * Constructs a random m-VCA.
     * 
     * A m-VCA has (m+1) transition functions
     * 
     * @param <I>            Input alphabet type
     * @param rand           The random number generator
     * @param alphabet       The alphabet
     * @param size           The number of states
     * @param m              The number of transition functions is (m+1)
     * @param acceptanceProb The probability that a state is accepting
     */
    public static <I> VCA<?, I> constructMachine(Random rand, VPDAlphabet<I> alphabet, int size, int m,
            double acceptanceProb) {
        return RandomAutomata.randomVCA(rand, size, m, acceptanceProb, alphabet);
    }
}
