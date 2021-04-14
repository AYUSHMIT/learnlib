package de.learnlib.oracle.equivalence.roca;

import java.util.Collection;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.util.automata.Automata;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * An equivalence oracle for a DFA learnt from a restricted automaton constructed from an ROCA.
 * 
 * Since such an automaton is constructed from a ROCA for a given maximal
 * counter value, that counter value limit must be explicitly increased by the
 * experiment after each round.
 * 
 * @author Gaëtan Staquet
 */
public final class RestrictedAutomatonSimulatorEQOracle<I>
        implements EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> {

    private final ROCA<?, I> roca;
    private final Alphabet<I> alphabet;
    private DFA<?, I> reference;
    private int counterLimit;

    public RestrictedAutomatonSimulatorEQOracle(final ROCA<?, I> reference, final Alphabet<I> alphabet) {
        this(reference, alphabet, 0);
    }

    public RestrictedAutomatonSimulatorEQOracle(final ROCA<?, I> reference, final Alphabet<I> alphabet,
            final int counterLimit) {
        this.roca = reference;
        this.alphabet = alphabet;
        setCounterLimit(counterLimit);
    }

    @Override
    public void setCounterLimit(int counterLimit) {
        this.counterLimit = counterLimit;
        reference = OCAUtil.constructRestrictedAutomaton(roca, counterLimit).toSimpleDFA();
        reference = DFAs.complete(reference, alphabet);
    }

    @Override
    public int getCounterLimit() {
        return counterLimit;
    }

    @Override
    public @Nullable DefaultQuery<I, Boolean> findCounterExample(DFA<?, I> hypothesis,
            Collection<? extends I> inputs) {
        hypothesis = DFAs.complete(hypothesis, alphabet);
        Word<I> separator = Automata.findSeparatingWord(reference, hypothesis, alphabet);

        if (separator == null) {
            return null;
        }

        return new DefaultQuery<>(separator, reference.accepts(separator));
    }

}
