package de.learnlib.algorithms.lstar.roca;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.commons.smartcollections.ResizingArrayStorage;
import net.automatalib.words.Word;

/**
 * An implementation of a row for {@link ObservationTableWithCounterValuesROCA}.
 * 
 * A row has a list of {@link ObservationTreeNode}s that are used to store the
 * row contents.
 * 
 * @author Gaëtan Staquet
 */
class RowImpl<I> implements Row<I> {
    private final int rowId;
    private final ObservationTreeNode<I> node;

    private int approxId = -1;
    private int lpIndex;
    private int sameOutputsId = -1;
    private ResizingArrayStorage<RowImpl<I>> successors;

    private final List<ObservationTreeNode<I>> nodes;

    private final ObservationTableWithCounterValuesROCA<I> table;

    private List<Boolean> outputs;
    private List<PairCounterValueOutput<Boolean>> cvOutputs;

    RowImpl(int rowId, ObservationTreeNode<I> node, ObservationTableWithCounterValuesROCA<I> table) {
        this.rowId = rowId;
        this.node = node;
        this.nodes = new ArrayList<>();
        this.table = table;
    }

    RowImpl(int rowId, ObservationTreeNode<I> node, ObservationTableWithCounterValuesROCA<I> table, int alphabetSize) {
        this(rowId, node, table);

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

    public int getApproxId() {
        return approxId;
    }

    public int getSameOutputsId() {
        return sameOutputsId;
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
        this.approxId = canonicalId;
    }

    void setSameOutputsId(int sameOutputsId) {
        this.sameOutputsId = sameOutputsId;
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

    List<PairCounterValueOutput<Boolean>> getRowContents() {
        if (cvOutputs == null || cvOutputs.size() != table.numberOfClassicalSuffixes()) {
            List<PairCounterValueOutput<Boolean>> newCvOutputs = new ArrayList<>(nodes.size());
            for (int i = 0; i < numberOfSuffixes(); i++) {
                ObservationTreeNode<I> node = nodes.get(i);
                if (!table.isSuffixOnlyForLanguage(i)) {
                    PairCounterValueOutput<Boolean> cvOutput = node.getCounterValueOutput();
                    newCvOutputs.add(cvOutput);
                }
            }
            cvOutputs = newCvOutputs;
        }
        return cvOutputs;
    }

    List<PairCounterValueOutput<Boolean>> getWholeRowContents() {
        List<PairCounterValueOutput<Boolean>> cvOutputs = new ArrayList<>(nodes.size());
        for (int i = 0; i < numberOfSuffixes(); i++) {
            ObservationTreeNode<I> node = nodes.get(i);
            if (table.isSuffixOnlyForLanguage(i)) {
                cvOutputs.add(new PairCounterValueOutput<>(node.getOutput(), -2));
            } else {
                cvOutputs.add(node.getCounterValueOutput());
            }
        }
        return cvOutputs;
    }

    List<Boolean> getOutputs() {
        if (outputs == null || outputs.size() != table.numberOfClassicalSuffixes()) {
            List<Boolean> newOutputs = new ArrayList<>();
            for (int i = 0; i < numberOfSuffixes(); i++) {
                ObservationTreeNode<I> node = nodes.get(i);
                if (!table.isSuffixOnlyForLanguage(i)) {
                    newOutputs.add(node.getOutput());
                }
            }
            outputs = newOutputs;
        }
        return outputs;
    }

    int getCounterValue() {
        return node.getCounterValue();
    }

    void addSuffix(ObservationTreeNode<I> node) {
        nodes.add(node);
    }

    int numberOfSuffixes() {
        return nodes.size();
    }

    @Override
    public boolean hasContents() {
        return !getRowContents().isEmpty();
    }

    /**
     * Call this function to notify the row that one of its underlying nodes changed
     * its output
     */
    void outputChange() {
        this.outputs = null;
        this.cvOutputs = null;
        table.outputsOfRowChanged(this);
    }

    /**
     * Call this function to notify the row that one of its underlying nodes changed
     * its counter value
     */
    void counterValueChange() {
        this.cvOutputs = null;
        table.changedCounterValue(this);
    }

    ObservationTableWithCounterValuesROCA<I> getTable() {
        return table;
    }

    @Override
    public String toString() {
        return getLabel().toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLabel());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }
        RowImpl<?> r = (RowImpl<?>) obj;
        return Objects.equals(r.getLabel(), this.getLabel());
    }
}
