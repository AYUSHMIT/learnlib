package de.learnlib.algorithms.lstar.vca;

import static de.learnlib.algorithms.lstar.vca.VCALearningUtils.computeCounterValue;
import static de.learnlib.algorithms.lstar.vca.VCALearningUtils.computeMaximalCounterValue;

import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.datastructure.observationtable.AbstractObservationTableWithCounterValues;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.words.VPDAlphabet;
import net.automatalib.words.Word;

/**
 * Implementation of an observation table storing counter values, for VCA
 * learning.
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
public class ObservationTableWithCounterValuesVCA<I> extends AbstractObservationTableWithCounterValues<I, Boolean> {

    private final VPDAlphabet<I> alphabet;

    public ObservationTableWithCounterValuesVCA(VPDAlphabet<I> alphabet) {
        super(alphabet);
        this.alphabet = alphabet;
    }

    @Override
    protected boolean isBinContents(List<OutputAndCounterValue<Boolean>> contents) {
        return false;
    }

    @Override
    protected boolean isAccepted(OutputAndCounterValue<Boolean> outputAndCounterValue) {
        return outputAndCounterValue.getOutput();
    }

    @Override
    protected int getCounterValue(Word<I> prefix, Word<I> suffix) {
        return computeCounterValue(prefix.concat(suffix), alphabet);
    }

    @Override
    protected boolean requiresSecondPassForCounterValues() {
        return false;
    }

    @Override
    protected boolean canHaveCounterValue(Word<I> prefix, Word<I> suffix, int counterLimit) {
        int maximalCounterValue = computeMaximalCounterValue(prefix.concat(suffix), alphabet);
        return maximalCounterValue <= counterLimit;
    }

    @Override
    protected void processRowInternal(Row<I> row, List<OutputAndCounterValue<Boolean>> rowContents) {
    }

    @Override
    protected @Nullable Integer getClassId(Row<I> row) {
        for (Row<I> spRow : getShortPrefixRows()) {
            if (row.getRowContentId() == spRow.getRowContentId()) {
                return spRow.getRowContentId();
            }
        }

        return null;
    }

}
