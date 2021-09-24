package de.learnlib.algorithms.lstar.roca;

import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.Row;

/**
 * A special kind of inconsistencies for
 * {@link ObservationTableWithCounterValuesROCA}.
 * 
 * A bottom inconsistency arises when there exists two prefixes u and v such
 * that u is in Approx(v) and there exists a suffix s with
 * {@code C_l(us) != UNKNOWN_COUNTER_VALUE} iff
 * {@code C_l(vs) = UNKNOWN_COUNTER_VALUE}. That is, u and v are "equivalent"
 * but they do not agree on the domain of the known counter values.
 * 
 * It is guaranteed that us is such that C_l(us) != -1 (i.e., us is in the
 * prefix of the table).
 * 
 * @author Gaëtan Staquet
 */
public class BottomInconsistency<I> extends Inconsistency<I> {

    private final int suffixIndex;

    public BottomInconsistency(Row<I> firstRow, Row<I> secondRow, int suffixIndex) {
        super(firstRow, secondRow, null);
        this.suffixIndex = suffixIndex;
    }

    public int getSuffixIndex() {
        return suffixIndex;
    }
}
