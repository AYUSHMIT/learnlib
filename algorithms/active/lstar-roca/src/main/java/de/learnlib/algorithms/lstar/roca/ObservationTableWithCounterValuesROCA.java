package de.learnlib.algorithms.lstar.roca;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.MutableObservationTable;
import de.learnlib.datastructure.observationtable.OTUtils;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * An observation table for {@link LStarROCA} where observations are pair
 * (output, counter value).
 * 
 * The implementation assumes the target ROCA accepts by final state and counter
 * value equal to zero, i.e., {@link AcceptanceMode.BOTH};
 * 
 * The learnt knowledge is actually stored in a prefix tree. The cells in the
 * table are references to nodes in the tree. This implies the learner stores
 * more information than is actually used. Storing more information allows us to
 * reduce the number of queries.
 * 
 * The counter value of a node is computed if and only if the node's label is in
 * the prefix of the language. That is, we ask counter value queries only when
 * we are sure the information will be useful. Otherwise, we store "-1" in the
 * table.
 * 
 * To determine if the table is closed and consistent, we first have to compute,
 * for each row r, the set of rows that approximate r. We call this set
 * "Approx". Approx is computed as follows:
 * <ul>
 * <li>If r is a short prefix row, Approx(r) is the set of short prefix rows
 * with exactly the same contents (i.e., the same output and counter value for
 * each suffix).</li>
 * <li>If r is a short prefix row but is such that each cell is (false, -1),
 * then Approx(r) is not computed.</li>
 * <li>If r is a long prefix row and there is a cell that is not (false, -1),
 * Approx(r) is the set of short prefix rows that approximate r. A row u
 * approximate r if, for each suffix, the output of u and r is the same, and
 * (the counter values of u and r are both not -1) implies the counter values of
 * u and r are equal. That is, the outputs must always be identical but there
 * may be differences in counter values if one of the two counter values is
 * -1.</li>
 * </ul>
 * 
 * Then, the table is closed if each row has a non-empty Approx and is
 * consistent if, for all row r, the intersection of all Approx(ua) (with u a
 * row in Approx(r) and a a symbol in the alphabet) is not empty. The rows where
 * all cells are (false, -1) are not considered in both checks.
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
final public class ObservationTableWithCounterValuesROCA<I> implements MutableObservationTable<I, Boolean> {
    private final static int NO_APPROX = -1;

    private final List<RowImpl<I>> shortPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> longPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> allRows = new ArrayList<>();
    private int numRows = 0;

    private final Map<Word<I>, RowImpl<I>> rowMap = new HashMap<>();

    private final ObservationTreeNode<I> observationTreeRoot;

    private final List<Set<Integer>> approx = new ArrayList<>();
    private final Map<Set<Integer>, Integer> approxToApproxId = new HashMap<>();
    // Approx id -> canonical row
    private final Map<Integer, RowImpl<I>> canonicalRows = new HashMap<>();

    private final List<Word<I>> suffixes = new ArrayList<>();
    private final Set<Word<I>> suffixesSet = new HashSet<>();

    private final Alphabet<I> alphabet;
    private int alphabetSize;

    public int counterLimit = -1;

    private boolean initialConsistencyCheckRequired;

    private final MembershipOracle.CounterValueOracle<I> counterValueOracle;

    private static <I> boolean checkInitialPrefixClosed(List<Word<I>> initialShortPrefixes) {
        Set<Word<I>> prefixes = new HashSet<>(initialShortPrefixes);
        for (Word<I> pref : initialShortPrefixes) {
            if (!pref.isEmpty() && !prefixes.contains(pref.prefix(-1))) {
                return false;
            }
        }

        return true;
    }

    public ObservationTableWithCounterValuesROCA(Alphabet<I> alphabet,
            MembershipOracle.CounterValueOracle<I> counterValueOracle) {
        this.alphabet = alphabet;
        this.alphabetSize = alphabet.size();
        this.observationTreeRoot = new ObservationTreeNode<>(Word.epsilon(), null, alphabet);
        this.counterValueOracle = counterValueOracle;
    }

    /**
     * Creates an object implementing
     * {@code ObservationTable<I, OutputAndCounterValue>}.
     * 
     * The returned table can be used to nicely display its contents (see
     * {@link OTUtils}).
     * 
     * @return
     */
    public ObservationTable<I, PairCounterValueOutput<Boolean>> toClassicObservationTable() {
        class Table implements ObservationTable<I, PairCounterValueOutput<Boolean>> {

            private final ObservationTableWithCounterValuesROCA<I> table;

            public Table(ObservationTableWithCounterValuesROCA<I> table) {
                this.table = table;
            }

            @Override
            public Word<I> transformAccessSequence(Word<I> word) {
                return table.transformAccessSequence(word);
            }

            @Override
            public Alphabet<I> getInputAlphabet() {
                return table.getInputAlphabet();
            }

            @Override
            public Collection<Row<I>> getShortPrefixRows() {
                return table.getShortPrefixRows();
            }

            @Override
            public Collection<Row<I>> getLongPrefixRows() {
                return table.getLongPrefixRows();
            }

            @Override
            public Row<I> getRow(int idx) {
                return table.getRow(idx);
            }

            @Override
            public int numberOfDistinctRows() {
                return table.numberOfDistinctRows();
            }

            @Override
            public List<Word<I>> getSuffixes() {
                return table.getSuffixes();
            }

            @Override
            public List<PairCounterValueOutput<Boolean>> rowContents(Row<I> row) {
                return table.fullRowContents(row);
            }

        }

        return new Table(this);
    }

    @Override
    public Alphabet<I> getInputAlphabet() {
        return alphabet;
    }

    @Override
    public Collection<Row<I>> getShortPrefixRows() {
        return Collections.unmodifiableCollection(shortPrefixRows);
    }

    @Override
    public Collection<Row<I>> getLongPrefixRows() {
        return Collections.unmodifiableCollection(longPrefixRows);
    }

    @Override
    public Row<I> getRow(int idx) {
        return allRows.get(idx);
    }

    @Override
    public int numberOfDistinctRows() {
        Set<List<PairCounterValueOutput<Boolean>>> distinct = new HashSet<>();
        allRows.stream().forEach(r -> distinct.add(this.fullRowContents(r)));
        return distinct.size();
    }

    @Override
    public boolean isInitialized() {
        return numberOfDistinctRows() != 0;
    }

    @Override
    public List<Word<I>> getSuffixes() {
        return Collections.unmodifiableList(suffixes);
    }

    /**
     * Gets a list with only the outputs of the given row.
     * 
     * @param row The row.
     * @return A list with booleans
     */
    @Override
    public List<Boolean> rowContents(Row<I> row) {
        List<ObservationTreeNode<I>> contents = treeNodes(row);
        return contents.stream().map(c -> c.getOutput()).collect(Collectors.toList());
    }

    @Override
    public Boolean cellContents(Row<I> row, int columnId) {
        List<ObservationTreeNode<I>> contents = treeNodes(row);
        return contents.get(columnId).getOutput();
    }

    /**
     * Gets a list of pairs (output, counter value) for the given row.
     * 
     * That is, it gives the actual contents of the row.
     * 
     * @param row The row
     * @return The row contents
     */
    public List<PairCounterValueOutput<Boolean>> fullRowContents(Row<I> row) {
        List<ObservationTreeNode<I>> nodes = treeNodes(row);
        return nodes.stream().map(n -> n.getSimplifiedCounterValueOutput()).collect(Collectors.toList());
    }

    public PairCounterValueOutput<Boolean> fullCellContents(Row<I> row, int columnId) {
        return treeNodes(row).get(columnId).getSimplifiedCounterValueOutput();
    }

    private List<ObservationTreeNode<I>> treeNodes(Row<I> row) {
        return allRows.get(row.getRowId()).getRowContents();
    }

    @Override
    public boolean isInitialConsistencyCheckRequired() {
        return initialConsistencyCheckRequired;
    }

    public List<Row<I>> getCanonicalRows() {
        return new ArrayList<>(canonicalRows.values());
    }

    @Override
    public @Nullable Inconsistency<I> findInconsistency() {
        final Alphabet<I> alphabet = getInputAlphabet();

        // To determine whether the table is consistent, we have to check if there is a
        // row r such that the intersection of all Approx(ua) is empty, with u in
        // Approx(r) and a an input symbol.
        // If this is the case, r and u are witnesses for inconsistency due to symbol a.
        // Note that non co-accessible rows are not considered as they have no canonical
        // rows.
        for (RowImpl<I> startRow : canonicalRows.values()) {
            int startApproxId = startRow.getCanonicalId();
            Set<Integer> startApprox = approx.get(startApproxId);
            // If the only row in Approx(r) is r, we have nothing to do.
            if (startApprox.size() == 1) {
                continue;
            }

            for (int i = 0; i < alphabet.size(); i++) {
                RowImpl<I> successorRow = startRow.getSuccessor(i);
                int successorApproxId = successorRow.getCanonicalId();
                // If the successor row is co-accessible
                if (successorApproxId != NO_APPROX) {
                    Set<Integer> intersection = new HashSet<>();
                    intersection.addAll(approx.get(successorApproxId));

                    for (Integer equivalentRowId : startApprox) {
                        RowImpl<I> equivalentRow = allRows.get(equivalentRowId);
                        RowImpl<I> equivalentSuccessorRow = equivalentRow.getSuccessor(i);
                        int equivalentSuccessorApproxId = equivalentSuccessorRow.getCanonicalId();

                        if (equivalentSuccessorApproxId != NO_APPROX) {
                            // Both successors have an approx
                            intersection.retainAll(approx.get(equivalentSuccessorApproxId));
                            if (intersection.size() == 0) {
                                return new Inconsistency<>(startRow, equivalentRow, alphabet.getSymbol(i));
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    @Override
    public @Nullable Row<I> findUnclosedRow() {
        // A row is unclosed if it is co-accessible and does not have a canonical row.
        for (Row<I> lpRow : getLongPrefixRows()) {
            if (isCoAccessibleRow(lpRow) && getCanonicalRow(lpRow) == null) {
                return lpRow;
            }
        }
        return null;
    }

    public @Nullable Row<I> getCanonicalRow(Row<I> row) {
        if (isCoAccessibleRow(row)) {
            RowImpl<I> r = allRows.get(row.getRowId());
            return canonicalRows.get(r.getCanonicalId());
        } else {
            return null;
        }
    }

    @Override
    public @Nullable Row<I> getRow(Word<I> prefix) {
        return rowMap.get(prefix);
    }

    @Override
    public Word<I> transformAccessSequence(Word<I> word) {
        Row<I> current = rowMap.get(Word.epsilon());
        assert current != null;

        for (I symbol : word) {
            current = getRowSuccessor(current, symbol);
            if (current == null) {
                return null;
            }
            current = getCanonicalRow(current);
            assert current != null;
        }

        return current.getLabel();
    }

    public void setInitialCounterLimit(int counterLimit) {
        this.counterLimit = counterLimit;
    }

    @Override
    public List<List<Row<I>>> initialize(List<Word<I>> initialShortPrefixes, List<Word<I>> initialSuffixes,
            MembershipOracle<I, Boolean> oracle) {
        if (!allRows.isEmpty()) {
            throw new IllegalStateException("Called initialize, but there are already rows present");
        }

        if (counterLimit == -1) {
            throw new IllegalStateException("Called initialize without setting the initial counter limit");
        }

        if (!checkInitialPrefixClosed(initialShortPrefixes)) {
            throw new IllegalArgumentException("Initial short prefixes are not prefix-closed");
        }

        if (!initialShortPrefixes.get(0).isEmpty()) {
            throw new IllegalArgumentException("First initial short prefix MUST be the empty word!");
        }

        for (Word<I> suffix : initialSuffixes) {
            if (suffixesSet.add(suffix)) {
                suffixes.add(suffix);
            }
        }

        // Initialize root of tree
        boolean epsilonAccepted = oracle.answerQuery(Word.epsilon());
        observationTreeRoot.setActualOutput(epsilonAccepted);
        observationTreeRoot.setOutput(epsilonAccepted);
        observationTreeRoot.setActualCounterValue(0);
        observationTreeRoot.setCounterValue(0);
        observationTreeRoot.inPrefix = epsilonAccepted;

        for (Word<I> sp : initialShortPrefixes) {
            RowImpl<I> row = createSpRow(sp);
            // createTreeNodes will also fill the counter values when needed
            createTreeNodes(row, suffixes, oracle);
        }

        for (RowImpl<I> row : shortPrefixRows) {
            for (int i = 0; i < alphabet.size(); i++) {
                I symbol = alphabet.getSymbol(i);
                Word<I> lp = row.getLabel().append(symbol);
                RowImpl<I> successorRow = rowMap.get(lp);
                if (successorRow == null) {
                    successorRow = createLpRow(lp);
                    createTreeNodes(successorRow, suffixes, oracle);
                }
                row.setSuccessor(i, successorRow);
            }
        }

        return updateCanonicalRows();
    }

    @Override
    public List<List<Row<I>>> addSuffixes(Collection<? extends Word<I>> newSuffixes,
            MembershipOracle<I, Boolean> oracle) {
        if (!addSuffixesInternal(newSuffixes, oracle)) {
            return Collections.emptyList();
        }

        return updateCanonicalRows();
    }

    private boolean addSuffixesInternal(Collection<? extends Word<I>> newSuffixes,
            MembershipOracle<I, Boolean> oracle) {
        List<Word<I>> newSuffixList = new ArrayList<>();
        for (Word<I> suffix : newSuffixes) {
            if (suffixesSet.add(suffix)) {
                newSuffixList.add(suffix);
            }
        }

        if (newSuffixList.isEmpty()) {
            return false;
        }

        this.suffixes.addAll(newSuffixList);

        for (RowImpl<I> row : allRows) {
            createTreeNodes(row, newSuffixList, oracle);
        }

        return true;
    }

    @Override
    public List<List<Row<I>>> addShortPrefixes(List<? extends Word<I>> shortPrefixes,
            MembershipOracle<I, Boolean> oracle) {

        List<Row<I>> toSpRows = new ArrayList<>();

        for (Word<I> sp : shortPrefixes) {
            Row<I> row = getRow(sp);
            if (row != null) {
                if (row.isShortPrefixRow()) {
                    continue;
                }
            } else {
                row = createSpRow(sp);
            }
            toSpRows.add(row);
        }

        return toShortPrefixes(toSpRows, oracle);
    }

    @Override
    public List<List<Row<I>>> toShortPrefixes(List<Row<I>> lpRows, MembershipOracle<I, Boolean> oracle) {
        toShortPrefixesInternal(lpRows, oracle);
        return updateCanonicalRows();
    }

    private void toShortPrefixesInternal(List<Row<I>> lpRows, MembershipOracle<I, Boolean> oracle) {
        List<RowImpl<I>> freshRows = new ArrayList<>();

        for (Row<I> r : lpRows) {
            final RowImpl<I> row = allRows.get(r.getRowId());
            if (row.isShortPrefixRow()) {
                if (row.hasContents()) {
                    continue;
                }
                freshRows.add(row);
            } else {
                makeShort(row);
                if (!row.hasContents()) {
                    freshRows.add(row);
                }
            }

            Word<I> prefix = row.getLabel();
            for (int i = 0; i < alphabet.size(); i++) {
                I symbol = alphabet.getSymbol(i);
                Word<I> lp = prefix.append(symbol);
                RowImpl<I> lpRow = rowMap.get(lp);
                if (lpRow == null) {
                    lpRow = createLpRow(lp);
                    freshRows.add(lpRow);
                }
                row.setSuccessor(i, lpRow);
            }
        }

        for (RowImpl<I> row : freshRows) {
            // createTreeNodes will also set the counter values when needed
            createTreeNodes(row, suffixes, oracle);
        }
    }

    /**
     * Increases the counter limit, and adds the provided short prefixes and
     * suffixes.
     * 
     * @param newCounterLimit  The new counter limit
     * @param newShortPrefixes The new short prefixes
     * @param newSuffixes      The new suffixes
     * @param oracle           The membership oracle
     * @return The unclosed rows
     */
    public List<List<Row<I>>> increaseCounterLimit(int newCounterLimit, List<Word<I>> newShortPrefixes,
            List<Word<I>> newSuffixes, MembershipOracle<I, Boolean> oracle) {
        assert newCounterLimit > counterLimit;
        this.counterLimit = newCounterLimit;

        // First, we update the tree without adding new nodes
        observationTreeRoot.increaseCounterLimit(oracle, counterValueOracle, counterLimit);

        // Second, we add the new prefixes and suffixes
        List<Row<I>> toSpRows = new ArrayList<>();
        for (Word<I> sp : newShortPrefixes) {
            Row<I> row = getRow(sp);
            if (row != null) {
                if (row.isShortPrefixRow()) {
                    continue;
                }
            } else {
                row = createSpRow(sp);
            }
            toSpRows.add(row);
        }
        toShortPrefixesInternal(toSpRows, oracle);
        addSuffixesInternal(newSuffixes, oracle);

        // Finally, we update the canonical rows and seek the unclosed rows
        return updateCanonicalRows();
    }

    @Override
    public List<List<Row<I>>> addAlphabetSymbol(I symbol, MembershipOracle<I, Boolean> oracle) {
        if (!alphabet.containsSymbol(symbol)) {
            Alphabets.toGrowingAlphabetOrThrowException(alphabet).addSymbol(symbol);
        }

        final int newAlphabetSize = alphabet.size();

        if (isInitialized() && alphabetSize < newAlphabetSize) {
            alphabetSize = newAlphabetSize;
            final int newSymbolIndex = alphabet.getSymbolIndex(symbol);

            for (RowImpl<I> spRow : shortPrefixRows) {
                spRow.ensureInputCapacity(newAlphabetSize);

                final Word<I> newLongPrefix = spRow.getLabel().append(symbol);
                final RowImpl<I> longPrefixRow = createLpRow(newLongPrefix);

                spRow.setSuccessor(newSymbolIndex, longPrefixRow);

                createTreeNodes(longPrefixRow, suffixes, oracle);
            }

            return updateCanonicalRows();
        } else {
            return Collections.emptyList();
        }
    }

    private List<List<Row<I>>> updateCanonicalRows() {
        Map<Integer, List<Row<I>>> unclosed = new HashMap<>();

        canonicalRows.clear();
        approx.clear();
        approxToApproxId.clear();
        for (RowImpl<I> row : allRows) {
            // We reset the previously computed canonical id
            row.setCanonicalId(NO_APPROX);
            if (isCoAccessibleRow(row)) {
                Set<Integer> approxOfRow = computeApprox(row);

                // If the set of rows approximating the current row is empty, it means we have
                // an unclosed row
                if (approxOfRow.size() == 0) {
                    assert !row.isShortPrefixRow();
                    int unclosedId = Objects.hash(fullRowContents(row));
                    if (!unclosed.containsKey(unclosedId)) {
                        unclosed.put(unclosedId, new ArrayList<>());
                    }
                    unclosed.get(unclosedId).add(row);
                } else {
                    Integer approxId = approxToApproxId.get(approxOfRow);
                    if (approxId == null) {
                        Integer rowId = approxOfRow.iterator().next();
                        approxId = approx.size();
                        approx.add(approxOfRow);
                        canonicalRows.put(approxId, allRows.get(rowId));
                        approxToApproxId.put(approxOfRow, approxId);
                    }

                    row.setCanonicalId(approxId);
                }
            }

        }

        return new ArrayList<>(unclosed.values());
    }

    private Set<Integer> computeApprox(Row<I> row) {
        // If row is a short prefix, Approx(row) is the set of short prefix rows with
        // exactly the same contents for each suffix.
        // If row is a long prefix, Approx(row) is the set of short prefix rows u such
        // that, for each suffix, the outputs of r and u are identical and (the counter
        // values of r and u are both not -1) implies (the counter values of u and r are
        // equal), for all suffix.
        Set<Integer> result = new HashSet<>();
        List<ObservationTreeNode<I>> rowContents = treeNodes(row);
        for (Row<I> spRow : getShortPrefixRows()) {
            List<ObservationTreeNode<I>> spContents = treeNodes(spRow);
            boolean isApprox = true;
            for (int i = 0; i < numberOfSuffixes(); i++) {
                PairCounterValueOutput<Boolean> spCell = spContents.get(i).getSimplifiedCounterValueOutput();
                PairCounterValueOutput<Boolean> rowCell = rowContents.get(i).getSimplifiedCounterValueOutput();
                if (row.isShortPrefixRow()) {
                    if (!Objects.equals(spCell.getOutput(), rowCell.getOutput())) {
                        isApprox = false;
                        break;
                    } else if (spCell.getCounterValue() != rowCell.getCounterValue()) {
                        isApprox = false;
                        break;
                    }
                } else {
                    if (spCell.getOutput() != rowCell.getOutput()) {
                        isApprox = false;
                        break;
                    }

                    if (spCell.getCounterValue() != ObservationTreeNode.UNKNOWN_COUNTER_VALUE
                            && rowCell.getCounterValue() != ObservationTreeNode.UNKNOWN_COUNTER_VALUE
                            && spCell.getCounterValue() != rowCell.getCounterValue()) {
                        isApprox = false;
                        break;
                    }
                }
            }

            if (isApprox) {
                result.add(spRow.getRowId());
            }
        }
        return result;
    }

    /**
     * Create the nodes in the tree for each provided suffix and sets the contents
     * of the row.
     * 
     * That is, it sets the contents of the row to be the list of nodes in the row.
     * 
     * If the row already has contents, it appends the new nodes at the end of the
     * list.
     * 
     * @param row      The row
     * @param suffixes The suffixes to use.
     * @param oracle   The membership oracle
     */
    private void createTreeNodes(RowImpl<I> row, List<Word<I>> suffixes, MembershipOracle<I, Boolean> oracle) {
        // First, we get the node in the tree corresponding to the label of the row.
        // Once we have that node, we add all the suffixes
        ObservationTreeNode<I> prefixNode = row.getNode();

        List<ObservationTreeNode<I>> nodes = row.getRowContents();
        if (nodes == null) {
            nodes = new ArrayList<>(suffixes.size());
        }

        for (Word<I> suffix : suffixes) {
            nodes.add(prefixNode.addSuffixInTable(suffix, 0, oracle, counterValueOracle, counterLimit));
        }

        if (row.getRowContents() == null) {
            row.setRowContents(nodes);
        }
    }

    private RowImpl<I> createSpRow(Word<I> prefix) {
        ObservationTreeNode<I> node = observationTreeRoot.getPrefix(prefix, 0);
        RowImpl<I> row = new RowImpl<>(numRows++, node, alphabet.size());
        allRows.add(row);
        rowMap.put(prefix, row);
        shortPrefixRows.add(row);
        return row;
    }

    private RowImpl<I> createLpRow(Word<I> prefix) {
        ObservationTreeNode<I> node = observationTreeRoot.getPrefix(prefix, 0);
        RowImpl<I> row = new RowImpl<>(numRows++, node);
        allRows.add(row);
        rowMap.put(prefix, row);
        int idx = longPrefixRows.size();
        longPrefixRows.add(row);
        row.setLpIndex(idx);
        return row;
    }

    private void makeShort(RowImpl<I> row) {
        if (row.isShortPrefixRow()) {
            return;
        }

        int lastIdx = longPrefixRows.size() - 1;
        RowImpl<I> last = longPrefixRows.get(lastIdx);
        int rowIdx = row.getLpIndex();
        longPrefixRows.remove(lastIdx);
        if (last != row) {
            longPrefixRows.set(rowIdx, last);
            last.setLpIndex(rowIdx);
        }

        shortPrefixRows.add(row);
        row.makeShort(alphabet.size());
    }

    private boolean isCoAccessibleRow(Row<I> row) {
        List<ObservationTreeNode<I>> rowContents = treeNodes(row);
        for (ObservationTreeNode<I> cell : rowContents) {
            if (cell.getSimplifiedCounterValue() != ObservationTreeNode.UNKNOWN_COUNTER_VALUE) {
                return true;
            }
        }
        return false;
    }

    ObservationTreeNode<I> getObservationTreeRoot() {
        return observationTreeRoot;
    }
}
