package de.learnlib.examples.oca;

import java.io.IOException;

import de.learnlib.algorithms.lstar.roca.LStarROCA;
import de.learnlib.algorithms.lstar.roca.ROCAExperiment;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.SingleQueryOracle;
import de.learnlib.datastructure.observationtable.OTUtils;
import de.learnlib.filter.cache.roca.CounterValueHashCacheOracle;
import de.learnlib.filter.statistic.oracle.CounterValueCounterOracle;
import de.learnlib.filter.statistic.oracle.ROCACounterEQOracle;
import de.learnlib.filter.statistic.oracle.ROCACounterOracle;
import de.learnlib.oracle.equivalence.roca.ROCASimulatorEQOracle;
import de.learnlib.oracle.equivalence.roca.RestrictedAutomatonCounterEQOracle;
import de.learnlib.oracle.equivalence.roca.RestrictedAutomatonROCASimulatorEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle.ROCASimulatorOracle;
import de.learnlib.oracle.membership.roca.CounterValueOracle;
import de.learnlib.util.statistics.SimpleProfiler;
import net.automatalib.automata.oca.DefaultROCA;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.ROCALocation;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;

/**
 * Example of using {@link LStarROCA} to learn a simple ROCA.
 * 
 * @author Gaëtan Staquet
 */
public class ROCAExample {
    private ROCAExample() {
    }

    public static void main(String[] args) throws IOException {
        ROCA<?, Character> target = constructSUL();
        runExample(target, target.getAlphabet());
    }

    private static ROCA<?, Character> constructSUL() {
        // L = {a^n b^m | n is odd and m > n}
        Alphabet<Character> alphabet = Alphabets.characters('a', 'b');
        DefaultROCA<Character> roca = new DefaultROCA<>(alphabet);

        ROCALocation q0 = roca.addInitialLocation(false);
        ROCALocation q1 = roca.addLocation(false);
        ROCALocation q2 = roca.addLocation(true);

        roca.setSuccessor(q0, 0, 'a', +1, q1);
        roca.setSuccessor(q0, 1, 'a', +1, q1);

        roca.setSuccessor(q1, 1, 'a', +1, q0);
        roca.setSuccessor(q1, 1, 'b', 0, q2);

        roca.setSuccessor(q2, 0, 'b', 0, q2);
        roca.setSuccessor(q2, 1, 'b', -1, q2);

        return roca;
    }

    private static <I> void runExample(ROCA<?, I> target, Alphabet<I> alphabet) throws IOException {
        SingleQueryOracle.SingleQueryOracleROCA<I> sul = new ROCASimulatorOracle<>(target);
        ROCACounterOracle<I> membershipOracle = new ROCACounterOracle<>(sul, "membership queries");

        SingleQueryOracle.SingleQueryCounterValueOracle<I> counterValue = new CounterValueOracle<>(target);
        CounterValueHashCacheOracle<I> counterValueCache = new CounterValueHashCacheOracle<>(counterValue);
        CounterValueCounterOracle<I> counterValueOracle = new CounterValueCounterOracle<>(counterValueCache,
                "counter value queries");

        EquivalenceOracle.ROCAEquivalenceOracle<I> eqOracle = new ROCASimulatorEQOracle<>(target);
        ROCACounterEQOracle<I> equivalenceOracle = new ROCACounterEQOracle<>(eqOracle, "equivalence queries");

        RestrictedAutomatonROCASimulatorEQOracle<I> resEqOracle = new RestrictedAutomatonROCASimulatorEQOracle<>(target,
                alphabet);
        RestrictedAutomatonCounterEQOracle<I> restrictedEquivalenceOracle = new RestrictedAutomatonCounterEQOracle<>(
                resEqOracle, "partial equivalence queries");

        LStarROCA<I> lstar_roca = new LStarROCA<>(membershipOracle, counterValueOracle, restrictedEquivalenceOracle,
                alphabet);

        ROCAExperiment<I> experiment = new ROCAExperiment<>(lstar_roca, equivalenceOracle, alphabet);
        experiment.setLogModels(false);
        experiment.setProfile(true);

        experiment.run();

        ROCA<?, I> result = experiment.getFinalHypothesis();

        System.out.println("-------------------------------------------------------");

        // profiling
        System.out.println(SimpleProfiler.getResults());

        // learning statistics
        System.out.println(experiment.getRounds().getSummary());
        System.out.println(equivalenceOracle.getStatisticalData().getSummary());
        System.out.println(restrictedEquivalenceOracle.getStatisticalData().getSummary());
        System.out.println(membershipOracle.getStatisticalData().getSummary());
        System.out.println(counterValueOracle.getStatisticalData().getSummary());

        // model statistics
        System.out.println("States: " + result.size());
        System.out.println("Sigma: " + alphabet.size());

        // show model
        System.out.println();
        System.out.println("Model: ");
        GraphDOT.write(result, System.out); // may throw IOException!

        Visualization.visualize(result);

        System.out.println("-------------------------------------------------------");

        OTUtils.displayHTMLInBrowser(lstar_roca.getObservationTable().toClassicObservationTable());
    }
}
