package de.learnlib.algorithms.lstar.roca;

import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.Row;

/**
 * A special kind of inconsistencies for {@link ObservationTableWithCounterValuesROCA}.
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
