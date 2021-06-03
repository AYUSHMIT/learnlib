package de.learnlib.algorithms.lstar.roca;

import java.util.List;

import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.commons.smartcollections.ResizingArrayStorage;
import net.automatalib.words.Word;

/**
 * An implementation of a row for {@link ObservationTableWithCounterValuesROCA}.
 * 
 * @author Gaëtan Staquet
 */
class RowImpl<I> implements Row<I> {
    private final int rowId;
    private final ObservationTreeNode<I> node;

    private int canonicalId = -1;
    private int lpIndex;
    private ResizingArrayStorage<RowImpl<I>> successors;

    private List<ObservationTreeNode<I>> rowContents;

    RowImpl(int rowId, ObservationTreeNode<I> node) {
        this.rowId = rowId;
        this.node = node;
    }

    RowImpl(int rowId, ObservationTreeNode<I> node, int alphabetSize) {
        this(rowId, node);

        makeShort(alphabetSize);
    }

    @Override
    public int getRowId() {
        return rowId;
    }

    @Override
    public int getRowContentId() {
        return rowId;
    }

    public int getCanonicalId() {
        return canonicalId;
    }

    public ObservationTreeNode<I> getNode() {
        return node;
    }

    @Override
    public Word<I> getLabel() {
        return node.getPrefix();
    }

    @Override
    public boolean isShortPrefixRow() {
        return lpIndex == -1;
    }

    @Override
    public RowImpl<I> getSuccessor(int inputIdx) {
        return successors.array[inputIdx];
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

    void setCanonicalId(int canonicalId) {
        this.canonicalId = canonicalId;
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

    List<ObservationTreeNode<I>> getRowContents() {
        return rowContents;
    }

    void setRowContents(List<ObservationTreeNode<I>> contents) {
        this.rowContents = contents;
    }

    @Override
    public boolean hasContents() {
        return getRowContents() != null;
    }
}
