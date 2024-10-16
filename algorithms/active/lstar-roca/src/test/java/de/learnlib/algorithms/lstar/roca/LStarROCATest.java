/* Copyright (C) 2021 – University of Mons, University Antwerpen
 * This file is part of LearnLib, http://www.learnlib.de/..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.algorithms.lstar.roca;

import static de.learnlib.algorithms.lstar.roca.ObservationTableWithCounterValuesROCA.UNKNOWN_COUNTER_VALUE;

import java.util.Collection;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.ROCAEquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.RestrictedAutomatonEquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.MembershipOracle.ROCAMembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.examples.dfa.ExamplePaulAndMary;
import de.learnlib.examples.roca.ExampleRandomROCA;
import de.learnlib.examples.roca.ExampleRegularROCA;
import de.learnlib.examples.roca.ExampleTinyROCA;
import de.learnlib.oracle.equivalence.roca.ROCARandomEQOracle;
import de.learnlib.oracle.equivalence.roca.RestrictedAutomatonROCASimulatorEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle.ROCASimulatorOracle;
import de.learnlib.oracle.membership.roca.CounterValueOracle;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.automatoncountervalues.ROCAFromDescription;
import net.automatalib.incremental.dfa.Acceptance;
import net.automatalib.incremental.dfa.tree.IncrementalPCDFATreeBuilder;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.words.impl.Symbol;

/**
 * @author Gaëtan Staquet
 */
public class LStarROCATest {
    private static <I> void testLearnROCA(ROCA<?, I> target, Alphabet<I> alphabet, LStarROCA<I> learner,
            EquivalenceOracle.ROCAEquivalenceOracle<I> eqOracle,
            MembershipOracle.ROCAMembershipOracle<I> membershipOracle,
            MembershipOracle.CounterValueOracle<I> counterValueOracle) {
        int maxRounds = (int) Math.pow(target.size(), 4);
        ROCA<?, I> learned = run(target, alphabet, learner, eqOracle, membershipOracle, counterValueOracle, maxRounds);
        Assert.assertNotNull(learned);

        ObservationTableWithCounterValuesROCA<I> table = learner.getObservationTable();
        checkPrefixTable(table);
    }

    private static <I> ROCA<?, I> run(ROCA<?, I> target, Alphabet<I> alphabet, LStarROCA<I> learner,
            EquivalenceOracle.ROCAEquivalenceOracle<I> eqOracle,
            MembershipOracle.ROCAMembershipOracle<I> membershipOracle,
            MembershipOracle.CounterValueOracle<I> counterValueOracle, int maxRounds) {
        learner.startLearning();

        while (maxRounds-- > 0) {
            verifyInvariants(learner.getObservationTable().getObservationTreeRoot(), membershipOracle,
                    counterValueOracle, learner.getCounterLimit());
            final Collection<ROCAFromDescription<?, I>> hypotheses = learner.getHypothesisModels();
            DefaultQuery<I, Boolean> counterexample = null;
            for (ROCAFromDescription<?, I> hypothesis : hypotheses) {
                DefaultQuery<I, Boolean> ce = eqOracle.findCounterExample(hypothesis, alphabet);

                if (ce == null) {
                    return hypothesis;
                } else if (!hypothesis.accepts(ce.getInput())
                        && learner.isCounterexample(ce.getInput(), ce.getOutput())) {
                    counterexample = ce;
                }
            }

            if (counterexample == null) {
                ROCAFromDescription<?, I> hypothesis = learner.getLearnedDFAAsROCA();
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

    /**
     * Only for testing purpose!
     */
    private static <I> void verifyInvariants(ObservationTreeNode<I> node, MembershipOracle<I, Boolean> membershipOracle,
            MembershipOracle<I, Integer> counterValueOracle, int counterLimit) {
        if (node.getOutput()) {
            Assert.assertTrue(node.isInPrefix());
        }

        if (node.getParent() != null) {
            if (!node.getParent().isInPrefix()) {
                Assert.assertFalse(node.isInPrefix());
            }

            if (node.getParent().isOutsideCounterLimit()) {
                Assert.assertTrue(node.isOutsideCounterLimit());
            }
        }

        if (node.isInTable() && node.isInPrefix() && !node.isOnlyForLanguage()) {
            Assert.assertNotEquals(node.getCounterValue(), UNKNOWN_COUNTER_VALUE);
        }

        if (node.isInPrefix()) {
            Assert.assertFalse(node.isOutsideCounterLimit());
        }

        if (node.getOutput() && !node.isOnlyForLanguage()) {
            Assert.assertEquals(node.getCounterValue(), 0);
        }

        for (ObservationTreeNode<I> successor : node.getSuccessors()) {
            verifyInvariants(successor, membershipOracle, counterValueOracle, counterLimit);
        }
    }

    private static <I> void checkPrefixTable(ObservationTableWithCounterValuesROCA<I> table) {
        IncrementalPCDFATreeBuilder<I> prefixOfL = new IncrementalPCDFATreeBuilder<>(table.getInputAlphabet());
        for (Row<I> row : table.getAllRows()) {
            List<PairCounterValueOutput<Boolean>> rowContents = table.fullWholeRowContents(row);
            for (int i = 0; i < table.numberOfSuffixes(); i++) {
                if (rowContents.get(i).getOutput()) {
                    prefixOfL.insert(row.getLabel().concat(table.getSuffix(i)));
                }
            }
        }

        for (Row<I> row : table.getAllRows()) {
            List<PairCounterValueOutput<Boolean>> rowContents = table.fullWholeRowContents(row);
            for (int i = 0; i < table.numberOfSuffixes(); i++) {
                if (!table.isSuffixOnlyForLanguage(i)) {
                    // The cell has a -1 iff the word is not in the prefix of the language
                    boolean isUnknown = rowContents.get(i).getCounterValue() == UNKNOWN_COUNTER_VALUE;
                    Acceptance isInPrefix = prefixOfL.lookup(row.getLabel().concat(table.getSuffix(i)));
                    boolean inPrefix = isInPrefix.equals(Acceptance.TRUE);

                    Assert.assertNotEquals(inPrefix, isUnknown);
                }
            }
        }
    }

    private <I> void launch(ROCA<?, I> roca, Alphabet<I> alphabet) {
        ROCAMembershipOracle<I> mOracle = new ROCASimulatorOracle<>(roca);
        CounterValueOracle<I> counterValueOracle = new CounterValueOracle<>(roca);
        ROCAEquivalenceOracle<I> rocaEqOracle = new ROCARandomEQOracle<>(roca);
        RestrictedAutomatonEquivalenceOracle<I> restrictedEqOracle = new RestrictedAutomatonROCASimulatorEQOracle<>(
                roca, alphabet);

        LStarROCA<I> learner = new LStarROCA<>(mOracle, counterValueOracle, restrictedEqOracle, alphabet);

        testLearnROCA(roca, alphabet, learner, rocaEqOracle, mOracle, counterValueOracle);
    }

    @Test
    public void testLearningRegularLanguage() {
        ExamplePaulAndMary example = ExamplePaulAndMary.createExample();
        Alphabet<Symbol> alphabet = example.getAlphabet();
        DFA<?, Symbol> dfa = example.getReferenceAutomaton();
        ROCA<?, Symbol> roca = OCAUtil.constructROCAFromDFA(dfa, alphabet);

        launch(roca, alphabet);
    }

    @Test
    public void testLearningTinyROCA() {
        ExampleTinyROCA example = new ExampleTinyROCA();
        Alphabet<Character> alphabet = example.getAlphabet();
        ROCA<?, Character> roca = example.getReferenceAutomaton();

        launch(roca, alphabet);
    }

    @Test
    public void testLearningRegularROCA() {
        ExampleRegularROCA example = new ExampleRegularROCA();
        launch(example.getReferenceAutomaton(), example.getAlphabet());
    }

    @Test(invocationCount = 10)
    public void testLearningRandomROCA() {
        Alphabet<Character> alphabet = Alphabets.characters('a', 'b');
        int size = 2;
        double acceptanceProb = 0.5;
        ExampleRandomROCA<Character> example = new ExampleRandomROCA<>(alphabet, size, acceptanceProb);
        ROCA<?, Character> roca = example.getReferenceAutomaton();
        launch(roca, alphabet);
    }
}