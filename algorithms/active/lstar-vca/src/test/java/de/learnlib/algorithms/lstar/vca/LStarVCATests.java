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
package de.learnlib.algorithms.lstar.vca;

import java.util.Collection;

import org.testng.Assert;
import org.testng.annotations.Test;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.RestrictedAutomatonEquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.VCAEquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.examples.vca.ExampleRandomVCA;
import de.learnlib.oracle.equivalence.vca.RestrictedAutomatonVCASimulatorEQOracle;
import de.learnlib.oracle.equivalence.vca.VCASimulatorEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle.ROCASimulatorOracle;
import net.automatalib.automata.oca.VCA;
import net.automatalib.automata.oca.automatoncountervalues.VCAFromDescription;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.VPDAlphabet;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.words.impl.DefaultVPDAlphabet;

public class LStarVCATests {
    private static <I> void runTest(VCA<?, I> target, VPDAlphabet<I> alphabet) {
        ROCASimulatorOracle<I> mOracle = new ROCASimulatorOracle<>(target);
        VCAEquivalenceOracle<I> vcaEqOracle = new VCASimulatorEQOracle<>(target);
        RestrictedAutomatonEquivalenceOracle<I> restrictedEqOracle = new RestrictedAutomatonVCASimulatorEQOracle<>(
                target, alphabet);

        LStarVCA<I> learner = new LStarVCA<>(mOracle, restrictedEqOracle, alphabet);

        int maxRounds = (int) Math.pow(target.size(), 4);

        learn(target, alphabet, learner, vcaEqOracle, mOracle, maxRounds);
    }

    private static <I> VCA<?, I> learn(VCA<?, I> target, VPDAlphabet<I> alphabet, LStarVCA<I> learner,
            EquivalenceOracle.VCAEquivalenceOracle<I> eqOracle,
            MembershipOracle.ROCAMembershipOracle<I> membershipOracle, int maxRounds) {
        learner.startLearning();

        while (maxRounds-- > 0) {
            final Collection<VCAFromDescription<?, I>> hypotheses = learner.getHypothesisModels();
            DefaultQuery<I, Boolean> counterexample = null;
            int maximalCounterValue = learner.getCounterLimit();

            for (VCAFromDescription<?, I> hypothesis : hypotheses) {
                DefaultQuery<I, Boolean> ce = eqOracle.findCounterExample(hypothesis, alphabet);

                if (ce == null) {
                    return hypothesis;
                }

                int counterValue = OCAUtil.computeMaximalCounterValue(ce.getInput(), alphabet);
                if (counterValue > maximalCounterValue) {
                    counterexample = ce;
                    maximalCounterValue = counterValue;
                }
            }

            if (counterexample == null) {
                VCAFromDescription<?, I> hypothesis = learner.getLearnedDFAAsVCA();
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

    @Test(timeOut = 10000)
    public void testRandomVCA() {
        VPDAlphabet<Character> alphabet = new DefaultVPDAlphabet<>(Alphabets.characters('a', 'b'),
                Alphabets.characters('c', 'd'), Alphabets.characters('e', 'f'));
        ExampleRandomVCA<Character> example = new ExampleRandomVCA<>(alphabet, 3, 2, 0.5);

        runTest(example.getReferenceAutomaton(), alphabet);
    }
}
