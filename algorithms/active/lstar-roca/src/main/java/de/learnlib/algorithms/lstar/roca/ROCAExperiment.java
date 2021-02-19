package de.learnlib.algorithms.lstar.roca;

import java.util.Iterator;

import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.logging.LearnLogger;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.statistic.Counter;
import de.learnlib.util.statistics.SimpleProfiler;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.automatoncountervalues.AcceptingOrExit;
import net.automatalib.words.Alphabet;

/**
 * An experiment for ROCAs.
 * 
 * @author Gaëtan Staquet
 */
public final class ROCAExperiment<I> {

    public static final String LEARNING_ROCA_PROFILE_KEY = "Learning ROCA";
    public static final String COUNTEREXAMPLE_PROFILE_KEY = "Searching for counterexample";

    private static final LearnLogger LOGGER = LearnLogger.getLogger(ROCAExperiment.class);
    private boolean logModels;
    private boolean profile;
    private final Counter rounds = new Counter("learning rounds", "#");

    private final LearningAlgorithm.ROCALearner<I> learningAlgorithm;
    private final EquivalenceOracle.ROCAEquivalenceOracle<I> equivalenceOracle;
    private final Alphabet<I> alphabet;

    private ROCA<?, I> finalHypothesis;

    public ROCAExperiment(LearningAlgorithm.ROCALearner<I> learningAlgorithm,
            EquivalenceOracle.ROCAEquivalenceOracle<I> equivalenceOracle, Alphabet<I> inputs) {
        this.learningAlgorithm = learningAlgorithm;
        this.equivalenceOracle = equivalenceOracle;
        this.alphabet = inputs;
    }

    public ROCA<?, I> run() {
        if (finalHypothesis != null) {
            throw new IllegalStateException("Experiment has already been run");
        }

        finalHypothesis = learn();
        return finalHypothesis;
    }

    public ROCA<?, I> getFinalHypothesis() {
        if (finalHypothesis == null) {
            throw new IllegalStateException("Experiment has not yet been run");
        }
        return finalHypothesis;
    }

    private void profileStart(String taskName) {
        if (profile) {
            SimpleProfiler.start(taskName);
        }
    }

    private void profileStop(String taskName) {
        if (profile) {
            SimpleProfiler.stop(taskName);
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

    private ROCA<?, I> learn() {
        rounds.increment();
        LOGGER.logPhase("Starting round " + rounds.getCount());
        LOGGER.logPhase("Learning");

        profileStart(LEARNING_ROCA_PROFILE_KEY);
        learningAlgorithm.startLearning();
        profileStop(LEARNING_ROCA_PROFILE_KEY);

        while (true) {
            final Iterator<ROCA<?, I>> hypotheses = learningAlgorithm.getHypothesisModels();
            System.out.println(hypotheses.hasNext());

            LOGGER.logPhase("Searching for counterexample");
            DefaultQuery<I, Boolean> counterexample = null;
            while (hypotheses.hasNext()) {
                ROCA<?, I> hypothesis = hypotheses.next();
                System.out.println(hypothesis);
                if (logModels) {
                    LOGGER.logModel(hypothesis);
                }

                profileStart(COUNTEREXAMPLE_PROFILE_KEY);
                DefaultQuery<I, Boolean> ce = equivalenceOracle.findCounterExample(hypothesis, alphabet);
                profileStop(COUNTEREXAMPLE_PROFILE_KEY);

                if (ce == null) {
                    return hypothesis;
                }
                counterexample = ce;
            }

            if (counterexample == null) {
                // If none of the ROCAs was useful (or if there was no ROCAs at all), we
                // directly take the learnt DFA as an ROCA.
                // Note that we are sure the learnt restricted automaton is not big enough to
                // contain the periodic part of the behavior graph.
                // Also, we are sure the counterexample is a useful one as the DFA is correct on
                // the restricted language.
                // There is however an exception to the last point: if the target language is regular.
                // In that case, it is not guaranteed that an hypothesis could be created above.
                // Thus, we end up here and use the learnt DFA as an ROCA.
                // Since the language is regular, the DFA accepts the correct language, and so does the ROCA.
                ROCA<?, I> hypothesis = learningAlgorithm.getLearntDFAAsROCA();
                profileStart(COUNTEREXAMPLE_PROFILE_KEY);
                counterexample = equivalenceOracle.findCounterExample(hypothesis, alphabet);
                profileStop(COUNTEREXAMPLE_PROFILE_KEY);
                if (counterexample == null) {
                    return hypothesis;
                }
            }

            LOGGER.logCounterexample(counterexample.getInput().toString());

            // Next round
            rounds.increment();
            LOGGER.logPhase("Starting round " + rounds.getCount());
            LOGGER.logPhase("Learning");

            // We convert the Boolean to AcceptingOrExit
            DefaultQuery<I, AcceptingOrExit> ce = new DefaultQuery<>(counterexample.getPrefix(),
                    counterexample.getSuffix(), booleanToAcceptingOrExit(counterexample.getOutput()));

            profileStart(LEARNING_ROCA_PROFILE_KEY);
            final boolean refined = learningAlgorithm.refineHypothesis(ce);
            profileStop(LEARNING_ROCA_PROFILE_KEY);

            assert refined;
        }
    }

    private AcceptingOrExit booleanToAcceptingOrExit(boolean accepting) {
        if (accepting) {
            return AcceptingOrExit.ACCEPTING;
        } else {
            return AcceptingOrExit.REJECTING;
        }
    }
}
