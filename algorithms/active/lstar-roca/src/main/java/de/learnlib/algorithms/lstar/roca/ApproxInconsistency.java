package de.learnlib.algorithms.lstar.roca;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.learnlib.datastructure.observationtable.Inconsistency;
import net.automatalib.words.Word;

/**
 * Special inconsistency for {@link ObservationTableWithCounterValuesROCA} where
 * an inconsistency is defined by an approx identifier and a symbol.
 * 
 * The approx class and the symbol define an inconsistency when the successors'
 * classes have nothing in common. That is, the intersection of all Approx(va)
 * (with v such that Approx(v) = Approx(u)) is empty. In that case, we can find
 * new suffixes to add in the table.
 * 
 * @param <I> Alphabet input type
 * 
 * @author Gaëtan Staquet
 */
final class ApproxInconsistency<I> extends Inconsistency<I> {

    private final Set<Word<I>> suffixes = new HashSet<>();

    public ApproxInconsistency() {
        super(null, null, null);
    }

    public void addSuffix(Word<I> suffix) {
        suffixes.add(suffix);
    }

    public Set<Word<I>> getAllSuffixes() {
        return Collections.unmodifiableSet(suffixes);
    }

    public boolean isEmpty() {
        return suffixes.isEmpty();
    }
}
