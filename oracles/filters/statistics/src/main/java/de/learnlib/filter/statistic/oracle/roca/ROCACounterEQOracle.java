/* Copyright (C) 2021 – University of Mons, University Antwerpen
 * This file is part of AutomataLib, http://www.automatalib.net/.
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
package de.learnlib.filter.statistic.oracle.roca;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.filter.statistic.oracle.CounterEQOracle;
import net.automatalib.automata.oca.automatoncountervalues.ROCAFromDescription;

public class ROCACounterEQOracle<I> extends CounterEQOracle<ROCAFromDescription<?, I>, I, Boolean> implements EquivalenceOracle.ROCAEquivalenceOracle<I> {

    public ROCACounterEQOracle(EquivalenceOracle<ROCAFromDescription<?, I>, I, Boolean> equivalenceOracle,
            String name) {
        super(equivalenceOracle, name);
    }
    
}
