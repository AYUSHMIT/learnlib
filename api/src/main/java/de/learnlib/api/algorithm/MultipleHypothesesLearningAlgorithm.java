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
package de.learnlib.api.algorithm;

import java.util.Collection;

import net.automatalib.automata.oca.automatoncountervalues.ROCAFromDescription;
import net.automatalib.automata.oca.automatoncountervalues.VCAFromDescription;
import net.automatalib.words.Word;

/**
 * Basic interface for a model inference algorithm that can output multiple
 * hypotheses each round.
 * 
 * The experiment using such a learner is expected to test each hypothesis and
 * choose a counterexample if needed.
 * 
 * @param <M> Model type
 * @param <I> Input symbol type
 * @param <D> Output symbol type
 * 
 * @author Gaëtan Staquet
 */
public interface MultipleHypothesesLearningAlgorithm<M, I, D> extends LearningAlgorithm<M, I, D> {
    /**
     * As an ROCA learner can construct multiple ROCAs each iteration, this function
     * should not be called.
     * 
     * See {@link getHypothesisModels()}.
     */
    @Override
    default M getHypothesisModel() {
        throw new UnsupportedOperationException(
                "Since the learning algorithm can construct multiple hypotheses in a round, please use getHypothesisModels()");
    }

    /**
     * Gets every model that can be constructed from the current knowledge.
     * 
     * @return A collection of models
     */
    Collection<M> getHypothesisModels();

    /**
     * Specialization of a learner for ROCAs.
     * 
     * @param <I> Input alphabet type
     */
    interface ROCALearner<I> extends MultipleHypothesesLearningAlgorithm<ROCAFromDescription<?, I>, I, Boolean> {
        /**
         * Gets the learned DFA (the restricted automaton up to a counter value) as an
         * ROCA.
         * 
         * @return An ROCA constructed from the learned DFA
         */
        ROCAFromDescription<?, I> getLearnedDFAAsROCA();

        @Override
        Collection<ROCAFromDescription<?, I>> getHypothesisModels();

        int getCounterLimit();

        /**
         * Determines whether the given word is a counterexample.
         * 
         * @param word   The word
         * @param output Whether the word should be accepted or rejected
         * @return True iff word is a counterexample
         */
        boolean isCounterexample(Word<I> word, boolean output);
    }

    /**
     * Specialization of a learner for VCAs.
     * 
     * @param <I> Input alphabet type
     */
    interface VCALearner<I> extends MultipleHypothesesLearningAlgorithm<VCAFromDescription<?, I>, I, Boolean> {
        /**
         * Gets the learned DFA (the restricted automaton up to a counter value) as a
         * VCA.
         * 
         * @return A VCA constructed from the learned DFA
         */
        VCAFromDescription<?, I> getLearnedDFAAsVCA();

        int getCounterLimit();
    }
}
