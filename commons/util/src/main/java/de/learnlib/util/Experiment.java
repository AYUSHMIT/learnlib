/* Copyright (C) 2013-2021 TU Dortmund
 * (C) 2021 – University of Mons, University Antwerpen for inheriting the class AbstractExperiment.
 * This file is part of LearnLib, http://www.learnlib.de/.
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
package de.learnlib.util;

import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.logging.LearnLogger;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * runs a learning experiment.
 *
 * @param <A> the automaton type
 *
 * @author falkhowar
 */
public class Experiment<A extends Object> extends AbstractExperiment<A> {

    public static final String LEARNING_PROFILE_KEY = "Learning";
    public static final String COUNTEREXAMPLE_PROFILE_KEY = "Searching for counterexample";

    private static final LearnLogger LOGGER = LearnLogger.getLogger(Experiment.class);
    private final ExperimentImpl<?, ?> impl;

    public <I, D> Experiment(LearningAlgorithm<? extends A, I, D> learningAlgorithm,
                             EquivalenceOracle<? super A, I, D> equivalenceAlgorithm,
                             Alphabet<I> inputs) {
        this.impl = new ExperimentImpl<>(learningAlgorithm, equivalenceAlgorithm, inputs);
    }

    @Override
    protected A runInternal() {
        return impl.run();
    }

    private final class ExperimentImpl<I, D> {

        private final LearningAlgorithm<? extends A, I, D> learningAlgorithm;
        private final EquivalenceOracle<? super A, I, D> equivalenceAlgorithm;
        private final Alphabet<I> inputs;

        ExperimentImpl(LearningAlgorithm<? extends A, I, D> learningAlgorithm,
                       EquivalenceOracle<? super A, I, D> equivalenceAlgorithm,
                       Alphabet<I> inputs) {
            this.learningAlgorithm = learningAlgorithm;
            this.equivalenceAlgorithm = equivalenceAlgorithm;
            this.inputs = inputs;
        }

        public A run() {
            rounds.increment();
            LOGGER.logPhase("Starting round " + rounds.getCount());
            LOGGER.logPhase("Learning");

            profileStart(LEARNING_PROFILE_KEY);
            learningAlgorithm.startLearning();
            profileStop(LEARNING_PROFILE_KEY);

            while (true) {
                final A hyp = learningAlgorithm.getHypothesisModel();

                if (logModels) {
                    LOGGER.logModel(hyp);
                }

                LOGGER.logPhase("Searching for counterexample");

                profileStart(COUNTEREXAMPLE_PROFILE_KEY);
                DefaultQuery<I, D> ce = equivalenceAlgorithm.findCounterExample(hyp, inputs);
                profileStop(COUNTEREXAMPLE_PROFILE_KEY);

                if (ce == null) {
                    return hyp;
                }

                LOGGER.logCounterexample(ce.getInput().toString());

                // next round ...
                rounds.increment();
                LOGGER.logPhase("Starting round " + rounds.getCount());
                LOGGER.logPhase("Learning");

                profileStart(LEARNING_PROFILE_KEY);
                final boolean refined = learningAlgorithm.refineHypothesis(ce);
                profileStop(LEARNING_PROFILE_KEY);

                assert refined;
            }
        }
    }

    public static class DFAExperiment<I> extends Experiment<DFA<?, I>> {
        public DFAExperiment(LearningAlgorithm<? extends DFA<?, I>, I, Boolean> learningAlgorithm,
                             EquivalenceOracle<? super DFA<?, I>, I, Boolean> equivalenceAlgorithm,
                             Alphabet<I> inputs) {
            super(learningAlgorithm, equivalenceAlgorithm, inputs);
        }

    }

    public static class MealyExperiment<I, O> extends Experiment<MealyMachine<?, I, ?, O>> {

        public MealyExperiment(LearningAlgorithm<? extends MealyMachine<?, I, ?, O>, I, Word<O>> learningAlgorithm,
                               EquivalenceOracle<? super MealyMachine<?, I, ?, O>, I, Word<O>> equivalenceAlgorithm,
                               Alphabet<I> inputs) {
            super(learningAlgorithm, equivalenceAlgorithm, inputs);
        }

    }
}
