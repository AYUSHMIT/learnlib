package de.learnlib.examples.roca;

import java.util.Random;

import de.learnlib.examples.DefaultLearningExample;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.Alphabet;

/**
 * Generates a random ROCA
 * 
 * @param <I> Input alphabet type
 * 
 * @author Gaëtan Staquet
 */
public class ExampleRandomROCA<I> extends DefaultLearningExample.DefaultROCALearningExample<I> {

    public ExampleRandomROCA(Alphabet<I> alphabet, int size, double acceptanceProb) {
        this(new Random(), alphabet, size, acceptanceProb);
    }

    public ExampleRandomROCA(Random rand, Alphabet<I> alphabet, int size, double acceptanceProb) {
        super(alphabet, constructMachine(rand, alphabet, size, acceptanceProb));
    }

    public static <I> ROCA<?, I> constructMachine(Random rand, Alphabet<I> alphabet, int size, double acceptanceProb) {
        return RandomAutomata.randomROCA(rand, size, acceptanceProb, alphabet);
    }
}
