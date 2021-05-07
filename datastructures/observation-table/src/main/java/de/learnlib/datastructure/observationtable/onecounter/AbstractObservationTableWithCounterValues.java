package de.learnlib.datastructure.observationtable.onecounter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.MutableObservationTable;
import de.learnlib.datastructure.observationtable.OTUtils;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * Abstract base class for an observation table the observations are pairs
 * (output, counter value).
 * 
 * When adding new prefixes, suffixes or a symbol, the table is filled in two
 * passes:
 * <ol>
 * <li>The first pass asks the membership queries</li>
 * <li>The second pass computes the counter value of the cells</li>
 * </ol>
 * 
 * How to compute the counter value for a given prefix and suffix, the
 * definition of the canonical rows, and whether the table is closed and
 * consistent depend on the concrete implementation.
 * 
 * @param <I> Input alphabet type
 * @param <D> Output type
 * @author Gaëtan Staquet
 */
public abstract class AbstractObservationTableWithCounterValues<I, D> implements MutableObservationTable<I, D> {

    private final static int NO_ENTRY = -1;

    private final List<RowImpl<I>> shortPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> longPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> allRows = new ArrayList<>();
    private int numRows = 0;

    private final Map<Word<I>, RowImpl<I>> rowMap = new HashMap<>();

    private final Map<Integer, List<PairCounterValueOutput<D>>> rowContents = new HashMap<>();
    private final Map<List<PairCounterValueOutput<D>>, Integer> rowContentsIds = new HashMap<>();
    private final Map<Integer, Set<RowImpl<I>>> rowsByContentsId = new HashMap<>();
    private final Set<Integer> freeRowContentsIds = new HashSet<>();

    private final List<Word<I>> suffixes = new ArrayList<>();
    private final Set<Word<I>> suffixesSet = new HashSet<>();

    private final Alphabet<I> alphabet;
    private int alphabetSize;

    private int counterLimit = 0;

    private boolean initialConsistencyCheckRequired;

    private static <I, D> void buildRowQueries(List<DefaultQuery<I, D>> queryList, List<? extends Row<I>> rows,
            List<? extends Word<I>> suffixes) {
        for (Row<I> row : rows) {
            buildQueries(queryList, row.getLabel(), suffixes);
        }
    }

    private static <I, D> void buildQueries(List<DefaultQuery<I, D>> queryList, Word<I> prefix,
            List<? extends Word<I>> suffixes) {
        for (Word<I> suffix : suffixes) {
            queryList.add(new DefaultQuery<>(prefix, suffix));
        }
    }

    private static <I> boolean checkInitialPrefixClosed(List<Word<I>> initialShortPrefixes) {
        Set<Word<I>> prefixes = new HashSet<>(initialShortPrefixes);
        for (Word<I> pref : initialShortPrefixes) {
            if (!pref.isEmpty() && !prefixes.contains(pref.prefix(-1))) {
                return false;
            }
        }

        return true;
    }

    protected AbstractObservationTableWithCounterValues(Alphabet<I> alphabet) {
        this.alphabet = alphabet;
        this.alphabetSize = alphabet.size();
    }

    /**
     * Fins all the unclosed rows.
     * 
     * Two rows are in the same list iff they are potential short rows for an
     * equivalence class.
     * 
     * @return The unclosed rows
     */
    protected abstract List<List<Row<I>>> findUnclosedRows();

    /**
     * Decides whether the given prefix and suffix can change the stored counter
     * value.
     * 
     * This function is called before {@link getCounterValue} in order to know
     * whether it is useful to compute a new counter value.
     * 
     * @param prefix              The prefix
     * @param suffix              The suffix
     * @param currentCounterValue The currently stored counter value
     * @return {@code true} iff a new counter value should be computed,
     *         {@code false} otherwise.
     */
    protected abstract boolean shouldChangeCounterValue(Word<I> prefix, Word<I> suffix, int currentCounterValue);

    /**
     * Decides whether a new membership query should be asked for the given prefix
     * and suffix, given what is currently stored in the table.
     * 
     * @param prefix      The prefix
     * @param suffix      The suffix
     * @param currentCell The current cell
     * @return {@code true} iff a new membership query should be asked,
     *         {@code false} otherwise.
     */
    protected abstract boolean shouldChangeOutput(Word<I> prefix, Word<I> suffix,
            PairCounterValueOutput<D> currentCell);

    /**
     * Computes the counter value to associate with the given prefix and suffix.
     * 
     * @param prefix The prefix
     * @param suffix The suffix
     * @return The counter value to set in the cell for prefix and suffix.
     */
    protected abstract int getCounterValue(Word<I> prefix, Word<I> suffix);

    /**
     * Allows the concrete implementation to update internal storage when a row is
     * added or modified.
     * 
     * @param row                    The row
     * @param rowContents            The (new) contents
     * @param canChangeCounterValues Whether the function is allowed to call
     *                               {@link updateCounterValues}.
     */
    protected abstract void processContentsMembershipQueries(Row<I> row, List<PairCounterValueOutput<D>> rowContents,
            boolean canChangeCounterValues);

    /**
     * Updates the canonical rows.
     */
    protected abstract void updateCanonicalRows();

    /**
     * Gets the canonical row of the given row, if it exists.
     * 
     * @param row The row
     * @return The canonical row, or {@code null}.
     */
    public abstract @Nullable Row<I> getCanonicalRow(Row<I> row);

    /**
     * Gets a list with all the canonical rows.
     * 
     * @return The canonical rows.
     */
    public abstract List<Row<I>> getCanonicalRows();

    @Override
    public abstract @Nullable Inconsistency<I> findInconsistency();

    @Override
    public abstract @Nullable Row<I> findUnclosedRow();

    public int getCounterLimit() {
        return counterLimit;
    }

    @Override
    public int numberOfShortPrefixRows() {
        return shortPrefixRows.size();
    }

    @Override
    public Collection<Row<I>> getShortPrefixRows() {
        return Collections.unmodifiableCollection(shortPrefixRows);
    }

    @Override
    public int numberOfLongPrefixRows() {
        return longPrefixRows.size();
    }

    @Override
    public Collection<Row<I>> getLongPrefixRows() {
        return Collections.unmodifiableCollection(longPrefixRows);
    }

    @Override
    public Collection<Row<I>> getAllRows() {
        return Collections.unmodifiableCollection(allRows);
    }

    @Override
    public int numberOfSuffixes() {
        return suffixes.size();
    }

    @Override
    public List<Word<I>> getSuffixes() {
        return Collections.unmodifiableList(suffixes);
    }

    @Override
    public Word<I> getSuffix(int index) {
        return suffixes.get(index);
    }

    @Override
    public Alphabet<I> getInputAlphabet() {
        return alphabet;
    }

    @Override
    public Row<I> getRow(int idx) {
        return allRows.get(idx);
    }

    @Override
    public List<D> rowContents(Row<I> row) {
        List<PairCounterValueOutput<D>> contents = fullRowContents(row);
        return contents.stream().map(c -> c.getOutput()).collect(Collectors.toList());
    }

    public List<PairCounterValueOutput<D>> fullRowContents(Row<I> row) {
        return rowContents.get(row.getRowContentId());
    }

    @Override
    public D cellContents(Row<I> row, int columnId) {
        return fullCellContents(row, columnId).getOutput();
    }

    public PairCounterValueOutput<D> fullCellContents(Row<I> row, int columnId) {
        return fullRowContents(row).get(columnId);
    }

    @Override
    public int numberOfDistinctRows() {
        return rowContents.size();
    }

    @Override
    public boolean isInitialized() {
        return rowContents.size() != 0;
    }

    @Override
    public boolean isInitialConsistencyCheckRequired() {
        return initialConsistencyCheckRequired;
    }

    @Override
    public List<List<Row<I>>> initialize(List<Word<I>> initialShortPrefixes, List<Word<I>> initialSuffixes,
            MembershipOracle<I, D> oracle) {
        if (!allRows.isEmpty()) {
            throw new IllegalStateException("Called initialize, but there are already rows present");
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

        int numPrefixes = alphabet.size() * initialShortPrefixes.size() + 1;
        int numSuffixes = suffixes.size();

        List<DefaultQuery<I, D>> queries = new ArrayList<>(numPrefixes * numSuffixes);

        // PASS A: membership queries
        // PASS A.1: Add short prefix rows
        for (Word<I> sp : initialShortPrefixes) {
            createSpRow(sp);
            buildQueries(queries, sp, suffixes);
        }

        // PASS A.2: Add missing long prefix rows
        for (RowImpl<I> spRow : shortPrefixRows) {
            Word<I> sp = spRow.getLabel();
            for (int i = 0; i < alphabet.size(); i++) {
                I symbol = alphabet.getSymbol(i);
                Word<I> lp = sp.append(symbol);
                RowImpl<I> successorRow = rowMap.get(lp);
                if (successorRow == null) {
                    successorRow = createLpRow(lp);
                    buildQueries(queries, lp, suffixes);
                }
                spRow.setSuccessor(i, successorRow);
            }
        }

        oracle.processQueries(queries);
        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

        // Since we do not want to add contents where the counter values are not yet
        // set, we temporarily store them locally
        // Later, when the counter values are filled, the contents are really added in
        // the table
        List<List<PairCounterValueOutput<D>>> temporarySpContents = new ArrayList<>();
        List<List<PairCounterValueOutput<D>>> temporaryLpContents = new ArrayList<>();

        for (RowImpl<I> sp : shortPrefixRows) {
            List<PairCounterValueOutput<D>> rowContents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, rowContents, numSuffixes);
            temporarySpContents.add(rowContents);
            // Since the table does not yet have actual rows, we do not want to let the
            // concrete implementation to try to update the counter values (as these
            // values will be set later)
            processContentsMembershipQueries(sp, rowContents, false);
        }
        for (RowImpl<I> spRow : shortPrefixRows) {
            for (int i = 0; i < alphabet.size(); i++) {
                RowImpl<I> successorRow = spRow.getSuccessor(i);
                if (successorRow.isShortPrefixRow()) {
                    continue;
                }
                List<PairCounterValueOutput<D>> rowContents = new ArrayList<>(numSuffixes);
                fetchResults(queryIt, rowContents, numSuffixes);
                temporaryLpContents.add(rowContents);
                processContentsMembershipQueries(successorRow, rowContents, false);
            }
        }

        // PASS B: counter values
        for (int i = 0; i < shortPrefixRows.size(); i++) {
            RowImpl<I> spRow = shortPrefixRows.get(i);
            List<PairCounterValueOutput<D>> rowContents = temporarySpContents.get(i);
            fillCounterValues(spRow, rowContents, 0);
            spRow.setCounterValue(rowContents.get(0).getCounterValue());

            if (!processContents(spRow, rowContents)) {
                initialConsistencyCheckRequired = true;
            }
        }

        Iterator<List<PairCounterValueOutput<D>>> iteratorLpContents = temporaryLpContents.iterator();
        for (RowImpl<I> spRow : shortPrefixRows) {
            for (int i = 0; i < alphabet.size(); i++) {
                RowImpl<I> successorRow = spRow.getSuccessor(i);
                if (successorRow.isShortPrefixRow()) {
                    continue;
                }

                List<PairCounterValueOutput<D>> rowContents = iteratorLpContents.next();
                fillCounterValues(successorRow, rowContents, 0);
                successorRow.setCounterValue(rowContents.get(0).getCounterValue());

                processContents(successorRow, rowContents);
            }
        }

        updateCanonicalRows();

        return findUnclosedRows();
    }

    @Override
    public List<List<Row<I>>> addSuffixes(Collection<? extends Word<I>> newSuffixes, MembershipOracle<I, D> oracle) {
        if (!addSuffixesInternal(newSuffixes, oracle)) {
            return Collections.emptyList();
        }

        updateCanonicalRows();

        return findUnclosedRows();
    }

    private boolean addSuffixesInternal(Collection<? extends Word<I>> newSuffixes, MembershipOracle<I, D> oracle) {
        List<Word<I>> newSuffixList = new ArrayList<>();
        for (Word<I> suffix : newSuffixes) {
            if (suffixesSet.add(suffix)) {
                newSuffixList.add(suffix);
            }
        }

        if (newSuffixList.isEmpty()) {
            return false;
        }

        int numNewSuffixes = newSuffixList.size();
        int numOldSuffixes = numberOfSuffixes();

        this.suffixes.addAll(newSuffixList);

        int numSpRows = numberOfShortPrefixRows();
        int numLpRows = numberOfLongPrefixRows();
        int rowCount = numSpRows + numLpRows;

        // PASS A: membership queries
        List<DefaultQuery<I, D>> queries = new ArrayList<>(rowCount * numNewSuffixes);
        buildRowQueries(queries, shortPrefixRows, newSuffixList);
        buildRowQueries(queries, longPrefixRows, newSuffixList);

        oracle.processQueries(queries);
        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

        List<List<PairCounterValueOutput<D>>> temporarySpContents = new ArrayList<>(numSpRows);
        List<List<PairCounterValueOutput<D>>> temporaryLpContents = new ArrayList<>(numLpRows);

        for (RowImpl<I> row : shortPrefixRows) {
            List<PairCounterValueOutput<D>> rowContents = fullRowContents(row);

            List<PairCounterValueOutput<D>> newContents = new ArrayList<>(numOldSuffixes + numNewSuffixes);
            newContents.addAll(rowContents.subList(0, numOldSuffixes));
            fetchResults(queryIt, newContents, numNewSuffixes);

            temporarySpContents.add(newContents);
            processContentsMembershipQueries(row, newContents, true);
        }
        for (RowImpl<I> row : longPrefixRows) {
            List<PairCounterValueOutput<D>> rowContents = fullRowContents(row);

            List<PairCounterValueOutput<D>> newContents = new ArrayList<>(numOldSuffixes + numNewSuffixes);
            newContents.addAll(rowContents.subList(0, numOldSuffixes));
            fetchResults(queryIt, newContents, numNewSuffixes);

            temporaryLpContents.add(newContents);
            processContentsMembershipQueries(row, newContents, true);
        }

        // PASS B: counter values
        for (int i = 0; i < shortPrefixRows.size(); i++) {
            RowImpl<I> row = shortPrefixRows.get(i);
            List<PairCounterValueOutput<D>> rowContents = temporarySpContents.get(i);

            fillCounterValues(row, rowContents, numOldSuffixes);
            processContents(row, rowContents);
        }

        for (int i = 0; i < longPrefixRows.size(); i++) {
            RowImpl<I> row = longPrefixRows.get(i);
            List<PairCounterValueOutput<D>> rowContents = temporaryLpContents.get(i);

            fillCounterValues(row, rowContents, numOldSuffixes);
            processContents(row, rowContents);
        }

        return true;
    }

    @Override
    public List<List<Row<I>>> addShortPrefixes(List<? extends Word<I>> shortPrefixes, MembershipOracle<I, D> oracle) {
        List<Row<I>> toSpRows = new ArrayList<>();

        for (Word<I> sp : shortPrefixes) {
            RowImpl<I> row = rowMap.get(sp);
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

    private void addShortPrefixesInternal(List<? extends Word<I>> shortPrefixes, MembershipOracle<I, D> oracle) {
        List<Row<I>> toSpRows = new ArrayList<>();

        for (Word<I> sp : shortPrefixes) {
            RowImpl<I> row = rowMap.get(sp);
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
    }

    @Override
    public List<List<Row<I>>> toShortPrefixes(List<Row<I>> lpRows, MembershipOracle<I, D> oracle) {
        toShortPrefixesInternal(lpRows, oracle);

        updateCanonicalRows();
        return findUnclosedRows();
    }

    private void toShortPrefixesInternal(List<Row<I>> lpRows, MembershipOracle<I, D> oracle) {
        List<RowImpl<I>> freshSpRows = new ArrayList<>();
        List<RowImpl<I>> freshLpRows = new ArrayList<>();

        for (Row<I> r : lpRows) {
            final RowImpl<I> row = allRows.get(r.getRowId());
            if (row.isShortPrefixRow()) {
                if (row.hasContents()) {
                    continue;
                }
                freshSpRows.add(row);
            } else {
                makeShort(row);
                if (!row.hasContents()) {
                    freshSpRows.add(row);
                }
            }

            Word<I> prefix = row.getLabel();
            for (int i = 0; i < alphabet.size(); i++) {
                I symbol = alphabet.getSymbol(i);
                Word<I> lp = prefix.append(symbol);
                RowImpl<I> lpRow = rowMap.get(lp);
                if (lpRow == null) {
                    lpRow = createLpRow(lp);
                    freshLpRows.add(lpRow);
                }
                row.setSuccessor(i, lpRow);
            }
        }

        int numSuffixes = suffixes.size();
        int numFreshSpRows = freshSpRows.size();
        int numFreshLpRows = freshLpRows.size();
        int numFreshRows = numFreshSpRows + numFreshLpRows;

        // PASS A: membership queries
        List<DefaultQuery<I, D>> queries = new ArrayList<>(numFreshRows * numSuffixes);
        buildRowQueries(queries, freshSpRows, suffixes);
        buildRowQueries(queries, freshLpRows, suffixes);

        oracle.processQueries(queries);
        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

        List<List<PairCounterValueOutput<D>>> temporarySpContents = new ArrayList<>(numFreshRows);
        List<List<PairCounterValueOutput<D>>> temporaryLpContents = new ArrayList<>(numFreshLpRows);

        for (int i = 0; i < freshSpRows.size(); i++) {
            RowImpl<I> row = freshSpRows.get(i);
            List<PairCounterValueOutput<D>> rowContents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, rowContents, numSuffixes);
            temporarySpContents.add(rowContents);
            processContentsMembershipQueries(row, rowContents, true);
        }
        for (int i = 0; i < freshLpRows.size(); i++) {
            RowImpl<I> row = freshLpRows.get(i);
            List<PairCounterValueOutput<D>> rowContents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, rowContents, numSuffixes);
            temporaryLpContents.add(rowContents);
            processContentsMembershipQueries(row, rowContents, true);
        }

        // PASS B: counter values
        for (int i = 0; i < freshSpRows.size(); i++) {
            RowImpl<I> row = freshSpRows.get(i);
            List<PairCounterValueOutput<D>> rowContents = temporarySpContents.get(i);

            fillCounterValues(row, rowContents, 0);
            row.setCounterValue(rowContents.get(0).getCounterValue());
            processContents(row, rowContents);
        }

        for (int i = 0; i < freshLpRows.size(); i++) {
            RowImpl<I> row = freshLpRows.get(i);
            List<PairCounterValueOutput<D>> rowContents = temporaryLpContents.get(i);

            fillCounterValues(row, rowContents, 0);
            row.setCounterValue(rowContents.get(0).getCounterValue());
            processContents(row, rowContents);
        }
    }

    /**
     * Increases the counter limit of the table, then adds the provided short
     * prefixes and suffixes.
     * 
     * The table iterates over all rows to check whether the cells can be updated or
     * not. That is, if a cell can be updated, a new membership query is asked and a
     * (potentially) new counter value is computed.
     * 
     * Once this is done, the new prefixes and suffixes are added.
     * 
     * @param newCounterLimit  The new counter limit
     * @param newShortPrefixes The short prefixes to add
     * @param newSuffixes      The suffixes to add
     * @param oracle           The membership oracle
     * @return The unclosed rows
     */
    public List<List<Row<I>>> increaseCounterLimit(int newCounterLimit, List<Word<I>> newShortPrefixes,
            List<Word<I>> newSuffixes, MembershipOracle<I, D> oracle) {
        assert newCounterLimit > counterLimit;
        // PASS A: membership queries
        List<List<PairCounterValueOutput<D>>> temporaryContents = new ArrayList<>();

        final int numSuffixes = suffixes.size();
        for (RowImpl<I> row : allRows) {
            Word<I> prefix = row.getLabel();

            List<PairCounterValueOutput<D>> currentContents = fullRowContents(row);
            List<PairCounterValueOutput<D>> newContents = new ArrayList<>(numSuffixes);

            for (int i = 0; i < numSuffixes; i++) {
                Word<I> suffix = suffixes.get(i);
                PairCounterValueOutput<D> currentCell = currentContents.get(i);
                if (shouldChangeOutput(prefix, suffix, currentCell)) {
                    D newOutput = oracle.answerQuery(prefix, suffix);
                    int newCounterValue = getCounterValue(prefix, suffix);
                    PairCounterValueOutput<D> newCell = new PairCounterValueOutput<>(newOutput, newCounterValue);
                    newContents.add(newCell);
                } else {
                    newContents.add(currentCell);
                }
            }

            temporaryContents.add(newContents);
            processContentsMembershipQueries(row, newContents, false);
        }

        // PASS B: counter values
        for (int i = 0; i < allRows.size(); i++) {
            RowImpl<I> row = allRows.get(i);
            List<PairCounterValueOutput<D>> rowContents = temporaryContents.get(i);
            fillCounterValues(row, rowContents, 0);
            processContents(row, rowContents);
        }

        // We add the new prefixes and suffixes
        addShortPrefixesInternal(newShortPrefixes, oracle);
        addSuffixesInternal(newSuffixes, oracle);

        updateCanonicalRows();
        return findUnclosedRows();
    }

    @Override
    public List<List<Row<I>>> addAlphabetSymbol(I symbol, MembershipOracle<I, D> oracle) {
        if (!alphabet.containsSymbol(symbol)) {
            Alphabets.toGrowingAlphabetOrThrowException(alphabet).addSymbol(symbol);
        }

        final int newAlphabetSize = alphabet.size();

        if (this.isInitialized() && this.alphabetSize < newAlphabetSize) {
            this.alphabetSize = newAlphabetSize;
            final int newSymbolIdx = alphabet.getSymbolIndex(symbol);

            final List<RowImpl<I>> newLongPrefixes = new ArrayList<>(shortPrefixRows.size());
            for (RowImpl<I> prefix : shortPrefixRows) {
                prefix.ensureInputCapacity(newAlphabetSize);
                final Word<I> newLp = prefix.getLabel().append(symbol);
                final RowImpl<I> newLpRow = createLpRow(newLp);
                newLongPrefixes.add(newLpRow);
                prefix.setSuccessor(newSymbolIdx, newLpRow);
            }

            final int numLongPrefixes = newLongPrefixes.size();
            final int numSuffixes = numberOfSuffixes();

            // PASS A: membership queries
            final List<DefaultQuery<I, D>> queries = new ArrayList<>(numLongPrefixes * numSuffixes);
            buildRowQueries(queries, newLongPrefixes, suffixes);
            oracle.processQueries(queries);

            final Iterator<DefaultQuery<I, D>> queryIterator = queries.iterator();
            final List<List<PairCounterValueOutput<D>>> temporaryContents = new ArrayList<>(numLongPrefixes);
            for (RowImpl<I> row : newLongPrefixes) {
                final List<PairCounterValueOutput<D>> contents = new ArrayList<>(numSuffixes);
                fetchResults(queryIterator, contents, numSuffixes);
                temporaryContents.add(contents);
                processContentsMembershipQueries(row, contents, true);
            }

            // PASS B: counter values
            for (int i = 0; i < numLongPrefixes; i++) {
                RowImpl<I> row = newLongPrefixes.get(i);
                List<PairCounterValueOutput<D>> rowContents = temporaryContents.get(i);
                fillCounterValues(row, rowContents, 0);
                processContents(row, rowContents);
            }

            return findUnclosedRows();
        } else {
            return Collections.emptyList();
        }
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

    /**
     * Creates an object implementing
     * {@code ObservationTable<I, OutputAndCounterValue>}.
     * 
     * The returned table can be used to nicely display its contents (see
     * {@link OTUtils}).
     * 
     * @return
     */
    public ObservationTable<I, PairCounterValueOutput<D>> toClassicObservationTable() {
        class Table implements ObservationTable<I, PairCounterValueOutput<D>> {

            private final AbstractObservationTableWithCounterValues<I, D> table;

            public Table(AbstractObservationTableWithCounterValues<I, D> table) {
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
            public List<PairCounterValueOutput<D>> rowContents(Row<I> row) {
                return table.fullRowContents(row);
            }

        }

        return new Table(this);
    }

    protected void updateCounterValues(Row<I> row) {
        final RowImpl<I> r = allRows.get(row.getRowId());
        // If the row does not yet have contents, then it means that row is currently
        // being initialized
        // Thus, it will have contents later.
        // However, it the row already has contents, then we update it
        if (r.hasContents()) {
            final List<PairCounterValueOutput<D>> rowContents = new ArrayList<>(fullRowContents(r));
            fillCounterValues(r, rowContents, 0);
            processContents(r, rowContents);
        }
    }

    private RowImpl<I> createSpRow(Word<I> prefix) {
        RowImpl<I> row = new RowImpl<>(prefix, numRows++, -1, alphabet.size());
        allRows.add(row);
        rowMap.put(prefix, row);
        shortPrefixRows.add(row);
        return row;
    }

    private RowImpl<I> createLpRow(Word<I> prefix) {
        RowImpl<I> row = new RowImpl<>(prefix, numRows++, -1);
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

    private void fetchResults(Iterator<DefaultQuery<I, D>> queryIt, List<PairCounterValueOutput<D>> output,
            int numSuffixes) {
        for (int i = 0; i < numSuffixes; i++) {
            DefaultQuery<I, D> query = queryIt.next();
            int counterValue = getCounterValue(query.getPrefix(), query.getSuffix());
            output.add(new PairCounterValueOutput<D>(query.getOutput(), counterValue));
        }
    }

    private void fillCounterValues(RowImpl<I> row, List<PairCounterValueOutput<D>> rowContents, int firstSuffixIndex) {
        for (int i = firstSuffixIndex; i < rowContents.size(); i++) {
            PairCounterValueOutput<D> currentCell = rowContents.get(i);
            int counterValue = currentCell.getCounterValue();
            if (shouldChangeCounterValue(row.getLabel(), suffixes.get(i), counterValue)) {
                counterValue = getCounterValue(row.getLabel(), suffixes.get(i));
                PairCounterValueOutput<D> newCell = new PairCounterValueOutput<>(currentCell.getOutput(), counterValue);
                rowContents.set(i, newCell);
            }
        }
    }

    private boolean processContents(RowImpl<I> row, List<PairCounterValueOutput<D>> rowContents) {
        if (row.hasContents()) {
            int currentId = row.getRowContentId();
            List<PairCounterValueOutput<D>> currentContents = fullRowContents(row);
            rowsByContentsId.get(currentId).remove(row);

            if (rowsByContentsId.get(currentId).size() == 0) {
                this.rowContents.remove(currentId);
                freeRowContentsIds.add(currentId);
                rowsByContentsId.remove(currentId);
                rowContentsIds.remove(currentContents);
            }
        }

        int contentsId = rowContentsIds.getOrDefault(rowContents, NO_ENTRY);
        boolean added = false;

        if (contentsId == NO_ENTRY) {
            if (freeRowContentsIds.size() != 0) {
                Iterator<Integer> iterator = freeRowContentsIds.iterator();
                contentsId = iterator.next();
                iterator.remove();
            } else {
                contentsId = numberOfDistinctRows();
            }

            rowContentsIds.put(rowContents, contentsId);
            this.rowContents.put(contentsId, rowContents);
            rowsByContentsId.put(contentsId, new HashSet<>());

            added = true;
        }

        rowsByContentsId.get(contentsId).add(row);

        row.setRowContentId(contentsId);

        return added;
    }
}
