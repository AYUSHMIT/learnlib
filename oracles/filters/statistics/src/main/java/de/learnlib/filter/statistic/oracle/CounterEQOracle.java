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
package de.learnlib.filter.statistic.oracle;

import java.util.Collection;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.DFAEquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.MealyEquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.statistic.StatisticCollector;
import de.learnlib.buildtool.refinement.annotation.GenerateRefinement;
import de.learnlib.buildtool.refinement.annotation.Generic;
import de.learnlib.buildtool.refinement.annotation.Interface;
import de.learnlib.buildtool.refinement.annotation.Map;
import de.learnlib.filter.statistic.Counter;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.words.Word;

/**
 * Counts the number of asked equivalence queries.
 * 
 * @author Gaëtan Staquet
 */
// @formatter:off
@GenerateRefinement(name = "DFACounterEQOracle",
                    generics = "I",
                    parentGenerics = {@Generic(clazz = DFA.class, generics = {"?", "I"}), @Generic("I"), @Generic(clazz = Boolean.class)},
                    parameterMapping = @Map(from = EquivalenceOracle.class,
                                            to = DFAEquivalenceOracle.class,
                                            withGenerics = "I"),
                    interfaces = @Interface(clazz = DFAEquivalenceOracle.class, generics = "I"))
@GenerateRefinement(name = "MealyCounterEQOracle",
                    generics = {"I", "O"},
                    parentGenerics = {@Generic(clazz = MealyMachine.class, generics = {"?", "I", "?", "O"}), @Generic("I"), @Generic(clazz = Word.class, generics = "O")},
                    parameterMapping = @Map(from = EquivalenceOracle.class,
                                            to = MealyEquivalenceOracle.class,
                                            withGenerics = {"I", "O"}),
                    interfaces = @Interface(clazz = MealyEquivalenceOracle.class, generics = {"I", "O"}))
// @formatter:on
public class CounterEQOracle<A, I, D> implements EquivalenceOracle<A, I, D>, StatisticCollector {

    private final Counter counter;
    private EquivalenceOracle<A, I, D> equivalenceOracle;

    public CounterEQOracle(EquivalenceOracle<A, I, D> equivalenceOracle, String name) {
        this.equivalenceOracle = equivalenceOracle;
        this.counter = new Counter(name, "queries");
    }

    @Override
    public Counter getStatisticalData() {
        return counter;
    }

    @Override
    public @Nullable DefaultQuery<I, D> findCounterExample(A hypothesis, Collection<? extends I> inputs) {
        counter.increment();
        return getNextOracle().findCounterExample(hypothesis, inputs);
    }

    public Counter getCounter() {
        return this.counter;
    }

    public long getCount() {
        return counter.getCount();
    }

    public void setNext(EquivalenceOracle<A, I, D> next) {
        this.equivalenceOracle = next;
    }

    protected EquivalenceOracle<A, I, D> getNextOracle() {
        return equivalenceOracle;
    }
}
