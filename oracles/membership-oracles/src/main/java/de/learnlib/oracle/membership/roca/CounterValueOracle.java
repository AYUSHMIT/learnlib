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
package de.learnlib.oracle.membership.roca;

import de.learnlib.api.oracle.SingleQueryOracle;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.words.Word;

/**
 * An oracle to query the counter value of a word, using a reference ROCA.
 * 
 * The oracle only guarantees a valid output if the provided word is in the
 * prefix of the language accepted by the ROCA.
 * 
 * @author Gaëtan Staquet
 */
public class CounterValueOracle<I> implements SingleQueryOracle.SingleQueryCounterValueOracle<I> {

    private final ROCA<?, I> roca;

    public CounterValueOracle(ROCA<?, I> roca) {
        this.roca = roca;
    }

    @Override
    public Integer answerQuery(Word<I> prefix, Word<I> suffix) {
        return roca.getCounterValue(prefix.concat(suffix));
    }

}
