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

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.filter.statistic.oracle.CounterEQOracle;
import net.automatalib.automata.fsa.DFA;

/**
 * A {@link CounterEQOracle} for equivalence queries over a restricted
 * automaton, up to a counter limit.
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
public class RestrictedAutomatonCounterEQOracle<I> extends CounterEQOracle<DFA<?, I>, I, Boolean>
        implements EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> {

    EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> oracle;

    public RestrictedAutomatonCounterEQOracle(
            EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> equivalenceOracle, String name) {
        super(equivalenceOracle, name);
        this.oracle = equivalenceOracle;
    }

    @Override
    protected EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> getNextOracle() {
        return oracle;
    }

    @Override
    public void setCounterLimit(int counterLimit) {
        getNextOracle().setCounterLimit(counterLimit);
    }

}
