/* Copyright (C) 2013-2021 TU Dortmund
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
package de.learnlib.api.algorithm;

import java.util.Iterator;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.automatoncountervalues.AcceptingOrExit;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.words.Word;

/**
 * Basic interface for a model inference algorithm.
 * <p>
 * Actively inferring models (such as DFAs or Mealy machines) consists of the construction of an initial hypothesis,
 * which is subsequently refined using counterexamples (see {@link EquivalenceOracle}).
 *
 * @param <M>
 *         model type
 * @param <I>
 *         input symbol type
 * @param <D>
 *         output domain type
 *
 * @author Maik Merten
 * @author Malte Isberner
 */
public interface LearningAlgorithm<M, I, D> {

    /**
     * Starts the model inference process, creating an initial hypothesis in the provided model object. Please note that
     * it should be illegal to invoke this method twice.
     */
    void startLearning();

    /**
     * Triggers a refinement of the model by providing a counterexample. A counterexample is a query which exposes
     * different behavior of the real SUL compared to the hypothesis. Please note that invoking this method before an
     * initial invocation of {@link #startLearning()} should be illegal.
     *
     * @param ceQuery
     *         the query which exposes diverging behavior, as posed to the real SUL (i.e. with the SULs output).
     *
     * @return {@code true} if the counterexample triggered a refinement of the hypothesis, {@code false} otherwise
     * (i.e., it was no counterexample).
     */
    boolean refineHypothesis(DefaultQuery<I, D> ceQuery);

    /**
     * Returns the current hypothesis model.
     * <p>
     * N.B.: By the contract of this interface, the model returned by this method may not be modified (i.e., M generally
     * should refer to an immutable interface), and its validity is retained only until the next invocation of {@link
     * #refineHypothesis(DefaultQuery)}. If older hypotheses have to be maintained, a copy of the returned model must be
     * made.
     * <p>
     * Please note that it should be illegal to invoke this method before an initial invocation of {@link
     * #startLearning()}.
     *
     * @return the current hypothesis model.
     */
    M getHypothesisModel();

    interface DFALearner<I> extends LearningAlgorithm<DFA<?, I>, I, Boolean> {}

    interface MealyLearner<I, O> extends LearningAlgorithm<MealyMachine<?, I, ?, O>, I, Word<O>> {}

    /**
     * Specialization of a learner for ROCAs.
     * 
     * Such a learner can yield multiple ROCAs each learning round.
     * Thus, the experiment must ask multiple equivalence queries (one by possible ROCA).
     * @author Gaëtan Staquet
     */
    interface ROCALearner<I> extends LearningAlgorithm<ROCA<?, I>, I, AcceptingOrExit> {
        /**
         * As an ROCA learner can construct multiple ROCAs each iteration, this function should not be called.
         * 
         * See {@link getHypothesisModels()}.
         */
        @Override
        default ROCA<?, I> getHypothesisModel() {
            throw new UnsupportedOperationException("Since LStar for ROCA can construct multiple ROCAs in a round, please use getHypothesisModels()");
        }

        /**
         * Gets every ROCA that can be constructed from the current knowledge such that the ROCAs are consistent with the knowledge.
         * 
         * In other words, it is guaranteed that the ROCA and the learner's knowledge agree on the representatives.
         * @return An iterator over ROCAs
         */
        Iterator<ROCA<?, I>> getHypothesisModels();

        /**
         * Gets the learnt DFA (the restricted automaton up to a counter value) as an ROCA.
         * @return An ROCA constructed from the learnt DFA
         */
        ROCA<?, I> getLearntDFAAsROCA();
    }
}
