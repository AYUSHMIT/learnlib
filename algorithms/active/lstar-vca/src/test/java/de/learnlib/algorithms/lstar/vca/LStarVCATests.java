package de.learnlib.algorithms.lstar.vca;

import static de.learnlib.algorithms.lstar.vca.VCALearningUtils.computeMaximalCounterValue;

import java.util.Collection;

import org.testng.Assert;
import org.testng.annotations.Test;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.RestrictedAutomatonEquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.VCAEquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.MembershipOracle.RestrictedAutomatonMembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.examples.vca.ExampleRandomVCA;
import de.learnlib.oracle.equivalence.vca.RestrictedAutomatonVCASimulatorEQOracle;
import de.learnlib.oracle.equivalence.vca.VCASimulatorEQOracle;
import de.learnlib.oracle.membership.vca.RestrictedAutomatonVCASimulatorOracle;
import net.automatalib.automata.oca.VCA;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.VPDAlphabet;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.words.impl.DefaultVPDAlphabet;

public class LStarVCATests {
    private static <I> void runTest(VCA<?, I> target, VPDAlphabet<I> alphabet) {
        RestrictedAutomatonMembershipOracle<I> mOracle = new RestrictedAutomatonVCASimulatorOracle<>(target);
        VCAEquivalenceOracle<I> vcaEqOracle = new VCASimulatorEQOracle<>(target);
        RestrictedAutomatonEquivalenceOracle<I> restrictedEqOracle = new RestrictedAutomatonVCASimulatorEQOracle<>(
                target, alphabet);

        LStarVCA<I> learner = new LStarVCA<>(mOracle, restrictedEqOracle, alphabet);

        int maxRounds = (int) Math.pow(target.size(), 4);

        VCA<?, I> learnt = learn(target, alphabet, learner, vcaEqOracle, mOracle, maxRounds);
        Assert.assertTrue(OCAUtil.testEquivalence(target, learnt, alphabet));
    }

    private static <I> VCA<?, I> learn(VCA<?, I> target, VPDAlphabet<I> alphabet, LStarVCA<I> learner,
            EquivalenceOracle.VCAEquivalenceOracle<I> eqOracle,
            MembershipOracle.RestrictedAutomatonMembershipOracle<I> membershipOracle, int maxRounds) {
        learner.startLearning();

        while (maxRounds-- > 0) {
            final Collection<VCA<?, I>> hypotheses = learner.getHypothesisModels();
            DefaultQuery<I, Boolean> counterexample = null;

            for (VCA<?, I> hypothesis : hypotheses) {
                DefaultQuery<I, Boolean> ce = eqOracle.findCounterExample(hypothesis, alphabet);

                if (ce == null) {
                    return hypothesis;
                } else if (computeMaximalCounterValue(ce.getInput(), alphabet) > learner.getCounterLimit()) {
                    counterexample = ce;
                }
            }

            if (counterexample == null) {
                VCA<?, I> hypothesis = learner.getLearntDFAAsVCA();
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

    @Test(invocationCount = 10, timeOut = 10000, successPercentage = 70)
    public void testRandomVCA() {
        VPDAlphabet<Character> alphabet = new DefaultVPDAlphabet<>(Alphabets.characters('a', 'b'),
                Alphabets.characters('c', 'd'), Alphabets.characters('e', 'f'));
        ExampleRandomVCA<Character> example = new ExampleRandomVCA<>(alphabet, 2, 1, 0.5);

        runTest(example.getReferenceAutomaton(), alphabet);
    }
}
