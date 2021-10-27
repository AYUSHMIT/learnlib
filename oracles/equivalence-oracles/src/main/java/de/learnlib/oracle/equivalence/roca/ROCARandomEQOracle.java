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

import java.util.Collection;
import java.util.Random;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.automatoncountervalues.ROCAFromDescription;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * Equivalence oracles for ROCAs based on a complete exploration that goes
 * further and further, repeated a random number of times.
 * 
 * It is assumed the hypothesis is constructed from a periodic description.
 * 
 * The oracle may not finish for a correct hypothesis. However, if the
 * hypothesis is incorrect, it is guaranteed to finish (but may not necessarily
 * return a counterexample).
 * 
 * @param <I> Alphabet type
 * @author Gaëtan Staquet
 */
public class ROCARandomEQOracle<I> implements EquivalenceOracle.ROCAEquivalenceOracle<I> {

    private final ROCA<?, I> reference;
    private final Alphabet<I> alphabet;
    private final Random random;

    public ROCARandomEQOracle(final ROCA<?, I> reference) {
        this(reference, reference.getAlphabet());
    }

    public ROCARandomEQOracle(final ROCA<?, I> reference, final Alphabet<I> alphabet) {
        this(reference, alphabet, new Random());
    }

    public ROCARandomEQOracle(final ROCA<?, I> reference, final Alphabet<I> alphabet, final Random rand) {
        this.reference = reference;
        this.alphabet = alphabet;
        this.random = rand;
    }

    @Override
    public @Nullable DefaultQuery<I, Boolean> findCounterExample(ROCAFromDescription<?, I> hypothesis,
            Collection<? extends I> inputs) {
        long maxCounterValue = (long) Math.pow(hypothesis.size() * reference.size(), 2);
        ROCARandomEQOracleExploration<I, ?, ?> exploration = new ROCARandomEQOracleExploration<>(reference, hypothesis,
                alphabet);
        boolean cont = true;
        while (cont) {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                return null;
            }
            Word<I> separator = exploration.findSeparatingWordByExploration(maxCounterValue);
            if (separator != null) {
                return new DefaultQuery<>(separator, reference.accepts(separator));
            }
            maxCounterValue += hypothesis.getPeriod();
            cont = random.nextBoolean();
        }
        return null;
    }
}
