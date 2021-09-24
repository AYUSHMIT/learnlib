package de.learnlib.oracle.equivalence.vca;

import java.util.Collection;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.oca.VCA;
import net.automatalib.util.automata.Automata;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * An equivalence oracle for a DFA learned from a restricted automaton
 * constructed from a VCA.
 * 
 * Since such an automaton is constructed from a VCA for a given maximal counter
 * value, that counter value limit must be explicitly increased by the
 * experiment each round.
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
public class RestrictedAutomatonVCASimulatorEQOracle<I>
        implements EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> {

    private final VCA<?, I> roca;
    private final Alphabet<I> alphabet;
    private DFA<?, I> reference;

    public RestrictedAutomatonVCASimulatorEQOracle(final VCA<?, I> reference, final Alphabet<I> alphabet) {
        this(reference, alphabet, 0);
    }

    public RestrictedAutomatonVCASimulatorEQOracle(final VCA<?, I> reference, final Alphabet<I> alphabet,
            final int counterLimit) {
        this.roca = reference;
        this.alphabet = alphabet;
        setCounterLimit(counterLimit);
    }

    @Override
    public void setCounterLimit(int counterLimit) {
        reference = OCAUtil.constructRestrictedAutomaton(roca, counterLimit);
        reference = DFAs.complete(reference, alphabet);
    }

    @Override
    public @Nullable DefaultQuery<I, Boolean> findCounterExample(DFA<?, I> hypothesis, Collection<? extends I> inputs) {
        hypothesis = DFAs.complete(hypothesis, alphabet);
        Word<I> separator = Automata.findSeparatingWord(reference, hypothesis, alphabet);

        if (separator == null) {
            return null;
        }

        return new DefaultQuery<>(separator, reference.accepts(separator));
    }
}
