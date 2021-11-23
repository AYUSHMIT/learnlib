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
package de.learnlib.oracle.equivalence.roca;

import org.testng.Assert;
import org.testng.annotations.Test;

import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.oca.DefaultROCA;
import net.automatalib.automata.oca.ROCALocation;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValuesROCA;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValuesState;
import net.automatalib.automata.oca.automatoncountervalues.ROCAFromDescription;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;

public class ROCARandomEQOracleTests {
    @Test(timeOut = 1000)
    public void testTermination() {
        // We take a simple complete ROCA that rejects every word
        // Since the period is zero, the oracle will finish
        Alphabet<Character> alphabet = Alphabets.singleton('a');
        DefaultROCA<Character> roca = new DefaultROCA<>(alphabet);

        ROCALocation q0 = roca.addInitialLocation(false);

        roca.setSuccessor(q0, 0, 'a', 0, q0);

        DefaultAutomatonWithCounterValuesROCA<Character> dfaWithCounterValues = new DefaultAutomatonWithCounterValuesROCA<>(alphabet);
        DefaultAutomatonWithCounterValuesState q0_dfa = dfaWithCounterValues.addInitialState(false, 0);
        dfaWithCounterValues.setSuccessor(q0_dfa, 'a', q0_dfa);

        ROCARandomEQOracle<Character> oracle = new ROCARandomEQOracle<>(roca);

        Assert.assertNull(oracle.findCounterExample(dfaWithCounterValues.asAutomaton(), alphabet));
    }

    @Test(timeOut = 1000)
    public void testEquivalenceWithSelf() {
        Alphabet<Character> alphabet = Alphabets.characters('a', 'b');
        DefaultROCA<Character> roca = new DefaultROCA<>(alphabet);

        ROCALocation q0 = roca.addInitialLocation(false);
        ROCALocation q1 = roca.addLocation(true);

        roca.setSuccessor(q0, 0, 'a', +1, q0);
        roca.setSuccessor(q0, 1, 'a', +1, q0);
        roca.setSuccessor(q0, 1, 'b', 0, q1);

        roca.setSuccessor(q1, 0, 'a', 0, q1);
        roca.setSuccessor(q1, 1, 'a', -1, q1);

        DefaultAutomatonWithCounterValuesROCA<Character> dfa = OCAUtil.constructRestrictedAutomaton(roca, 10);

        ROCARandomEQOracle<Character> oracle = new ROCARandomEQOracle<>(roca);

        boolean foundOne = false;
        for (ROCAFromDescription<?, Character> r : dfa.toAutomata(8)) {
            if (oracle.findCounterExample(r, alphabet) == null) {
                foundOne = true;
            }
        }

        Assert.assertTrue(foundOne);

        Assert.assertNotNull(oracle.findCounterExample(dfa.asAutomaton(), alphabet));
    }

    @Test(timeOut = 1000)
    public void testEquivalenceEmptyAndFull() {
        // L_1 = {a^n | n >= 0}
        // L_2 = {}
        Alphabet<Character> alphabet = Alphabets.singleton('a');
        DefaultROCA<Character> roca = new DefaultROCA<>(alphabet);
        
        ROCALocation q0_1 = roca.addInitialLocation(true);

        roca.setSuccessor(q0_1, 0, 'a', 0, q0_1);

        DefaultAutomatonWithCounterValuesROCA<Character> dfa = new DefaultAutomatonWithCounterValuesROCA<>(alphabet);

        DefaultAutomatonWithCounterValuesState q0_2 = dfa.addInitialState(false, 0);

        dfa.setSuccessor(q0_2, 'a', q0_2);

        ROCARandomEQOracle<Character> oracle = new ROCARandomEQOracle<>(roca);

        Assert.assertNotNull(oracle.findCounterExample(dfa.asAutomaton(), alphabet));
    }

    @Test(timeOut = 1000)
    public void testNonEquivalence() {
        // L_1 = {a^n b^{n+1} a | n > 0}
        // L_2 = {a^n b^{n+1} | n > 0}
        Alphabet<Character> alphabet = Alphabets.characters('a', 'b');
        DefaultROCA<Character> roca1 = new DefaultROCA<>(alphabet);
        
        ROCALocation q0_1 = roca1.addInitialLocation(false);
        ROCALocation q1_1 = roca1.addLocation(false);
        ROCALocation q2_1 = roca1.addLocation(true);

        roca1.setSuccessor(q0_1, 0, 'a', +1, q0_1);
        roca1.setSuccessor(q0_1, 1, 'a', +1, q0_1);
        roca1.setSuccessor(q0_1, 1, 'b', 0, q1_1);

        roca1.setSuccessor(q1_1, 0, 'a', 0, q2_1);
        roca1.setSuccessor(q1_1, 1, 'b', -1, q1_1);

        DefaultROCA<Character> roca2 = new DefaultROCA<>(alphabet);

        ROCALocation q0_2 = roca2.addInitialLocation(false);
        ROCALocation q1_2 = roca2.addLocation(true);

        roca2.setSuccessor(q0_2, 0, 'a', +1, q0_2);
        roca2.setSuccessor(q0_2, 1, 'a', +1, q0_2);
        roca2.setSuccessor(q0_2, 1, 'b', 0, q1_2);

        roca2.setSuccessor(q1_2, 1, 'b', -1, q1_2);

        ROCARandomEQOracle<Character> oracle = new ROCARandomEQOracle<>(roca1);

        DefaultAutomatonWithCounterValuesROCA<Character> dfa = OCAUtil.constructRestrictedAutomaton(roca2, 10);
        for (ROCAFromDescription<?, Character> r : dfa.toAutomata(10)) {
            DefaultQuery<Character, Boolean> query = oracle.findCounterExample(r, alphabet);
            Assert.assertNotNull(query);
        }
        Assert.assertNotNull(oracle.findCounterExample(dfa.asAutomaton(), alphabet));
    }
}
