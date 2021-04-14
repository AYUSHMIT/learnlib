package de.learnlib.algorithms.lstar.roca;

import java.util.List;

import de.learnlib.api.oracle.MembershipOracle.CounterValueOracle;
import de.learnlib.datastructure.observationtable.GenericObservationTableWithCounterValues;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.words.Alphabet;

/**
 * An implementation of a {@link GenericObservationTableWithCounterValues} for
 * {@link AutomatonWithCounterValues}.
 * 
 * That is, the observation table encodes a restricted automaton up to a counter
 * limit.
 * 
 * @author Gaëtan Staquet
 */
public class ObservationTableWithCounterValues<I> extends GenericObservationTableWithCounterValues<I, Boolean> {

    public ObservationTableWithCounterValues(Alphabet<I> alphabet, CounterValueOracle<I> counterValueOracle) {
        super(alphabet, counterValueOracle);
    }

    @Override
    protected boolean isBinRow(Row<I> row) {
        List<OutputAndCounterValue<Boolean>> rowContents = fullRowContents(row);
        for (int i = 0; i < numberOfSuffixes(); i++) {
            OutputAndCounterValue<Boolean> cell = rowContents.get(i);
            if (cell.getOutput() || cell.getCounterValue() != NO_COUNTER_VALUE) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean isAccepted(OutputAndCounterValue<Boolean> outputAndCounterValue) {
        return outputAndCounterValue.getOutput();
    }
}
