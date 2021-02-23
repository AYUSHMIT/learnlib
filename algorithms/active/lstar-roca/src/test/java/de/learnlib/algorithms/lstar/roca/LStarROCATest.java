package de.learnlib.algorithms.lstar.roca;

import org.testng.annotations.Test;

import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.oracle.EquivalenceOracle.ROCAEquivalenceOracle;
import de.learnlib.api.oracle.EquivalenceOracle.RestrictedAutomatonEquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle.RestrictedAutomatonMembershipOracle;
import de.learnlib.examples.dfa.ExamplePaulAndMary;
import de.learnlib.examples.roca.ExampleTinyROCA;
import de.learnlib.oracle.equivalence.roca.ROCASimulatorEQOracle;
import de.learnlib.oracle.equivalence.roca.RestrictedAutomatonSimulatorEQOracle;
import de.learnlib.oracle.membership.roca.RestrictedAutomatonSimulatorOracle;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Symbol;

public class LStarROCATest {
    private <I> void launch(ROCA<?, I> roca, Alphabet<I> alphabet) {
        RestrictedAutomatonMembershipOracle<I> mOracle = new RestrictedAutomatonSimulatorOracle<>(roca);
        ROCAEquivalenceOracle<I> rocaEqOracle = new ROCASimulatorEQOracle<>(roca);
        RestrictedAutomatonEquivalenceOracle<I> restrictedEqOracle = new RestrictedAutomatonSimulatorEQOracle<>(roca,
                alphabet);

        LearningAlgorithm.ROCALearner<I> learner = new LStarROCA<>(mOracle, restrictedEqOracle, alphabet);

        LearningTest.testLearnROCA(roca, alphabet, learner, rocaEqOracle);
    }

    @Test
    public void testLearningRegularLanguage() {
        ExamplePaulAndMary example = ExamplePaulAndMary.createExample();
        Alphabet<Symbol> alphabet = example.getAlphabet();
        DFA<?, Symbol> dfa = example.getReferenceAutomaton();
        ROCA<?, Symbol> roca = OCAUtil.constructROCAFromDFA(dfa, alphabet);

        launch(roca, alphabet);
    }

    @Test
    public void testLearningTinyROCA() {
        Alphabet<Character> alphabet = ExampleTinyROCA.getAlphabet();
        ROCA<?, Character> roca = ExampleTinyROCA.constructMachine();

        launch(roca, alphabet);
    }
}