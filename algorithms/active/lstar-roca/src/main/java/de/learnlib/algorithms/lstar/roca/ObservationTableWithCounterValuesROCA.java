package de.learnlib.algorithms.lstar.roca;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.datastructure.observationtable.Inconsistency;
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
 * A cell in the table is a pair (output, counter value) where output is a
 * boolean and counter value is {@code UNKNOWN_COUNTER_VALUE} or a natural. One
 * of the conditions to have a counter value different than
 * {@code UNKNOWN_COUNTER_VALUE} is that the cell's word is in the known prefix
 * of the language, i.e., in the set of prefixes of the words that are known to
 * be accepted.
 * 
 * The table maintains three sets:
 * <ul>
 * <li>A set of prefixes (the rows of the table);</li>
 * <li>Two sets of suffixes (the columns). The first is simply called the set of
 * suffixes (noted S), while the second is called the set of "only for language
 * suffixes".</li>
 * </ul>
 * 
 * The idea is that the counter values of the "only for language" columns are
 * not needed. That is, the "only for language" columns are present only to have
 * witnesses that a given word is in the known prefix of the language. It is
 * guaranteed that the set of suffixes is included in the set of "only for
 * language" suffixes.
 * 
 * The learned knowledge is actually stored in a prefix tree. The cells in the
 * table are references to nodes in the tree. This implies the learner stores
 * more information than is actually used. Storing more information allows us to
 * reduce the number of queries and reduce the overall time complexity (at the
 * cost of a higher memory consumption).
 * 
 * We can not use the equivalence relation naturally induced by the table (i.e.,
 * the classical relation used in L*) as this may render the learning process
 * infinite, due to constant changes in the known prefix. So, for each row r, we
 * define an approximation of the rows that may be equivalent to r. More
 * formally, that set Approx(r) is defined as the set of rows (short and long)
 * such that for all suffixes in S, the outputs of the rows are the same and the
 * counter values are equal, up to UNKNOWN_COUNTER_VALUE. That is, the counter
 * values must be equal if they are both not UNKNOWN_COUNTER_VALUE.
 * 
 * We say that the table is closed if each row has a non-empty intersection
 * between Approx and the set of short prefix rows, is Sigma consistent (or just
 * consistent) if, for all short prefix row r, the intersection of all
 * Approx(ua) (with u a short prefix row in Approx(r) and a a symbol in the
 * alphabet) is not empty, and is bottom consistent if for every pair of rows
 * and every column, the counter values are both UNKNOWN_COUNTER_VALUE or both
 * naturals.
 * 
 * Once the table is closed, Sigma consistent, and bottom consistent, the Approx
 * sets define a right congruence equivalence relation.
 * 
 * Remark: the implementation assumes the target ROCA accepts by final state and
 * counter value equal to zero, i.e., {@link AcceptanceMode#BOTH}.
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
public final class ObservationTableWithCounterValuesROCA<I> implements MutableObservationTable<I, Boolean> {
    public final static int UNKNOWN_COUNTER_VALUE = -1;

    private final static int NO_APPROX = -1;
    private final static int NO_ID = -1;

    private final List<RowImpl<I>> shortPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> longPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> allRows = new ArrayList<>();
    private int numRows = 0;

    private final Map<Word<I>, RowImpl<I>> rowMap = new HashMap<>();

    private final ObservationTreeNode<I> observationTreeRoot;

    // Approx id -> set of identifiers of rows in the Approx set
    private Map<Integer, Set<Integer>> approx = new HashMap<>();

    private final Map<List<Boolean>, Integer> sameOutputsIds = new HashMap<>();
    private final Set<Integer> freeSameOutputsIds = new HashSet<>();
    private final Map<Integer, Set<RowImpl<I>>> sameOutputs = new HashMap<>();

    // The set of rows for which Approx must be recomputed
    private final Set<Integer> sameOutputsToUpdateApprox = new HashSet<>();

    private final List<Word<I>> suffixes = new ArrayList<>();
    private final Set<Word<I>> suffixesSet = new HashSet<>();
    private final Set<Integer> classicalSuffixIndices = new HashSet<>();

    private final Alphabet<I> alphabet;
    private int alphabetSize;

    private int counterLimit = -1;

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
        this.observationTreeRoot = new ObservationTreeNode<>(this, null, alphabet);
        this.counterValueOracle = counterValueOracle;
    }

    /**
     * Creates an object implementing
     * {@code ObservationTable<I, OutputAndCounterValue>}.
     * 
     * The returned table can be used to nicely display its contents (see
     * {@link OTUtils}).
     * 
     * A -2 as a counter value indicates that the suffix is a "only for language"
     * suffix.
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
                RowImpl<I> r = allRows.get(row.getRowId());
                return r.getWholeRowContents();
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
    public RowImpl<I> getRow(int idx) {
        return allRows.get(idx);
    }

    @Override
    public int numberOfDistinctRows() {
        return approx.size();
    }

    @Override
    public boolean isInitialized() {
        return numberOfDistinctRows() != 0;
    }

    @Override
    public List<Word<I>> getSuffixes() {
        return Collections.unmodifiableList(suffixes);
    }

    @Override
    public int numberOfRows() {
        return allRows.size();
    }

    @Override
    public int numberOfShortPrefixRows() {
        return shortPrefixRows.size();
    }

    public int numberOfBinShortPrefixRows() {
        int n = 0;
        for (RowImpl<I> row : shortPrefixRows) {
            if (!isCoAccessibleRow(row)) {
                n += 1;
            }
        }
        return n;
    }

    @Override
    public int numberOfLongPrefixRows() {
        return longPrefixRows.size();
    }

    @Override
    public int numberOfSuffixes() {
        return suffixes.size();
    }

    public int numberOfForLanguageOnlySuffixes() {
        return numberOfSuffixes() - numberOfClassicalSuffixes();
    }

    public int numberOfClassicalSuffixes() {
        return classicalSuffixIndices.size();
    }

    public int getCounterLimit() {
        return counterLimit;
    }

    /**
     * For each equivalence class defined by Approx, it selects one representative
     * and defines the transitions according to these selected representatives.
     * 
     * @return A map mapping "representative" to a map for the transitions
     */
    public Map<Row<I>, Map<I, Row<I>>> getCanonicalRows() {
        // We assume the table is closed, \Sigma-consistent, and \bot-consistent
        // This implies that the Approx sets define equivalence classes
        Map<Row<I>, Map<I, Row<I>>> canonicalRows = new HashMap<>();

        // First, we select one representative by Approx class
        Map<Integer, RowImpl<I>> selectedRows = new HashMap<>();
        for (RowImpl<I> spRow : shortPrefixRows) {
            if (isCoAccessibleRow(spRow)) {
                int approxId = spRow.getApproxId();
                if (!selectedRows.containsKey(approxId)) {
                    selectedRows.put(approxId, spRow);
                }
            }
        }

        // Second, we define the transitions according to the selected representatives
        for (RowImpl<I> row : selectedRows.values()) {
            Map<I, Row<I>> transitions = new HashMap<>();
            for (I symbol : alphabet) {
                RowImpl<I> successor = row.getSuccessor(alphabet.getSymbolIndex(symbol));
                if (isCoAccessibleRow(successor)) {
                    RowImpl<I> selectedSuccessor = selectedRows.get(successor.getApproxId());
                    transitions.put(symbol, selectedSuccessor);
                }
            }
            canonicalRows.put(row, transitions);
        }

        return canonicalRows;
    }

    /**
     * Gets a list with only the outputs of the given row.
     * 
     * @param row The row.
     * @return A list with booleans
     */
    @Override
    public List<Boolean> rowContents(Row<I> row) {
        RowImpl<I> r = allRows.get(row.getRowId());
        List<Boolean> contents = new ArrayList<>(r.getOutputs().size());
        for (int i = 0; i < r.getOutputs().size(); i++) {
            contents.add(r.getOutputs().get(i));
        }
        return contents;
    }

    @Override
    public Boolean cellContents(Row<I> row, int columnId) {
        return rowContents(row).get(columnId);
    }

    /**
     * Gets a list of pairs (output, counter value) for the given row, excluding the
     * "only for language" suffixes.
     * 
     * That is, it gives the actual contents of the row, over S.
     * 
     * @param row The row
     * @return The row contents
     */
    public List<PairCounterValueOutput<Boolean>> fullRowContents(Row<I> row) {
        RowImpl<I> r = allRows.get(row.getRowId());
        return r.getRowContents();
    }

    public PairCounterValueOutput<Boolean> fullCellContents(Row<I> row, int columnId) {
        return fullRowContents(row).get(columnId);
    }

    /**
     * Gets the list of pairs (output, counter value) for all suffixes, including
     * the "only for language" suffixes
     * 
     * @param row The row
     * @return The row contents
     */
    public List<PairCounterValueOutput<Boolean>> fullWholeRowContents(Row<I> row) {
        RowImpl<I> r = allRows.get(row.getRowId());
        return r.getWholeRowContents();
    }

    @Override
    public boolean isInitialConsistencyCheckRequired() {
        return true;
    }

    @Override
    public @Nullable Inconsistency<I> findInconsistency() {
        for (RowImpl<I> spRow : shortPrefixRows) {
            Set<RowImpl<I>> approxOfSpRow = getShortPrefixesInApprox(spRow);
            for (RowImpl<I> equivalentSpRow : approxOfSpRow) {
                for (I symbol : alphabet) {
                    RowImpl<I> successorSpRow = spRow.getSuccessor(alphabet.getSymbolIndex(symbol));
                    RowImpl<I> successorEquivalentRow = equivalentSpRow.getSuccessor(alphabet.getSymbolIndex(symbol));
                    Set<Integer> approxOfSuccessorSpRow = approx.get(successorSpRow.getApproxId());
                    if (!approxOfSuccessorSpRow.contains(successorEquivalentRow.getRowId())) {
                        return new Inconsistency<>(spRow, equivalentSpRow, symbol);
                    }
                }
            }
        }

        return null;
    }

    public @Nullable Inconsistency<I> findSigmaInconsistency() {
        return findInconsistency();
    }

    public @Nullable BottomInconsistency<I> findBottomInconsistency() {
        for (RowImpl<I> row : allRows) {
            // We only test the rows that are in the Approx(row) and that are not yet seen
            // in the loop.
            // That is, we only want rows with rowId strictly greater than the current rowId
            // @formatter:off
            Set<RowImpl<I>> approxOfRow = approx.get(row.getApproxId()).stream().
                filter(i -> row.getRowId() < i).
                map(i -> allRows.get(i)).
                collect(Collectors.toSet());
            // @formatter:on
            List<PairCounterValueOutput<Boolean>> rowContents = fullRowContents(row);
            for (RowImpl<I> rowInApprox : approxOfRow) {
                List<PairCounterValueOutput<Boolean>> rowInApproxContents = fullRowContents(rowInApprox);
                int suffixIndex = 0;
                for (int i = 0; i < numberOfSuffixes(); i++) {
                    if (!isSuffixOnlyForLanguage(i)) {
                        int rowCV = rowContents.get(suffixIndex).getCounterValue();
                        int rowInApproxCV = rowInApproxContents.get(suffixIndex).getCounterValue();
                        if ((rowCV == UNKNOWN_COUNTER_VALUE) != (rowInApproxCV == UNKNOWN_COUNTER_VALUE)) {
                            if (rowCV != UNKNOWN_COUNTER_VALUE) {
                                return new BottomInconsistency<>(row, rowInApprox, i);
                            } else {
                                return new BottomInconsistency<>(rowInApprox, row, i);
                            }
                        }
                        suffixIndex++;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Gets a witness that the cell for provided row and column is in the prefix of
     * the table.
     * 
     * The witness w is such that the {@code L_l(u) = 1}. If the cell is not in the
     * prefix, a {@link IllegalArgumentException} is thrown.
     * 
     * @param row         The row
     * @param suffixIndex The index of the column
     * @return An {@link ObservationTreeNode} that is the witness.
     */
    public ObservationTreeNode<I> getWitnessInPrefix(Row<I> row, int suffixIndex) {
        RowImpl<I> r = allRows.get(row.getRowId());
        ObservationTreeNode<I> node = r.getNode().getPrefix(getSuffix(suffixIndex), 0);
        if (node.getCounterValue() == UNKNOWN_COUNTER_VALUE) {
            throw new IllegalArgumentException(
                    "The provided row and suffixIndex are not in the prefix of the table " + row + " " + suffixIndex);
        }
        // BFS
        Queue<ObservationTreeNode<I>> toExplore = new LinkedList<>();
        toExplore.add(node);
        while (!toExplore.isEmpty()) {
            node = toExplore.poll();
            if (node.getOutput() && node.isInTable()) {
                return node;
            }
            for (I symbol : alphabet) {
                ObservationTreeNode<I> successor = node.getSuccessor(symbol);
                if (successor != null && successor.isInPrefix()) {
                    toExplore.add(successor);
                }
            }
        }
        return null;
    }

    public boolean isSuffixOnlyForLanguage(int suffixIndex) {
        return !classicalSuffixIndices.contains(suffixIndex);
    }

    private void setSuffixOnlyForLanguage(int suffixIndex, boolean onlyForLanguage) {
        if (!onlyForLanguage) {
            classicalSuffixIndices.add(suffixIndex);
        }
    }

    private void setSuffixesOnlyForLanguage(int startSuffixIndexInclusive, int endSuffixIndexExclusive,
            boolean onlyForLanguage) {
        if (!onlyForLanguage) {
            Set<Integer> range = IntStream.range(startSuffixIndexInclusive, endSuffixIndexExclusive).boxed()
                    .collect(Collectors.toSet());
            classicalSuffixIndices.addAll(range);
        }
    }

    public boolean isSuffixOnlyForLanguage(Word<I> suffix) {
        return isSuffixOnlyForLanguage(suffixes.indexOf(suffix));
    }

    private Set<RowImpl<I>> getShortPrefixesInApprox(RowImpl<I> row) {
        return getShortPrefixesInApprox(approx.get(row.getApproxId()));
    }

    private Set<RowImpl<I>> getShortPrefixesInApprox(Set<Integer> approxOfRow) {
        Set<Integer> intersectionApproxShortPrefixes = new HashSet<>(approxOfRow);
        // @formatter:off
        Set<RowImpl<I>> shortPrefixesInApprox = intersectionApproxShortPrefixes.stream().
            map(i -> allRows.get(i)).
            filter(r -> r.isShortPrefixRow()).
            collect(Collectors.toSet());
        // @formatter:on
        return shortPrefixesInApprox;
    }

    @Nullable
    private RowImpl<I> pickShortPrefixInApprox(Set<Integer> approxOfRow) {
        Set<RowImpl<I>> shortPrefixesInApprox = getShortPrefixesInApprox(approxOfRow);
        Optional<RowImpl<I>> spRow = shortPrefixesInApprox.stream().findAny();
        return spRow.orElse(null);
    }

    private boolean approxHasIntersectionWithShortPrefixes(Set<Integer> approxOfRow) {
        return pickShortPrefixInApprox(approxOfRow) != null;
    }

    @Override
    public @Nullable Row<I> findUnclosedRow() {
        // A row is unclosed if it is co-accessible and does not have a canonical row.
        for (RowImpl<I> lpRow : longPrefixRows) {
            Set<Integer> approxOfRow = approx.get(lpRow.getApproxId());
            if (!approxHasIntersectionWithShortPrefixes(approxOfRow)) {
                return lpRow;
            }
        }
        return null;
    }

    /**
     * Gets all the short prefix rows in the Approx class identified by
     * {@code approxId}
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
            RowImpl<I> r = allRows.get(current.getRowId());
            current = pickShortPrefixInApprox(approx.get(r.getApproxId()));
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

        alphabetSize = alphabet.size();

        for (Word<I> suffix : initialSuffixes) {
            if (suffixesSet.add(suffix)) {
                setSuffixOnlyForLanguage(numberOfSuffixes(), false);
                suffixes.add(suffix);
            }
        }

        // Initialize root of tree
        observationTreeRoot.initializeAsRoot(oracle, counterValueOracle);

        for (Word<I> sp : initialShortPrefixes) {
            RowImpl<I> row = createSpRow(sp);
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

        return updateApproxSets();
    }

    @Override
    public List<List<Row<I>>> addSuffixes(Collection<? extends Word<I>> newSuffixes,
            MembershipOracle<I, Boolean> oracle) {
        if (!addSuffixesInternal(newSuffixes, oracle)) {
            return Collections.emptyList();
        }

        return updateApproxSets();
    }

    private boolean addSuffixesInternal(Collection<? extends Word<I>> newSuffixes,
            MembershipOracle<I, Boolean> oracle) {
        List<Word<I>> newSuffixList = new ArrayList<>();
        List<Integer> existingSuffixesIndices = new ArrayList<>();
        for (Word<I> suffix : newSuffixes) {
            if (suffixesSet.add(suffix)) {
                newSuffixList.add(suffix);
            } else {
                int suffixIndex = suffixes.indexOf(suffix);
                if (isSuffixOnlyForLanguage(suffixIndex)) {
                    existingSuffixesIndices.add(suffixIndex);
                    setSuffixOnlyForLanguage(suffixIndex, false);
                }
            }
        }

        if (newSuffixList.isEmpty() && existingSuffixesIndices.isEmpty()) {
            return false;
        }

        int oldNumberOfSuffixes = numberOfSuffixes();
        setSuffixesOnlyForLanguage(oldNumberOfSuffixes, oldNumberOfSuffixes + newSuffixList.size(), false);
        this.suffixes.addAll(newSuffixList);

        // For the suffixes that were already known but were used only for language, we
        // have to update the tree to retrieve the counter values
        if (!existingSuffixesIndices.isEmpty()) {
            // Note that we first need to update all rows before updating the sameOutputs
            // mapping.
            // Indeed, we first have to correctly set all the counter values
            for (RowImpl<I> row : allRows) {
                ObservationTreeNode<I> node = observationTreeRoot.getPrefix(row.getLabel(), 0);
                for (int suffixIndex : existingSuffixesIndices) {
                    ObservationTreeNode<I> nodeForSuffix = node.getPrefix(getSuffix(suffixIndex), 0);
                    nodeForSuffix.oneCellNowUsesNodeForCounterValue(counterValueOracle, counterLimit);
                }
            }

            for (RowImpl<I> row : allRows) {
                outputsOfRowChanged(row);
            }
        }

        for (RowImpl<I> row : allRows) {
            createTreeNodes(row, newSuffixList, oracle);
        }

        return true;
    }

    public List<List<Row<I>>> addSuffixesOnlyForLanguage(Collection<? extends Word<I>> newSuffixes,
            MembershipOracle<I, Boolean> oracle) {
        List<Word<I>> newSuffixList = new ArrayList<>();
        for (Word<I> suffix : newSuffixes) {
            if (suffixesSet.add(suffix)) {
                newSuffixList.add(suffix);
            }
        }

        if (newSuffixList.isEmpty()) {
            return Collections.emptyList();
        }

        int oldNumberOfSuffixes = numberOfSuffixes();
        setSuffixesOnlyForLanguage(oldNumberOfSuffixes, oldNumberOfSuffixes + newSuffixList.size(), true);
        this.suffixes.addAll(newSuffixList);

        for (RowImpl<I> row : allRows) {
            createTreeNodes(row, newSuffixList, oracle);
        }

        return updateApproxSets();
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
        return updateApproxSets();
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
        return updateApproxSets();
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

                // We need to re-compute the approximation set for the short row, too, as it may
                // happen than the new successor belongs in that set
                sameOutputsToUpdateApprox.add(spRow.getSameOutputsId());
            }

            return updateApproxSets();
        } else {
            return Collections.emptyList();
        }
    }

    public boolean isInInitialClass(Row<I> row) {
        // We know that '0' is always the row id for epsilon.
        // So, if the class of row contains 0, it means row is in the same class as
        // epsilon.
        RowImpl<I> r = allRows.get(row.getRowId());
        return approx.getOrDefault(r.getApproxId(), Collections.emptySet()).contains(0);
    }

    private List<List<Row<I>>> updateApproxSets() {
        Map<List<PairCounterValueOutput<Boolean>>, List<Row<I>>> unclosed = new HashMap<>();

        Set<RowImpl<I>> rowsToUpdate = new HashSet<>();
        for (int sameOutputsId : sameOutputsToUpdateApprox) {
            rowsToUpdate.addAll(sameOutputs.get(sameOutputsId));
        }

        Map<Integer, Set<Integer>> newApprox = new HashMap<>();
        Map<Set<Integer>, Integer> approxToApproxId = new HashMap<>();
        Set<Integer> freeApproxIds = new HashSet<>();
        freeApproxIds.addAll(approx.keySet());

        // We construct the new Approx classes (and canonical rows) by first copying the
        // information that is not modified.
        // We then compute only what is needed

        // We copy the classes that do not have to be modified
        for (RowImpl<I> row : allRows) {
            int approxId = NO_APPROX;
            if (!rowsToUpdate.contains(row)) {
                approxId = row.getApproxId();
                if (!newApprox.containsKey(row.getApproxId())) {
                    Set<Integer> approxOfRow = approx.get(approxId);
                    newApprox.put(approxId, approxOfRow);
                    approxToApproxId.put(approxOfRow, approxId);
                    freeApproxIds.remove(approxId);
                }
            }
        }

        // We compute the new approx sets
        for (RowImpl<I> row : rowsToUpdate) {
            Set<Integer> approxOfRow = computeApprox(row);

            // If the intersection between the computed Approx and the set of short prefixes
            // is empty, it means the row is unclosed.
            // Notice that this is not possible for short prefixes
            if (row.isShortPrefixRow()) {
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
            } else {
                int approxId;
                if (!approxHasIntersectionWithShortPrefixes(approxOfRow)) {
                    List<PairCounterValueOutput<Boolean>> contents = row.getRowContents();
                    if (!unclosed.containsKey(contents)) {
                        unclosed.put(contents, new ArrayList<>());
                    }
                    unclosed.get(contents).add(row);
                    approxId = NO_APPROX;
                } else {
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
            }
        }

        approx = newApprox;
        return new ArrayList<>(unclosed.values());
    }

    private Set<Integer> computeApprox(RowImpl<I> row) {
        // For all row v, v is in Approx(u) if
        // 1. row and v have exactly the same boolean outputs
        // 2. For every separator s, (the counter value of (v s) !=
        // UNKNOWN_COUNTER_VALUE AND the counter value of (u s) !=
        // UNKNOWN_COUNTER_VALUE) implies that the counter values are equal.
        // Here, an approx set contains the ids of the rows
        // Note that sameOutputs is constructed such that 1. is satisfied

        Set<Integer> approx = new HashSet<>();

        List<PairCounterValueOutput<Boolean>> rowContents = row.getRowContents();

        Set<RowImpl<I>> rowsToConsider = sameOutputs.get(row.getSameOutputsId());
        for (RowImpl<I> potentialRow : rowsToConsider) {
            List<PairCounterValueOutput<Boolean>> potentialRowContents = potentialRow.getRowContents();
            boolean isApprox = true;
            for (int i = 0; i < rowContents.size(); i++) {
                PairCounterValueOutput<Boolean> rowCell = rowContents.get(i);
                PairCounterValueOutput<Boolean> potentialCell = potentialRowContents.get(i);
                int rowCounterValue = rowCell.getCounterValue();
                int potentialCounterValue = potentialCell.getCounterValue();
                if (rowCounterValue != UNKNOWN_COUNTER_VALUE && potentialCounterValue != UNKNOWN_COUNTER_VALUE
                        && rowCounterValue != potentialCounterValue) {
                    isApprox = false;
                    break;
                }
            }

            if (isApprox) {
                approx.add(potentialRow.getRowId());
            }
        }
        return approx;
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
        int suffixIndex = row.numberOfSuffixes();

        for (int i = 0; i < suffixes.size(); i++) {
            ObservationTreeNode<I> node = prefixNode.addSuffixInTable(suffixes.get(i), 0, oracle, counterValueOracle,
                    counterLimit, row, suffixIndex++);
            row.addSuffix(node);
        }

        outputsOfRowChanged(row);
    }

    private RowImpl<I> createSpRow(Word<I> prefix) {
        ObservationTreeNode<I> node = observationTreeRoot.getPrefix(prefix, 0);
        RowImpl<I> row = new RowImpl<>(numRows++, prefix, node, this, alphabet.size());
        allRows.add(row);
        rowMap.put(prefix, row);
        shortPrefixRows.add(row);
        return row;
    }

    private RowImpl<I> createLpRow(Word<I> prefix) {
        ObservationTreeNode<I> node = observationTreeRoot.getPrefix(prefix, 0);
        RowImpl<I> row = new RowImpl<>(numRows++, prefix, node, this);
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

        sameOutputsToUpdateApprox.add(row.getSameOutputsId());
    }

    private boolean isCoAccessibleRow(Row<I> row) {
        PairCounterValueOutput<Boolean> cellContents = fullCellContents(row, 0);
        return cellContents.getCounterValue() != UNKNOWN_COUNTER_VALUE;
    }

    ObservationTreeNode<I> getObservationTreeRoot() {
        return observationTreeRoot;
    }

    void outputsOfRowChanged(RowImpl<I> row) {
        List<Boolean> outputs = row.getOutputs();
        int currentId = row.getSameOutputsId();
        int newId = sameOutputsIds.getOrDefault(outputs, NO_ID);

        // If the outputs did not actually change, we have nothing to do
        if (newId == currentId && newId != NO_ID) {
            return;
        }

        // If the row already has an id, we remove the row from the associated sets
        if (currentId != NO_ID) {
            sameOutputs.get(currentId).remove(row);
            if (sameOutputs.get(currentId).isEmpty()) {
                sameOutputs.remove(currentId);
                sameOutputsIds.values().remove(currentId);
                freeSameOutputsIds.add(currentId);
                // Since the set is now empty, we do not have to re-compute Approx for it
                sameOutputsToUpdateApprox.remove(currentId);
            } else {
                sameOutputsToUpdateApprox.add(currentId);
            }
        }

        if (newId == NO_ID) {
            if (freeSameOutputsIds.isEmpty()) {
                newId = sameOutputsIds.size();
            } else {
                Iterator<Integer> itr = freeSameOutputsIds.iterator();
                newId = itr.next();
                itr.remove();
            }

            sameOutputs.put(newId, new HashSet<>());
            sameOutputsIds.put(outputs, newId);
        }
        row.setSameOutputsId(newId);
        sameOutputs.get(newId).add(row);
        sameOutputsToUpdateApprox.add(newId);
    }

    void changedCounterValue(RowImpl<I> row) {
        if (row.getSameOutputsId() != NO_ID) {
            sameOutputsToUpdateApprox.add(row.getSameOutputsId());
        }
    }
}
