package de.learnlib.examples.oca;

import java.io.IOException;

import de.learnlib.algorithms.lstar.vca.LStarVCA;
import de.learnlib.algorithms.lstar.vca.VCAExperiment;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.SingleQueryOracle;
import de.learnlib.datastructure.observationtable.OTUtils;
import de.learnlib.datastructure.observationtable.writer.StratifiedObservationTableASCIIWriter;
import de.learnlib.filter.statistic.oracle.ROCACounterOracle;
import de.learnlib.filter.statistic.oracle.vca.VCACounterEQOracle;
import de.learnlib.oracle.equivalence.roca.RestrictedAutomatonCounterEQOracle;
import de.learnlib.oracle.equivalence.vca.RestrictedAutomatonVCASimulatorEQOracle;
import de.learnlib.oracle.equivalence.vca.VCARandomEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle.ROCASimulatorOracle;
import de.learnlib.util.statistics.SimpleProfiler;
import net.automatalib.automata.oca.DefaultVCA;
import net.automatalib.automata.oca.VCA;
import net.automatalib.automata.oca.VCALocation;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.VPDAlphabet;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.words.impl.DefaultVPDAlphabet;

/**
 * An example learning a VCA using {@link LStarVCA}.
 * 
 * @author Gaëtan Staquet
 */
public class VCAExample {
    private VCAExample() {
    }

    public static void main(String[] args) throws IOException {
        VCA<?, Character> vca = constructSUL();
        runExample(vca, vca.getAlphabet());
    }

    private static VCA<?, Character> constructSUL() {
        // L = {a^n b^m c^n | n > 0 and m > 0}
        // a is a call symbol, b an internal symbol, and c a return symbol
        VPDAlphabet<Character> alphabet = new DefaultVPDAlphabet<>(Alphabets.singleton('b'), Alphabets.singleton('a'),
                Alphabets.singleton('c'));
        DefaultVCA<Character> vca = new DefaultVCA<>(1, alphabet);

        VCALocation q0 = vca.addInitialLocation(false);
        VCALocation q1 = vca.addLocation(false);
        VCALocation q2 = vca.addLocation(true);

        vca.setSuccessor(q0, 0, 'a', q0);
        vca.setSuccessor(q0, 1, 'a', q0);
        vca.setSuccessor(q0, 1, 'b', q1);

        vca.setSuccessor(q1, 1, 'b', q1);
        vca.setSuccessor(q1, 1, 'c', q2);

        vca.setSuccessor(q2, 1, 'c', q2);

        return vca;
    }

    private static <I> void runExample(VCA<?, I> target, VPDAlphabet<I> alphabet) throws IOException {
        SingleQueryOracle.SingleQueryOracleROCA<I> sul = new ROCASimulatorOracle<>(target);
        ROCACounterOracle<I> membershipOracle = new ROCACounterOracle<>(sul, "membership queries");

        EquivalenceOracle.VCAEquivalenceOracle<I> eqOracle = new VCARandomEQOracle<>(target);
        VCACounterEQOracle<I> equivalenceOracle = new VCACounterEQOracle<>(eqOracle, "equivalence queries");

        RestrictedAutomatonVCASimulatorEQOracle<I> resEqOracle = new RestrictedAutomatonVCASimulatorEQOracle<>(target,
                alphabet);
        RestrictedAutomatonCounterEQOracle<I> restrictedEquivalenceOracle = new RestrictedAutomatonCounterEQOracle<>(
                resEqOracle, "partial equivalence queries");

        LStarVCA<I> lstar_vca = new LStarVCA<>(membershipOracle, restrictedEquivalenceOracle, alphabet);

        VCAExperiment<I> experiment = new VCAExperiment<>(lstar_vca, equivalenceOracle, alphabet);
        experiment.setLogModels(false);
        experiment.setProfile(true);

        experiment.run();

        VCA<?, I> result = experiment.getFinalHypothesis();

        System.out.println("-------------------------------------------------------");

        // profiling
        System.out.println(SimpleProfiler.getResults());

        // learning statistics
        System.out.println(experiment.getRounds().getSummary());
        System.out.println(equivalenceOracle.getStatisticalData().getSummary());
        System.out.println(restrictedEquivalenceOracle.getStatisticalData().getSummary());
        System.out.println(membershipOracle.getStatisticalData().getSummary());

        // model statistics
        System.out.println("States: " + result.size());
        System.out.println("Sigma: " + alphabet.size());
        System.out.println("Number of transition functions: " + result.getNumberOfTransitionFunctions());

        // show model
        System.out.println();
        System.out.println("Model: ");
        GraphDOT.write(result, System.out); // may throw IOException!

        Visualization.visualize(result);

        System.out.println("-------------------------------------------------------");
        System.out.println("Final observation table:");
        new StratifiedObservationTableASCIIWriter<>().write(lstar_vca.getObservationTable(), System.out);

        OTUtils.displayHTMLInBrowser(lstar_vca.getObservationTable());
    }
}
