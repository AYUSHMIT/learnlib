package de.learnlib.oracle.equivalence.roca;

import java.util.Collection;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * An equivalence oracle, which checks hypothesis automata against an ROCA.
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
public final class ROCASimulatorEQOracle<I> implements EquivalenceOracle.ROCAEquivalenceOracle<I> {

    private final ROCA<?, I> reference;
    private final Alphabet<I> alphabet;

    public ROCASimulatorEQOracle(final ROCA<?, I> reference) {
        this(reference, reference.getAlphabet());
    }

    public ROCASimulatorEQOracle(final ROCA<?, I> reference, final Alphabet<I> alphabet) {
        this.reference = reference;
        this.alphabet = alphabet;
    }

    @Override
    public @Nullable DefaultQuery<I, Boolean> findCounterExample(ROCA<?, I> hypothesis,
            Collection<? extends I> inputs) {
        Word<I> separator = OCAUtil.findSeparatingWord(hypothesis, reference, alphabet);
        if (separator == null) {
            return null;
        }
        return new DefaultQuery<>(separator, reference.accepts(separator));
    }

}
