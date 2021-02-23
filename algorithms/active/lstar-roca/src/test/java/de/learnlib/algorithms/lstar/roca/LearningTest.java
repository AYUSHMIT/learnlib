package de.learnlib.algorithms.lstar.roca;

import java.util.Iterator;

import org.testng.Assert;

import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.automatoncountervalues.AcceptingOrExit;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;

public class LearningTest {
    public static <I> void testLearnROCA(ROCA<?, I> target, Alphabet<I> alphabet, LearningAlgorithm.ROCALearner<I> learner, EquivalenceOracle.ROCAEquivalenceOracle<I> eqOracle) {
        int maxRounds = (int)Math.pow(target.size(), 4);
        ROCA<?, I> learnt = run(target, alphabet, learner, eqOracle, maxRounds);
        System.out.println(learnt);
        Assert.assertNotNull(learnt);
        Assert.assertTrue(OCAUtil.testEquivalence(target, learnt, alphabet));
    }

    private static <I> ROCA<?, I> run(ROCA<?, I> target, Alphabet<I> alphabet, LearningAlgorithm.ROCALearner<I> learner, EquivalenceOracle.ROCAEquivalenceOracle<I> eqOracle, int maxRounds) {
        learner.startLearning();

        while (maxRounds-- > 0) {
            System.out.println("Remaining rounds: " + maxRounds);
            final Iterator<ROCA<?, I>> hypotheses = learner.getHypothesisModels();
            DefaultQuery<I, Boolean> counterexample = null;
            while (hypotheses.hasNext()) {
                ROCA<?, I> hypothesis = hypotheses.next();
                DefaultQuery<I, Boolean> ce = eqOracle.findCounterExample(hypothesis, alphabet);

                if (ce == null) {
                    System.out.println("Found a correct ROCA!");
                    return hypothesis;
                }
                counterexample = ce;
            }

            if (counterexample == null) {
                ROCA<?, I> hypothesis = learner.getLearntDFAAsROCA();
                counterexample = eqOracle.findCounterExample(hypothesis, alphabet);
                if (counterexample == null) {
                    System.out.println("The DFA is enough!");
                    return hypothesis;
                }
            }

            System.out.println("Before assert: " + maxRounds);
            Assert.assertNotEquals(maxRounds, 0);
            System.out.println("After assert: " + maxRounds);

            AcceptingOrExit acceptance = counterexample.getOutput() ? AcceptingOrExit.ACCEPTING : AcceptingOrExit.REJECTING;
            DefaultQuery<I, AcceptingOrExit> ce = new DefaultQuery<>(counterexample.getPrefix(), counterexample.getSuffix(), acceptance);

            System.out.println(ce);
            final boolean refined = learner.refineHypothesis(ce);
            
            System.out.println("Is refined? " + refined);
            Assert.assertTrue(refined);
        }
        return null;
    }
}
