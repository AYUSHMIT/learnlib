package de.learnlib.algorithms.lstar.roca;

import java.util.Collection;

import org.testng.Assert;

import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;

public class LearningTest {
    public static <I> void testLearnROCA(ROCA<?, I> target, Alphabet<I> alphabet, LearningAlgorithm.ROCALearner<I> learner, EquivalenceOracle.ROCAEquivalenceOracle<I> eqOracle) {
        int maxRounds = (int)Math.pow(target.size(), 4);
        ROCA<?, I> learnt = run(target, alphabet, learner, eqOracle, maxRounds);
        Assert.assertNotNull(learnt);
        Assert.assertTrue(OCAUtil.testEquivalence(target, learnt, alphabet));
    }

    private static <I> ROCA<?, I> run(ROCA<?, I> target, Alphabet<I> alphabet, LearningAlgorithm.ROCALearner<I> learner, EquivalenceOracle.ROCAEquivalenceOracle<I> eqOracle, int maxRounds) {
        learner.startLearning();

        while (maxRounds-- > 0) {
            final Collection<ROCA<?, I>> hypotheses = learner.getHypothesisModels();
            DefaultQuery<I, Boolean> counterexample = null;
            for (ROCA<?, I> hypothesis : hypotheses) {
                DefaultQuery<I, Boolean> ce = eqOracle.findCounterExample(hypothesis, alphabet);

                if (ce == null) {
                    return hypothesis;
                }
                else if (!hypothesis.accepts(ce.getInput())) {
                    counterexample = ce;
                }
            }

            if (counterexample == null) {
                ROCA<?, I> hypothesis = learner.getLearntDFAAsROCA();
                counterexample = eqOracle.findCounterExample(hypothesis, alphabet);
                if (counterexample == null) {
                    return hypothesis;
                }
            }

            Assert.assertNotEquals(maxRounds, 0);

            final boolean refined = learner.refineHypothesis(counterexample);
            
            Assert.assertTrue(refined);
        }
        return null;
    }
}
