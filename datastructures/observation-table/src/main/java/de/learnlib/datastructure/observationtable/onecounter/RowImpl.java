package de.learnlib.datastructure.observationtable.onecounter;

import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.commons.smartcollections.ResizingArrayStorage;
import net.automatalib.words.Word;

/**
 * Implementation of a row where the label of the row has an associated counter value.
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
final class RowImpl<I> implements Row<I> {
    private final Word<I> label;
    private final int rowId;
    private final int counterValue;

    private int rowContentId = -1;
    private int lpIndex;
    private ResizingArrayStorage<RowImpl<I>> successors;

    RowImpl(Word<I> label, int rowId, int counterValue) {
        this.label = label;
        this.rowId = rowId;
        this.counterValue = counterValue;
    }

    RowImpl(Word<I> label, int rowId, int counterValue, int alphabetSize) {
        this(label, rowId, counterValue);

        makeShort(alphabetSize);
    }

    @Override
    public int getRowId() {
        return rowId;
    }

    @Override
    public int getRowContentId() {
        return rowContentId;
    }

    @Override
    public Word<I> getLabel() {
        return label;
    }

    @Override
    public boolean isShortPrefixRow() {
        return lpIndex == -1;
    }

    @Override
    public Row<I> getSuccessor(int inputIdx) {
        return successors.array[inputIdx];
    }

    int getCounterValue() {
        return counterValue;
    }

    void makeShort(int initialAlphabetSize) {
        if (lpIndex == -1) {
            return;
        }
        lpIndex = -1;
        successors = new ResizingArrayStorage<>(RowImpl.class, initialAlphabetSize);
    }

    void setSuccessor(int inputIdx, RowImpl<I> successor) {
        successors.array[inputIdx] = successor;
    }

    void setRowContentId(int id) {
        rowContentId = id;
    }

    boolean hasContents() {
        return rowContentId != -1;
    }

    void setLpIndex(int lpIndex) {
        this.lpIndex = lpIndex;
    }

    int getLpIndex() {
        return lpIndex;
    }

    void ensureInputCapacity(int capacity) {
        successors.ensureCapacity(capacity);
    }
}
