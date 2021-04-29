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
 * See {@link Experiment}, {@link ROCAExperiment}, and {@link VCAExperiment} for
 * concrete implementations.
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
     * @return The learnt model
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
