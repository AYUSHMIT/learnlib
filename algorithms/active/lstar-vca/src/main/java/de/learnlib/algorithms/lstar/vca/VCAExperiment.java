package de.learnlib.algorithms.lstar.vca;

import static de.learnlib.algorithms.lstar.vca.VCALearningUtils.computeMaximalCounterValue;

import java.util.Collection;

import de.learnlib.api.algorithm.MultipleHypothesesLearningAlgorithm;
import de.learnlib.api.logging.LearnLogger;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.util.AbstractExperiment;
import net.automatalib.automata.oca.VCA;
import net.automatalib.words.VPDAlphabet;

/**
 * An experiment for learning a VCA.
 * 
 * See the unpublished paper "Learning Visibly One-Counter Automata" by D.
 * Neider and C. Löding (2010).
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
public class VCAExperiment<I> extends AbstractExperiment<VCA<?, I>> {

    public static final String LEARNING_VCA_PROFILE_KEY = "Learning VCA";
    public static final String COUNTEREXAMPLE_PROFILE_KEY = "Searching for counterexample";

    private static final LearnLogger LOGGER = LearnLogger.getLogger(VCAExperiment.class);

    private final MultipleHypothesesLearningAlgorithm.VCALearner<I> learningAlgorithm;
    private final EquivalenceOracle.VCAEquivalenceOracle<I> equivalenceOracle;
    private final VPDAlphabet<I> alphabet;

    public VCAExperiment(MultipleHypothesesLearningAlgorithm.VCALearner<I> learningAlgorithm,
            EquivalenceOracle.VCAEquivalenceOracle<I> equivalenceOracle, VPDAlphabet<I> inputs) {
        this.learningAlgorithm = learningAlgorithm;
        this.equivalenceOracle = equivalenceOracle;
        this.alphabet = inputs;
    }

    @Override
    protected VCA<?, I> runInternal() {
        rounds.increment();
        LOGGER.logPhase("Starting round " + rounds.getCount());
        LOGGER.logPhase("Learning");

        profileStart(LEARNING_VCA_PROFILE_KEY);
        learningAlgorithm.startLearning();
        profileStop(LEARNING_VCA_PROFILE_KEY);

        while (true) {
            final Collection<VCA<?, I>> hypotheses = learningAlgorithm.getHypothesisModels();

            LOGGER.logPhase("Searching for counterexample");
            DefaultQuery<I, Boolean> counterexample = null;
            for (VCA<?, I> hypothesis : hypotheses) {
                if (logModels) {
                    LOGGER.logModel(hypothesis);
                }

                profileStart(COUNTEREXAMPLE_PROFILE_KEY);
                DefaultQuery<I, Boolean> ce = equivalenceOracle.findCounterExample(hypothesis, alphabet);
                profileStop(COUNTEREXAMPLE_PROFILE_KEY);

                if (ce == null) {
                    return hypothesis;
                } else if (computeMaximalCounterValue(ce.getInput(), alphabet) > learningAlgorithm.getCounterLimit()) {
                    counterexample = ce;
                }
            }

            if (counterexample == null) {
                // If there was no VCAs in the previous loop, we directly take the learnt DFA
                // as an VCA.
                // Note that we are sure the learnt restricted automaton is not big enough to
                // contain the periodic part of the behavior graph.
                // Also, we are sure the counterexample is a useful one as the DFA is correct on
                // the restricted language.
                // There is however an exception to the last point: if the target language is
                // regular.
                // In that case, it is not guaranteed that an hypothesis could be created above.
                // Thus, we end up here and use the learnt DFA as an VCA.
                // Since the language is regular, the DFA accepts the correct language, and so
                // does the VCA.
                VCA<?, I> hypothesis = learningAlgorithm.getLearntDFAAsVCA();
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

            profileStart(LEARNING_VCA_PROFILE_KEY);
            final boolean refined = learningAlgorithm.refineHypothesis(counterexample);
            profileStop(LEARNING_VCA_PROFILE_KEY);

            assert refined;
        }
    }
}
