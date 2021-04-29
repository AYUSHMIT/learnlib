package de.learnlib.algorithms.lstar.roca;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.MembershipOracle.CounterValueOracle;
import de.learnlib.datastructure.observationtable.AbstractObservationTableWithCounterValues;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.automatoncountervalues.AutomatonWithCounterValues;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * An implementation of a {@link AbstractObservationTableWithCounterValues} for
 * {@link AutomatonWithCounterValues} and outputting {@link ROCA}s.
 * 
 * The observation table encodes a restricted automaton up to a counter limit
 * such that it is possible to construct {@link ROCA}s from the stored
 * knowledge.
 * 
 * @author Gaëtan Staquet
 */
public class ObservationTableWithCounterValuesROCA<I> extends AbstractObservationTableWithCounterValues<I, Boolean> {

    private final MembershipOracle.CounterValueOracle<I> counterValueOracle;
    // TODO: use a trie
    private final Set<Word<I>> prefixesOfL = new HashSet<>();

    public ObservationTableWithCounterValuesROCA(Alphabet<I> alphabet, CounterValueOracle<I> counterValueOracle) {
        super(alphabet);
        this.counterValueOracle = counterValueOracle;
    }

    @Override
    protected boolean isBinContents(List<OutputAndCounterValue<Boolean>> contents) {
        for (int i = 0; i < numberOfSuffixes(); i++) {
            OutputAndCounterValue<Boolean> cell = contents.get(i);
            if (cell.getOutput() || !Objects.equals(cell.getCounterValue(), NO_COUNTER_VALUE)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean isAccepted(OutputAndCounterValue<Boolean> outputAndCounterValue) {
        return outputAndCounterValue.getOutput();
    }

    @Override
    protected int getCounterValue(Word<I> prefix, Word<I> suffix) {
        return counterValueOracle.answerQuery(prefix, suffix);
    }

    @Override
    protected boolean requiresSecondPassForCounterValues() {
        return true;
    }

    @Override
    protected boolean canHaveCounterValue(Word<I> prefix, Word<I> suffix, int counterLimit) {
        return prefixesOfL.contains(prefix.concat(suffix));
    }

    @Override
    protected void processRowInternal(Row<I> row, List<OutputAndCounterValue<Boolean>> rowContents) {
        // We update the set of prefixes of L, according to the table
        for (int i = 0; i < numberOfSuffixes(); i++) {
            if (isAccepted(rowContents.get(i))) {
                Word<I> word = row.getLabel().concat(getSuffix(i));
                for (Word<I> prefix : word.prefixes(false)) {
                    prefixesOfL.add(prefix);
                }
            }
        }
    }
}
