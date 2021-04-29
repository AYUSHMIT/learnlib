package de.learnlib.oracle.equivalence.vca;

import java.util.Collection;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.oca.VCA;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * An equivalence oracle, which checks an hypothesis against a reference VCA.
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
public final class VCASimulatorEQOracle<I> implements EquivalenceOracle.VCAEquivalenceOracle<I> {

    private final VCA<?, I> reference;
    private final Alphabet<I> alphabet;

    public VCASimulatorEQOracle(VCA<?, I> reference) {
        this(reference, reference.getAlphabet());
    }

    public VCASimulatorEQOracle(VCA<?, I> reference, Alphabet<I> alphabet) {
        this.reference = reference;
        this.alphabet = alphabet;
    }

    @Override
    public @Nullable DefaultQuery<I, Boolean> findCounterExample(VCA<?, I> hypothesis, Collection<? extends I> inputs) {
        Word<I> separator = OCAUtil.findSeparatingWord(hypothesis, reference, alphabet);
        if (separator == null) {
            return null;
        }
        return new DefaultQuery<>(separator, reference.accepts(separator));
    }

}
