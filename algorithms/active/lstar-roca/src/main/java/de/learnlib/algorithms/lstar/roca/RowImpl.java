package de.learnlib.algorithms.lstar.roca;

import java.util.ArrayList;
import java.util.List;

import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.commons.smartcollections.ResizingArrayStorage;
import net.automatalib.words.Word;

/**
 * An implementation of a row for {@link ObservationTableWithCounterValuesROCA}.
 * 
 * A row has a list of {@link ObservationTreeNode}s that are used to store the row contents.
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
        List<PairCounterValueOutput<Boolean>> cvOutputs = new ArrayList<>(nodes.size());
        for (int i = 0 ; i < numberOfSuffixes() ; i++) {
            ObservationTreeNode<I> node = nodes.get(i);
            if (!table.isSuffixOnlyForLanguage(i)) {
                PairCounterValueOutput<Boolean> cvOutput = node.getCounterValueOutput();
                cvOutputs.add(cvOutput);
            }
        }

        return cvOutputs;
    }

    List<PairCounterValueOutput<Boolean>> getWholeRowContents() {
        List<PairCounterValueOutput<Boolean>> cvOutputs = new ArrayList<>(nodes.size());
        for (int i = 0 ; i < numberOfSuffixes() ; i++) {
            ObservationTreeNode<I> node  = nodes.get(i);
            if (table.isSuffixOnlyForLanguage(i)) {
                cvOutputs.add(new PairCounterValueOutput<>(node.getOutput(), -2));
            }
            else {
                cvOutputs.add(node.getCounterValueOutput());
            }
        }
        return cvOutputs;
    }

    List<Boolean> getOutputs() {
        List<Boolean> outputs = new ArrayList<>();
        for (int i = 0 ; i < numberOfSuffixes() ; i++) {
            ObservationTreeNode<I> node = nodes.get(i);
            if (!table.isSuffixOnlyForLanguage(i)) {
                outputs.add(node.getOutput());
            }
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

    void updateOutput() {
        table.updateOutputs(this);
    }

    void updateCounterValue() {
        table.changedCounterValue(this);
    }

    ObservationTableWithCounterValuesROCA<I> getTable() {
        return table;
    }
}
