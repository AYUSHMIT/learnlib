package de.learnlib.algorithms.lstar.roca;

import static de.learnlib.algorithms.lstar.roca.ObservationTreeNode.UNKNOWN_COUNTER_VALUE;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.datastructure.observationtable.MutableObservationTable;
import de.learnlib.datastructure.observationtable.OTUtils;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.automata.oca.AcceptanceMode;
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
public final class ObservationTableWithCounterValuesROCA<I> implements MutableObservationTable<I, Boolean> {
    private final static int NO_APPROX = -1;

    private final List<RowImpl<I>> shortPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> longPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> allRows = new ArrayList<>();
    private int numRows = 0;

    private final Map<Word<I>, RowImpl<I>> rowMap = new HashMap<>();

    private final ObservationTreeNode<I> observationTreeRoot;

    // Approx id -> set of identifiers of rows in the Approx set
    private Map<Integer, Set<Integer>> approx = new HashMap<>();
    // Approx id -> canonical row
    private final Map<Integer, RowImpl<I>> canonicalRows = new HashMap<>();
    private final Map<Integer, Map<I, RowImpl<I>>> canonicalSuccessors = new HashMap<>();

    private final Map<BitSet, Set<RowImpl<I>>> sameOutputs = new HashMap<>();
    private final Map<BitSet, RowImpl<I>> canonicalSameOutputs = new HashMap<>();

    private final Set<RowImpl<I>> updatedRows = new HashSet<>();

    private final List<Word<I>> suffixes = new ArrayList<>();
    private final Set<Word<I>> suffixesSet = new HashSet<>();

    private final Alphabet<I> alphabet;
    private int alphabetSize;

    public int counterLimit = -1;

    private final MembershipOracle.CounterValueOracle<I> counterValueOracle;

    private ApproxInconsistency<I> inconsistency;

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
        return canonicalRows.size();
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
        return true;
    }

    public List<Row<I>> getCanonicalRows() {
        return new ArrayList<>(canonicalRows.values());
    }

    public List<Row<I>> getCanonicalShortPrefixRows() {
        return canonicalRows.values().stream().filter(r -> r.isShortPrefixRow()).collect(Collectors.toList());
    }

    @Override
    public @Nullable ApproxInconsistency<I> findInconsistency() {
        if (inconsistency.isEmpty()) {
            return null;
        }
        else {
            return inconsistency;
        }
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

    private @Nullable RowImpl<I> getCanonicalRow(Row<I> row) {
        if (row.isShortPrefixRow()) {
            RowImpl<I> r = allRows.get(row.getRowId());
            return canonicalRows.get(r.getCanonicalId());
        } else {
            return null;
        }
    }

    /**
     * Returns the canonical successor for the given row and symbol.
     * 
     * If the table is not modified, it is guaranteed that the same row is returned,
     * given the same row and symbol.
     * 
     * @param row    The row
     * @param symbol The symbol
     * @return The canonical successor, i.e., a selected row among the possible
     *         successors.
     */
    public @Nullable Row<I> getCanonicalSuccessor(Row<I> row, I symbol) {
        assert row.isShortPrefixRow();
        RowImpl<I> r = allRows.get(row.getRowId());
        if (canonicalSuccessors.containsKey(r.getCanonicalId())) {
            return canonicalSuccessors.get(r.getCanonicalId()).get(symbol);
        } else {
            return null;
        }
    }

    /**
     * Gets the canonical row for the given approx identifier, assuming the Approx
     * class in the short prefixes.
     * 
     * @param approxId The Approx class identifier
     * @return The canonical row
     */
    @Nullable
    Row<I> getShortPrefixCanonicalRow(int approxId) {
        return canonicalRows.get(approxId);
    }

    /**
     * Gets all the short prefix rows in the Approx class identified by {@code approxId}
     * 
     * @param approxId The Approx class identifier
     * @return All the rows in the class
     */
    Set<Row<I>> getRowsInApprox(int approxId) {
        return approx.get(approxId).stream().map(i -> allRows.get(i)).collect(Collectors.toSet());
    }

    Set<Integer> getAllShortPrefixesApproxIds() {
        return approx.keySet();
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
        observationTreeRoot.inPrefix = epsilonAccepted;
        observationTreeRoot.setActualOutput(epsilonAccepted);
        observationTreeRoot.setActualCounterValue(0);
        if (epsilonAccepted) {
            observationTreeRoot.setOutput(epsilonAccepted);
            observationTreeRoot.setCounterValue(0);
        }

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

    public boolean isInInitialClass(Row<I> row) {
        // We know that '0' is always the row id for epsilon.
        // So, if the class of row contains 0, it means row is in the same class as
        // epsilon.
        RowImpl<I> r = allRows.get(row.getRowId());
        return approx.getOrDefault(r.getCanonicalId(), Collections.emptySet()).contains(0);
    }

    private List<List<Row<I>>> updateCanonicalRows() {
        // First, we retrieve all the rows for which we must (re)compute Approx.
        Set<RowImpl<I>> shortPrefixesToUpdate = new HashSet<>();
        Set<RowImpl<I>> longPrefixesToUpdate = new HashSet<>();
        for (RowImpl<I> row : updatedRows) {
            for (RowImpl<I> r : sameOutputs.get(row.getOutputs())) {
                if (r.isShortPrefixRow()) {
                    shortPrefixesToUpdate.add(r);
                } else {
                    longPrefixesToUpdate.add(r);
                }
            }
        }

        Map<Integer, Set<Integer>> newApprox = new HashMap<>();
        Map<Set<Integer>, Integer> approxToApproxId = new HashMap<>();
        Map<Integer, Set<RowImpl<I>>> rowsByApproxId = new HashMap<>();
        Set<Integer> freeApproxIds = new HashSet<>();
        freeApproxIds.addAll(approx.keySet());

        canonicalRows.clear();
        canonicalSuccessors.clear();

        // We construct the new Approx classes (and canonical rows) by first copying the
        // information that is not modified.
        // We then compute only what is needed

        // We copy the classes that do not have to be modified
        for (RowImpl<I> row : allRows) {
            int approxId = NO_APPROX;
            if (row.isShortPrefixRow() && !shortPrefixesToUpdate.contains(row)) {
                approxId = row.getCanonicalId();
                if (!newApprox.containsKey(row.getCanonicalId())) {
                    Set<Integer> approxOfRow = approx.get(approxId);
                    newApprox.put(approxId, approxOfRow);
                    approxToApproxId.put(approxOfRow, approxId);
                    freeApproxIds.remove(approxId);
                }
                if (!rowsByApproxId.containsKey(approxId)) {
                    rowsByApproxId.put(approxId, new HashSet<>());
                }
                rowsByApproxId.get(approxId).add(row);
            } else if (!row.isShortPrefixRow() && !longPrefixesToUpdate.contains(row)) {
                approxId = row.getCanonicalId();
                if (approxId != NO_APPROX && !newApprox.containsKey(approxId)) {
                    Set<Integer> approxOfRow = approx.get(approxId);
                    newApprox.put(approxId, approxOfRow);
                    approxToApproxId.put(approxOfRow, approxId);
                    freeApproxIds.remove(approxId);
                }
                if (!rowsByApproxId.containsKey(approxId)) {
                    rowsByApproxId.put(approxId, new HashSet<>());
                }
                rowsByApproxId.get(approxId).add(row);
            }
        }

        // We compute the classes for the short prefix rows
        for (RowImpl<I> row : shortPrefixesToUpdate) {
            Set<Integer> approxOfRow = computeApprox(row);

            // If the intersection between the computed Approx and the set of short prefixes is empty, it means the row is unclosed.
            // This is not possible for short prefixes. Then, we assume that Approx is not empty.

            int approxId = approxToApproxId.getOrDefault(approxOfRow, NO_APPROX);
            if (approxId == NO_APPROX) {
                if (freeApproxIds.isEmpty()) {
                    approxId = newApprox.size();
                } else {
                    Iterator<Integer> itr = freeApproxIds.iterator();
                    approxId = itr.next();
                    itr.remove();
                }
                newApprox.put(approxId, approxOfRow);
                approxToApproxId.put(approxOfRow, approxId);
            }

            row.setCanonicalId(approxId);
            if (!rowsByApproxId.containsKey(approxId)) {
                rowsByApproxId.put(approxId, new HashSet<>());
            }
            rowsByApproxId.get(approxId).add(row);
        }

        Map<Integer, List<Row<I>>> unclosed = new HashMap<>();
        for (RowImpl<I> row : longPrefixesToUpdate) {
            if (isCoAccessibleRow(row)) {
                Set<Integer> approxOfRow = computeApprox(row);

                Set<Integer> intersectionApproxShortPrefixes = new HashSet<>(approxOfRow);
                intersectionApproxShortPrefixes.retainAll(shortPrefixRows.stream().map(r -> r.getRowId()).collect(Collectors.toSet()));

                int approxId;
                if (intersectionApproxShortPrefixes.isEmpty()) {
                    int unclosedId = Objects.hash(fullRowContents(row));
                    if (!unclosed.containsKey(unclosedId)) {
                        unclosed.put(unclosedId, new ArrayList<>());
                    }
                    unclosed.get(unclosedId).add(row);
                    approxId = NO_APPROX;
                }
                else {
                    approxId = approxToApproxId.getOrDefault(approxOfRow, NO_APPROX);
                    if (approxId == NO_APPROX) {
                        if (freeApproxIds.isEmpty()) {
                            approxId = newApprox.size();
                        } else {
                            Iterator<Integer> itr = freeApproxIds.iterator();
                            approxId = itr.next();
                            itr.remove();
                        }
                        newApprox.put(approxId, approxOfRow);
                        approxToApproxId.put(approxOfRow, approxId);
                    }
                }

                row.setCanonicalId(approxId);
                if (!rowsByApproxId.containsKey(approxId)) {
                    rowsByApproxId.put(approxId, new HashSet<>());
                }
                rowsByApproxId.get(approxId).add(row);
            }
        }

        approx = newApprox;

        // We define a canonical row by approx id
        for (Map.Entry<Integer, Set<RowImpl<I>>> entry : rowsByApproxId.entrySet()) {
            int approxId = entry.getKey();
            Set<RowImpl<I>> rows = entry.getValue();
            RowImpl<I> canonical = rows.stream().filter(r -> r.isShortPrefixRow()).findAny().orElse(null);
            if (canonical == null) {
                continue;
            }
            canonicalRows.put(approxId, canonical);
        }

        // We define the canonical successors of each canonical row.
        // For each row u and symbol a, we compute the intersection of all Approx(va)
        // for every v such that Approx(v) = Approx(u).
        // We then arbitrarily pick a class in the intersection and take its canonical
        // row.
        inconsistency = new ApproxInconsistency<>();
        for (RowImpl<I> canonicalRow : canonicalRows.values()) {
            int approxId = canonicalRow.getCanonicalId();
            Set<Integer> approxOfRow = approx.get(approxId);

            for (int i = 0; i < alphabet.size(); i++) {
                I symbol = alphabet.getSymbol(i);
                Set<Integer> intersection = new HashSet<>();

                RowImpl<I> successorRow = canonicalRow.getSuccessor(i);
                if (successorRow.getCanonicalId() == NO_APPROX) {
                    continue;
                }
                Set<Integer> approxOfSuccessor = approx.get(successorRow.getCanonicalId());
                intersection.addAll(approxOfSuccessor);

                for (int equivalentRowId : approxOfRow) {
                    RowImpl<I> row = allRows.get(equivalentRowId);
                    if (!row.isShortPrefixRow()) {
                        continue;
                    }
                    successorRow = row.getSuccessor(i);
                    if (successorRow.getCanonicalId() != NO_APPROX) {
                        approxOfSuccessor = approx.get(successorRow.getCanonicalId());
                        Set<Integer> newIntersection = new HashSet<>(intersection);
                        newIntersection.retainAll(approxOfSuccessor);

                        if (newIntersection.isEmpty()) {
                            int otherRowId = intersection.iterator().next();
                            RowImpl<I> otherRow = allRows.get(otherRowId);

                            for (int j = 0 ; j < numberOfSuffixes() ; j++) {
                                Word<I> suffix = getSuffix(j);
                                Word<I> newSuffix = suffix.prepend(symbol);
                                PairCounterValueOutput<Boolean> successorCell = fullCellContents(successorRow, j);
                                PairCounterValueOutput<Boolean> otherCell = fullCellContents(otherRow, j);

                                if (otherCell.getOutput() != successorCell.getOutput()) {
                                    inconsistency.addSuffix(newSuffix);
                                }
                                else if (otherCell.getCounterValue() != UNKNOWN_COUNTER_VALUE
                                            && successorCell.getCounterValue() != UNKNOWN_COUNTER_VALUE
                                            && otherCell.getCounterValue() != successorCell.getCounterValue()) {
                                    inconsistency.addSuffix(newSuffix);
                                }
                            }
                            break;
                        }

                        intersection = newIntersection;
                    }
                }

                if (!intersection.isEmpty()) {
                    if (!canonicalSuccessors.containsKey(approxId)) {
                        canonicalSuccessors.put(approxId, new HashMap<>());
                    }
                    int successorApproxId = intersection.stream().filter(id -> canonicalRows.get(allRows.get(id).getCanonicalId()) != null).findAny().orElse(NO_APPROX);
                    if (successorApproxId != NO_APPROX) {
                        RowImpl<I> sucRow = allRows.get(successorApproxId);
                        RowImpl<I> canonicalSuccessorRow = canonicalRows.get(sucRow.getCanonicalId());
                        if (canonicalSuccessorRow != null) {
                            canonicalSuccessors.get(approxId).put(alphabet.getSymbol(i), canonicalSuccessorRow);
                        }
                    }
                }
            }
        }

        updatedRows.clear();
        return new ArrayList<>(unclosed.values());
    }

    private Set<Integer> computeApprox(RowImpl<I> row) {
        // If row is a short prefix, Approx(row) is the set of short prefix rows with
        // exactly the same contents for each suffix.
        // If row is a long prefix, Approx(row) is the set of short prefix rows u such
        // that, for each suffix, the outputs of r and u are identical and (the counter
        // values of r and u are both not -1) implies (the counter values of u and r are
        // equal), for all suffix.
        Set<Integer> result = new HashSet<>();
        List<ObservationTreeNode<I>> rowContents = treeNodes(row);
        Set<RowImpl<I>> spRowsToConsider = sameOutputs.get(row.getOutputs());
        for (RowImpl<I> spRow : spRowsToConsider) {
            List<ObservationTreeNode<I>> spContents = treeNodes(spRow);
            boolean isApprox = true;
            for (int i = 0; i < numberOfSuffixes(); i++) {
                PairCounterValueOutput<Boolean> spCell = spContents.get(i).getSimplifiedCounterValueOutput();
                PairCounterValueOutput<Boolean> rowCell = rowContents.get(i).getSimplifiedCounterValueOutput();
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
        int suffixIndex;
        if (nodes == null) {
            suffixIndex = 0;
            nodes = new ArrayList<>(suffixes.size());
        } else {
            suffixIndex = row.getRowContents().size();
        }

        for (Word<I> suffix : suffixes) {
            nodes.add(prefixNode.addSuffixInTable(suffix, 0, oracle, counterValueOracle, counterLimit, row,
                    suffixIndex++));
        }

        if (row.getRowContents() == null) {
            row.setRowContents(nodes);
        }

        if (!sameOutputs.containsKey(row.getOutputs())) {
            sameOutputs.put(row.getOutputs(), new HashSet<>());
        }
        sameOutputs.get(row.getOutputs()).add(row);
        if (!canonicalSameOutputs.containsKey(row.getOutputs())) {
            canonicalSameOutputs.put(row.getOutputs(), row);
        }
    }

    private RowImpl<I> createSpRow(Word<I> prefix) {
        ObservationTreeNode<I> node = observationTreeRoot.getPrefix(prefix, 0);
        RowImpl<I> row = new RowImpl<>(numRows++, node, this, alphabet.size());
        allRows.add(row);
        rowMap.put(prefix, row);
        shortPrefixRows.add(row);
        return row;
    }

    private RowImpl<I> createLpRow(Word<I> prefix) {
        ObservationTreeNode<I> node = observationTreeRoot.getPrefix(prefix, 0);
        RowImpl<I> row = new RowImpl<>(numRows++, node, this);
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

        updatedRows.add(row);
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

    void removeOutputs(RowImpl<I> row) {
        BitSet outputs = row.getOutputs();
        if (sameOutputs.containsKey(outputs)) {
            Set<RowImpl<I>> sameOut = sameOutputs.get(outputs);
            sameOut.remove(row);
            if (sameOut.size() == 0) {
                sameOutputs.remove(outputs);
                if (canonicalSameOutputs.get(outputs) == row) {
                    canonicalSameOutputs.remove(outputs);
                }
            } else if (canonicalSameOutputs.get(outputs) == row) {
                Iterator<RowImpl<I>> itr = sameOut.iterator();
                RowImpl<I> next;
                while (itr.hasNext() && !(next = itr.next()).isShortPrefixRow()) {
                    canonicalSameOutputs.put(outputs, next);
                }
            }
        }
    }

    void addOutputs(RowImpl<I> row) {
        BitSet outputs = row.getOutputs();
        if (!sameOutputs.containsKey(outputs)) {
            HashSet<RowImpl<I>> set = new HashSet<>();
            set.add(row);
            sameOutputs.put(outputs, set);
        } else {
            sameOutputs.get(outputs).add(row);
        }

        if (row.isShortPrefixRow() && !canonicalSameOutputs.containsKey(outputs)) {
            canonicalSameOutputs.put(outputs, row);
        }
        updatedRows.add(row);
    }

    void changedCounterValue(RowImpl<I> row) {
        updatedRows.add(row);
    }
}
