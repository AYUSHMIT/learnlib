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
package de.learnlib.util;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.filter.statistic.Counter;
import de.learnlib.util.statistics.SimpleProfiler;

/**
 * Abstract base class for experiments.
 * 
 * An experiment takes a learning algorithm and equivalence oracle(s) and
 * produces a model satisfying the oracle(s).
 * 
 * See {@link Experiment} for a possible concrete implementation.
 * 
 * @param <A> The type of the model to learn
 * @author Gaëtan Staquet
 */
public abstract class AbstractExperiment<A extends Object> {
    protected final Counter rounds = new Counter("learning rounds", "#");
    protected boolean logModels;
    protected boolean profile;

    protected @Nullable A finalHypothesis;

    public A getFinalHypothesis() {
        if (finalHypothesis == null) {
            throw new IllegalStateException("Experiment has not yet been run");
        }

        return finalHypothesis;
    }

    public A run() {
        if (this.finalHypothesis != null) {
            throw new IllegalStateException("Experiment has already been run");
        }

        finalHypothesis = runInternal();
        return finalHypothesis;
    }

    /**
     * Actually run the learner. It is guaranteed that the learning algorithm has
     * not yet been ran.
     * 
     * @return The learned model
     */
    protected abstract A runInternal();

    protected void profileStart(String taskname) {
        if (profile) {
            SimpleProfiler.start(taskname);
        }
    }

    protected void profileStop(String taskname) {
        if (profile) {
            SimpleProfiler.stop(taskname);
        }
    }

    /**
     * @param logModels flag whether models should be logged
     */
    public void setLogModels(boolean logModels) {
        this.logModels = logModels;
    }

    /**
     * @param profile flag whether learning process should be profiled
     */
    public void setProfile(boolean profile) {
        this.profile = profile;
    }

    /**
     * @return the rounds
     */
    public Counter getRounds() {
        return rounds;
    }
}
