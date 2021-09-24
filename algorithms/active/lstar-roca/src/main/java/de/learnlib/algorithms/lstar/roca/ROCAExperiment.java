package de.learnlib.algorithms.lstar.roca;

import java.util.Collection;

import de.learnlib.api.algorithm.MultipleHypothesesLearningAlgorithm;
import de.learnlib.api.logging.LearnLogger;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.util.AbstractExperiment;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.words.Alphabet;

/**
 * An experiment for learning ROCAs.
 * 
 * It is guaranteed that the counterexample picked among the counterexamples
 * returned by the equivalence queries is an actual counterexample for the
 * learner's known information.
 * 
 * @author Gaëtan Staquet
 */
public final class ROCAExperiment<I> extends AbstractExperiment<ROCA<?, I>> {

    public static final String LEARNING_ROCA_PROFILE_KEY = "Learning ROCA";
    public static final String COUNTEREXAMPLE_PROFILE_KEY = "Searching for counterexample ROCA";

    private static final LearnLogger LOGGER = LearnLogger.getLogger(ROCAExperiment.class);

    private final MultipleHypothesesLearningAlgorithm.ROCALearner<I> learningAlgorithm;
    private final EquivalenceOracle.ROCAEquivalenceOracle<I> equivalenceOracle;
    private final Alphabet<I> alphabet;

    public ROCAExperiment(MultipleHypothesesLearningAlgorithm.ROCALearner<I> learningAlgorithm,
            EquivalenceOracle.ROCAEquivalenceOracle<I> equivalenceOracle, Alphabet<I> inputs) {
        this.learningAlgorithm = learningAlgorithm;
        this.equivalenceOracle = equivalenceOracle;
        this.alphabet = inputs;
    }

    @Override
    protected ROCA<?, I> runInternal() {
        rounds.increment();
        LOGGER.logPhase("Starting round " + rounds.getCount());
        LOGGER.logPhase("Learning");

        profileStart(LEARNING_ROCA_PROFILE_KEY);
        learningAlgorithm.startLearning();
        profileStop(LEARNING_ROCA_PROFILE_KEY);

        while (true) {
            if (Thread.interrupted()) {
                return null;
            }

            final Collection<ROCA<?, I>> hypotheses = learningAlgorithm.getHypothesisModels();

            LOGGER.logPhase("Searching for counterexample");
            DefaultQuery<I, Boolean> counterexample = null;
            for (ROCA<?, I> hypothesis : hypotheses) {
                if (logModels) {
                    LOGGER.logModel(hypothesis);
                }

                profileStart(COUNTEREXAMPLE_PROFILE_KEY);
                DefaultQuery<I, Boolean> ce = equivalenceOracle.findCounterExample(hypothesis, alphabet);
                profileStop(COUNTEREXAMPLE_PROFILE_KEY);

                if (ce == null) {
                    return hypothesis;
                } else if (!hypothesis.accepts(ce.getInput())
                        && learningAlgorithm.isCounterexample(ce.getInput(), ce.getOutput())) {
                    // Since we want the output of the counterexample to be true and to be an actual
                    // counterexample for the learner, we have to discard some counterexamples.
                    // Indeed, it may happen than a word is a counterexample for an ROCA but not for
                    // the learner's knowledge as the ROCAs construction process does not
                    // necessarily take into account the whole table.
                    counterexample = ce;
                }
            }

            if (counterexample == null) {
                // If there was no ROCAs in the previous loop, we directly take the learned DFA
                // as an ROCA.
                // Note that we are sure the learned restricted automaton is not big enough to
                // contain the periodic part of the behavior graph.
                // Also, we are sure the counterexample is a useful one as the DFA is correct on
                // the restricted language.
                // There is however an exception to the last point: if the target language is
                // regular.
                // In that case, it is not guaranteed that an hypothesis could be created above.
                // Thus, we end up here and use the learned DFA as an ROCA.
                // Since the language is regular, the DFA accepts the correct language, and so
                // does the ROCA.
                ROCA<?, I> hypothesis = learningAlgorithm.getlearnedDFAAsROCA();
                profileStart(COUNTEREXAMPLE_PROFILE_KEY);
                counterexample = equivalenceOracle.findCounterExample(hypothesis, alphabet);
                profileStop(COUNTEREXAMPLE_PROFILE_KEY);
                if (counterexample == null) {
                    return hypothesis;
                }
            }

            LOGGER.logCounterexample(counterexample.getInput().toString());

            if (Thread.interrupted()) {
                return null;
            }

            // Next round
            rounds.increment();
            LOGGER.logPhase("Starting round " + rounds.getCount());
            LOGGER.logPhase("Learning");

            profileStart(LEARNING_ROCA_PROFILE_KEY);
            final boolean refined = learningAlgorithm.refineHypothesis(counterexample);
            profileStop(LEARNING_ROCA_PROFILE_KEY);

            assert refined;
        }
    }
}
