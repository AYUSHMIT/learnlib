package de.learnlib.oracle.equivalence.roca;

import java.util.Collection;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.automatoncountervalues.AutomatonWithCounterValues;
import net.automatalib.util.automata.Automata;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * An equivalence oracle for an {@link AutomatonWithCounterValues}.
 * 
 * Since such an automaton is constructed from a ROCA for a given maximal
 * counter value, that counter value limit must be explicitly increased by the
 * experiment after each round.
 * 
 * @author Gaëtan Staquet
 */
public final class AutomatonCounterValuesSimulatorEQOracle<I>
        implements EquivalenceOracle<AutomatonWithCounterValues<?, I>, I, Boolean> {

    private final ROCA<?, I> roca;
    private final Alphabet<I> alphabet;
    private AutomatonWithCounterValues<?, I> reference;
    private int maxCounterValue = 0;

    public AutomatonCounterValuesSimulatorEQOracle(final ROCA<?, I> reference, final Alphabet<I> alphabet) {
        this.roca = reference;
        this.alphabet = alphabet;
    }

    public AutomatonCounterValuesSimulatorEQOracle(final ROCA<?, I> reference, final Alphabet<I> alphabet,
            final int maxCounterValue) {
        this(reference, alphabet);
        this.maxCounterValue = maxCounterValue;
    }

    public void incrementMaxCounterValue() {
        maxCounterValue++;
    }

    public void setMaxCounterValue(int maxCounterValue) {
        this.maxCounterValue = maxCounterValue;
        reference = OCAUtil.constructRestrictedAutomaton(roca, maxCounterValue);
    }

    @Override
    public @Nullable DefaultQuery<I, Boolean> findCounterExample(AutomatonWithCounterValues<?, I> hypothesis,
            Collection<? extends I> inputs) {
        Word<I> separator = Automata.findSeparatingWord(hypothesis, reference, alphabet);

        if (separator == null) {
            return null;
        }

        return new DefaultQuery<>(separator, reference.accepts(separator));
    }

}
