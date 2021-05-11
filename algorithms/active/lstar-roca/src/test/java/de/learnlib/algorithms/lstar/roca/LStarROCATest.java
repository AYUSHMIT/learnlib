package de.learnlib.algorithms.lstar.roca;

import java.util.Collection;

import org.testng.Assert;
import org.testng.annotations.Test;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.ROCAEquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.RestrictedAutomatonEquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.MembershipOracle.RestrictedAutomatonMembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.examples.dfa.ExamplePaulAndMary;
import de.learnlib.examples.roca.ExampleRandomROCA;
import de.learnlib.examples.roca.ExampleRegularROCA;
import de.learnlib.examples.roca.ExampleTinyROCA;
import de.learnlib.oracle.equivalence.roca.ROCASimulatorEQOracle;
import de.learnlib.oracle.equivalence.roca.RestrictedAutomatonROCASimulatorEQOracle;
import de.learnlib.oracle.membership.roca.CounterValueOracle;
import de.learnlib.oracle.membership.roca.RestrictedAutomatonROCASimulatorOracle;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.util.tries.PrefixTrie;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.words.impl.Symbol;

public class LStarROCATest {
    private static <I> void testLearnROCA(ROCA<?, I> target, Alphabet<I> alphabet, LStarROCA<I> learner,
            EquivalenceOracle.ROCAEquivalenceOracle<I> eqOracle,
            MembershipOracle.RestrictedAutomatonMembershipOracle<I> membershipOracle,
            MembershipOracle.CounterValueOracle<I> counterValueOracle) {
        int maxRounds = (int) Math.pow(target.size(), 4);
        ROCA<?, I> learnt = run(target, alphabet, learner, eqOracle, membershipOracle, counterValueOracle, maxRounds);
        Assert.assertNotNull(learnt);
        Assert.assertTrue(OCAUtil.testEquivalence(target, learnt, alphabet));

        ObservationTableWithCounterValuesROCA<I> table = learner.getObservationTable();
        checkTable(table);
    }

    private static <I> ROCA<?, I> run(ROCA<?, I> target, Alphabet<I> alphabet, LStarROCA<I> learner,
            EquivalenceOracle.ROCAEquivalenceOracle<I> eqOracle,
            MembershipOracle.RestrictedAutomatonMembershipOracle<I> membershipOracle,
            MembershipOracle.CounterValueOracle<I> counterValueOracle, int maxRounds) {
        learner.startLearning();

        while (maxRounds-- > 0) {
            final Collection<ROCA<?, I>> hypotheses = learner.getHypothesisModels();
            DefaultQuery<I, Boolean> counterexample = null;
            for (ROCA<?, I> hypothesis : hypotheses) {
                DefaultQuery<I, Boolean> ce = eqOracle.findCounterExample(hypothesis, alphabet);

                if (ce == null) {
                    return hypothesis;
                } else if (!hypothesis.accepts(ce.getInput())) {
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

    private static <I> void checkTable(ObservationTableWithCounterValuesROCA<I> table) {
        PrefixTrie<I> prefixOfL = new PrefixTrie<>(table.getInputAlphabet());
        for (Row<I> row : table.getAllRows()) {
            for (int i = 0; i < table.numberOfSuffixes(); i++) {
                if (table.fullCellContents(row, i).getOutput()) {
                    prefixOfL.add(row.getLabel().concat(table.getSuffix(i)));
                }
            }
        }

        for (Row<I> row : table.getAllRows()) {
            for (int i = 0; i < table.numberOfSuffixes(); i++) {
                // The cell has a -1 iff the word is not in the prefix of the language
                Assert.assertEquals(table.fullCellContents(row, i).getCounterValue() != -1,
                        prefixOfL.contains(row.getLabel().concat(table.getSuffix(i))));
            }
        }
    }

    private <I> void launch(ROCA<?, I> roca, Alphabet<I> alphabet) {
        RestrictedAutomatonMembershipOracle<I> mOracle = new RestrictedAutomatonROCASimulatorOracle<>(roca);
        CounterValueOracle<I> counterValueOracle = new CounterValueOracle<>(roca);
        ROCAEquivalenceOracle<I> rocaEqOracle = new ROCASimulatorEQOracle<>(roca);
        RestrictedAutomatonEquivalenceOracle<I> restrictedEqOracle = new RestrictedAutomatonROCASimulatorEQOracle<>(
                roca, alphabet);

        LStarROCA<I> learner = new LStarROCA<>(mOracle, counterValueOracle, restrictedEqOracle, alphabet);

        testLearnROCA(roca, alphabet, learner, rocaEqOracle, mOracle, counterValueOracle);
    }

    @Test(timeOut = 1000)
    public void testLearningRegularLanguage() {
        ExamplePaulAndMary example = ExamplePaulAndMary.createExample();
        Alphabet<Symbol> alphabet = example.getAlphabet();
        DFA<?, Symbol> dfa = example.getReferenceAutomaton();
        ROCA<?, Symbol> roca = OCAUtil.constructROCAFromDFA(dfa, alphabet);

        launch(roca, alphabet);
    }

    @Test(timeOut = 1000)
    public void testLearningTinyROCA() {
        ExampleTinyROCA example = new ExampleTinyROCA();
        Alphabet<Character> alphabet = example.getAlphabet();
        ROCA<?, Character> roca = example.getReferenceAutomaton();

        launch(roca, alphabet);
    }

    @Test(timeOut = 1000)
    public void testLearningRegularROCA() {
        ExampleRegularROCA example = new ExampleRegularROCA();
        launch(example.getReferenceAutomaton(), example.getAlphabet());
    }

    @Test(invocationCount = 10, timeOut = 1000)
    public void testLearningRandomROCA() {
        Alphabet<Character> alphabet = Alphabets.characters('a', 'b');
        int size = 2;
        double acceptanceProb = 0.5;
        ExampleRandomROCA<Character> example = new ExampleRandomROCA<>(alphabet, size, acceptanceProb);
        ROCA<?, Character> roca = example.getReferenceAutomaton();
        launch(roca, alphabet);
    }
}