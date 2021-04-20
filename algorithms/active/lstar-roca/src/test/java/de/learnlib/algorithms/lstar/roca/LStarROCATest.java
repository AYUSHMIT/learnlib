package de.learnlib.algorithms.lstar.roca;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.EquivalenceOracle.ROCAEquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.RestrictedAutomatonEquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle.RestrictedAutomatonMembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.GenericObservationTableWithCounterValues.OutputAndCounterValue;
import de.learnlib.examples.dfa.ExamplePaulAndMary;
import de.learnlib.examples.roca.ExampleRandomROCA;
import de.learnlib.examples.roca.ExampleRegularROCA;
import de.learnlib.examples.roca.ExampleTinyROCA;
import de.learnlib.oracle.equivalence.roca.ROCASimulatorEQOracle;
import de.learnlib.oracle.equivalence.roca.RestrictedAutomatonSimulatorEQOracle;
import de.learnlib.oracle.membership.roca.CounterValueOracle;
import de.learnlib.oracle.membership.roca.RestrictedAutomatonSimulatorOracle;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
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

            checkEverything(learner.getObservationTable(), membershipOracle, counterValueOracle);
        }
        return null;
    }

    private static <I> void checkEverything(ObservationTableWithCounterValues<I> table,
            MembershipOracle.RestrictedAutomatonMembershipOracle<I> membershipOracle,
            MembershipOracle.CounterValueOracle<I> counterValueOracle) {
        for (Row<I> row : table.getLongPrefixRows()) {
            if (!table.isBinContents(table.fullRowContents(row))) {
                Assert.assertNotNull(table.getRepresentativeForEquivalenceClass(row));
            }
        }

        Set<Integer> rowContentsSet = new HashSet<>();
        List<Word<I>> pref = new ArrayList<>();
        for (Row<I> row : table.getAllRows()) {
            List<OutputAndCounterValue<Boolean>> contents = table.fullRowContents(row);
            rowContentsSet.add(row.getRowContentId());

            for (int i = 0; i < table.numberOfSuffixes(); i++) {
                OutputAndCounterValue<Boolean> cell = contents.get(i);
                Word<I> word = row.getLabel().concat(table.getSuffix(i));
                Boolean output = membershipOracle.answerQuery(word);
                Assert.assertEquals(output, cell.getOutput());
                if (table.isAccepted(cell)) {
                    for (Word<I> prefix : word.prefixes(false)) {
                        pref.add(prefix);
                    }
                }
            }

            for (Row<I> row2 : table.getAllRows()) {
                if (table.fullRowContents(row).equals(table.fullRowContents(row2))) {
                    Assert.assertEquals(row.getRowContentId(), row2.getRowContentId());
                } else {
                    Assert.assertNotEquals(row.getRowContentId(), row2.getRowContentId());
                }
            }
        }

        for (Row<I> row : table.getAllRows()) {
            for (int i = 0; i < table.numberOfSuffixes(); i++) {
                Word<I> word = row.getLabel().concat(table.getSuffix(i));
                Assert.assertEquals(table.fullCellContents(row, i).getCounterValue() != -1, pref.contains(word));
            }
        }

        Assert.assertEquals(rowContentsSet.size(), table.numberOfDistinctRows());
    }

    private <I> void launch(ROCA<?, I> roca, Alphabet<I> alphabet) {
        RestrictedAutomatonMembershipOracle<I> mOracle = new RestrictedAutomatonSimulatorOracle<>(roca);
        CounterValueOracle<I> counterValueOracle = new CounterValueOracle<>(roca);
        ROCAEquivalenceOracle<I> rocaEqOracle = new ROCASimulatorEQOracle<>(roca);
        RestrictedAutomatonEquivalenceOracle<I> restrictedEqOracle = new RestrictedAutomatonSimulatorEQOracle<>(roca,
                alphabet);

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

    // successPercentage is used because it can happen than some ROCAs take too long
    // to learn
    @Test(invocationCount = 10, timeOut = 100000, successPercentage = 70)
    public void testLearningRandomROCA() {
        Alphabet<Character> alphabet = Alphabets.characters('a', 'b');
        int size = 2;
        double acceptanceProb = 0.5;
        ExampleRandomROCA<Character> example = new ExampleRandomROCA<>(alphabet, size, acceptanceProb);
        ROCA<?, Character> roca = example.getReferenceAutomaton();
        launch(roca, alphabet);
    }
}