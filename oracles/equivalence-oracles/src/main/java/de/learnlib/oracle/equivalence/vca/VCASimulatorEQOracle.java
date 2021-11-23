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
package de.learnlib.oracle.equivalence.vca;

import java.util.Collection;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.oca.VCA;
import net.automatalib.automata.oca.automatoncountervalues.VCAFromDescription;
import net.automatalib.util.automata.Automata;
import net.automatalib.words.VPDAlphabet;
import net.automatalib.words.Word;

public class VCASimulatorEQOracle<I> implements EquivalenceOracle.VCAEquivalenceOracle<I> {

    private final VCA<?, I> reference;
    private final VPDAlphabet<I> alphabet;

    public VCASimulatorEQOracle(VCA<?, I> reference) {
        this(reference, reference.getAlphabet());
    }

    public VCASimulatorEQOracle(VCA<?, I> reference, VPDAlphabet<I> alphabet) {
        this.reference = reference;
        this.alphabet = alphabet;
    }

    @Override
    public @Nullable DefaultQuery<I, Boolean> findCounterExample(VCAFromDescription<?, I> hypothesis,
            Collection<? extends I> inputs) {
        final Word<I> sep = Automata.findSeparatingWordVCA(reference, hypothesis, alphabet);

        if (sep == null) {
            return null;
        }

        return new DefaultQuery<>(sep, reference.computeOutput(sep));
    }
    
}
