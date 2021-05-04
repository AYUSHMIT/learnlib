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

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.StratifiedObservationTable;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.VPDAlphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * A {@link StratifiedObservationTable} where the layers are defined by the
 * counter value of the prefixes.
 * 
 * There is a layer for each counter value between zero and the counter limit.
 * Each layer has its own sets of prefixes and suffixes such that the
 * concatenation of a prefix and a suffix has a counter value of zero.
 * 
 * Two rows are equivalent iff both prefixes have exactly the same counter value
 * and contents.
 * 
 * For the theoretical details, see the unpublished paper "Learning Visibly
 * One-Counter Automata" by D.Neider and C. Löding (2010).
 * 
 * @param <I> Input alphabet type
 * @param <D> Output type
 * @author Gaëtan Staquet
 */
public final class StratifiedObservationTableWithCounterValues<I, D> implements StratifiedObservationTable<I, D> {

    private class StratifiedTable {
        private final List<RowImpl<I>> shortPrefixRows = new ArrayList<>();
        private final List<RowImpl<I>> longPrefixRows = new ArrayList<>();
        private final Map<Integer, RowImpl<I>> canonicalRows = new HashMap<>();
        private final Map<Word<I>, RowImpl<I>> rowMap = new HashMap<>();
        private final Map<Integer, RowImpl<I>> allRows = new HashMap<>();

        private final Map<Integer, List<D>> rowContents = new HashMap<>();
        private final Map<List<D>, Integer> rowContentIds = new HashMap<>();
        // We store the rows by the content id in order to update the canonical rows
        private final Map<Integer, List<RowImpl<I>>> rowByContentId = new HashMap<>();
        // Since the content id of a row may change upon time (when increasing the
        // counter limit), we store a set of ids that used ot be in use but are now
        // free.
        private final Set<Integer> freeRowIds = new HashSet<>();

        private final List<Word<I>> suffixes = new ArrayList<>();
        private final Set<Word<I>> suffixesSet = new HashSet<>();

        private final int counterValue;

        StratifiedTable(int counterValue) {
            this.counterValue = counterValue;
        }

        void initializeShortPrefixes(List<? extends Word<I>> initialShortPrefixes,
                List<? extends Word<I>> initialSuffixes, MembershipOracle<I, D> oracle) {
            for (Word<I> suffix : initialSuffixes) {
                if (suffixesSet.add(suffix)) {
                    suffixes.add(suffix);
                }
            }

            int numSuffixes = suffixes.size();
            int numPrefixes = alphabet.size() * initialShortPrefixes.size() + 1;

            List<DefaultQuery<I, D>> queries = new ArrayList<>(numPrefixes * numSuffixes);

            for (Word<I> sp : initialShortPrefixes) {
                createSpRow(sp);
                buildQueries(queries, sp, suffixes);
            }

            oracle.processQueries(queries);

            Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

            for (RowImpl<I> spRow : shortPrefixRows) {
                List<D> rowContents = new ArrayList<>(numSuffixes);
                fetchResults(queryIt, rowContents, numSuffixes);
                if (!processContents(spRow, rowContents, true)) {
                    initialConsistencyCheckRequired = true;
                }
            }
        }

        void initializeLongPrefixes(MembershipOracle<I, D> oracle) {
            createLongPrefixes(shortPrefixRows, oracle);
        }

        private void createLongPrefixes(List<RowImpl<I>> shortPrefixRows, MembershipOracle<I, D> oracle) {
            for (RowImpl<I> spRow : shortPrefixRows) {
                Word<I> shortPrefix = spRow.getLabel();
                for (int i = 0; i < alphabet.size(); i++) {
                    I symbol = alphabet.getSymbol(i);
                    Word<I> longPrefix = shortPrefix.append(symbol);
                    int counterOperation = OCAUtil.counterOperation(symbol, alphabet);
                    int lpCounterValue = counterValue + counterOperation;
                    if (0 <= lpCounterValue && lpCounterValue <= counterLimit) {
                        StratifiedTable table = stratifiedTables.get(lpCounterValue);
                        RowImpl<I> longPrefixRow = table.addLongPrefix(longPrefix, oracle);
                        spRow.setSuccessor(i, longPrefixRow);
                    }
                }
            }
        }

        void toShortPrefixes(List<RowImpl<I>> lpRows, MembershipOracle<I, D> oracle) {
            List<RowImpl<I>> freshSpRows = new ArrayList<>();
            List<RowImpl<I>> rowsToExtend = new ArrayList<>();

            for (RowImpl<I> row : lpRows) {
                if (row.isShortPrefixRow()) {
                    if (row.hasContents()) {
                        continue;
                    }
                    freshSpRows.add(row);
                    rowsToExtend.add(row);
                } else {
                    makeShort(row);
                    if (!row.hasContents()) {
                        freshSpRows.add(row);
                    }
                    rowsToExtend.add(row);
                }
            }

            int numSuffixes = numberOfSuffixes();
            int numFreshSpRows = freshSpRows.size();

            List<DefaultQuery<I, D>> queries = new ArrayList<>(numFreshSpRows * numSuffixes);
            buildRowQueries(queries, freshSpRows, suffixes);
            oracle.processQueries(queries);

            Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

            for (RowImpl<I> row : freshSpRows) {
                List<D> contents = new ArrayList<>(numSuffixes);
                fetchResults(queryIt, contents, numSuffixes);
                processContents(row, contents, true);
            }

            createLongPrefixes(rowsToExtend, oracle);
        }

        RowImpl<I> addLongPrefix(Word<I> longPrefix, MembershipOracle<I, D> oracle) {
            RowImpl<I> longPrefixRow = rowMap.get(longPrefix);
            if (longPrefixRow == null) {
                longPrefixRow = createLpRow(longPrefix);
                List<DefaultQuery<I, D>> queries = new ArrayList<>(suffixes.size());
                buildQueries(queries, longPrefix, suffixes);
                oracle.processQueries(queries);
                List<D> contents = new ArrayList<>(suffixes.size());
                fetchResults(queries.iterator(), contents, suffixes.size());
                processContents(longPrefixRow, contents, false);
            }

            return longPrefixRow;
        }

        void addSuffixes(List<? extends Word<I>> newSuffixes, MembershipOracle<I, D> oracle) {
            List<Word<I>> newSuffixList = new ArrayList<>();
            for (Word<I> suffix : newSuffixes) {
                if (suffixesSet.add(suffix)) {
                    newSuffixList.add(suffix);
                }
            }

            if (newSuffixList.isEmpty()) {
                return;
            }

            int numNewSuffixes = newSuffixList.size();
            int rowCount = numberOfRows();

            List<DefaultQuery<I, D>> queries = new ArrayList<>(rowCount * numNewSuffixes);

            for (RowImpl<I> row : shortPrefixRows) {
                buildQueries(queries, row.getLabel(), newSuffixList);
            }
            for (RowImpl<I> row : longPrefixRows) {
                buildQueries(queries, row.getLabel(), newSuffixList);
            }

            oracle.processQueries(queries);

            int oldSuffixCount = numberOfSuffixes();
            this.suffixes.addAll(newSuffixList);

            Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();
            for (RowImpl<I> row : shortPrefixRows) {
                List<D> currentContents = rowContents(row);
                List<D> newContents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
                newContents.addAll(currentContents.subList(0, oldSuffixCount));
                fetchResults(queryIt, newContents, numNewSuffixes);
                processContents(row, newContents, true);
            }
            for (RowImpl<I> row : longPrefixRows) {
                List<D> currentContents = rowContents(row);
                List<D> newContents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
                newContents.addAll(currentContents.subList(0, oldSuffixCount));
                fetchResults(queryIt, newContents, numNewSuffixes);
                processContents(row, newContents, false);
            }
        }

        @Nullable
        Inconsistency<I> findInconsistency() {
            final Map<Integer, Row<I>> canonicalRows = new HashMap<>();
            final VPDAlphabet<I> alphabet = getInputAlphabet();

            for (Row<I> spRow : getShortPrefixRows()) {
                int contentId = spRow.getRowContentId();

                if (!canonicalRows.containsKey(contentId)) {
                    canonicalRows.put(contentId, spRow);
                } else {
                    Row<I> canRow = canonicalRows.get(contentId);

                    for (int i = 0; i < alphabet.size(); i++) {
                        Row<I> spSuccessorRow = spRow.getSuccessor(i);
                        Row<I> canSuccessorRow = canRow.getSuccessor(i);

                        if (spSuccessorRow == null && canSuccessorRow == null) {
                            continue;
                        } else if ((spSuccessorRow == null && canSuccessorRow != null)
                                || (spSuccessorRow != null && canSuccessorRow == null)) {
                            return new Inconsistency<>(canRow, spRow, alphabet.getSymbol(i));
                        } else {
                            int spSuccessorContent = spRow.getSuccessor(i).getRowContentId();
                            int canSuccessorContent = canRow.getSuccessor(i).getRowContentId();
                            if (spSuccessorContent != canSuccessorContent) {
                                return new Inconsistency<>(canRow, spRow, alphabet.getSymbol(i));
                            }
                        }
                    }
                }
            }

            return null;
        }

        int numberOfRows() {
            return allRows.size();
        }

        int numberOfSuffixes() {
            return suffixes.size();
        }

        int numberOfDistinctRows() {
            return rowContents.size();
        }

        List<D> rowContents(Row<I> row) {
            return this.rowContents.get(row.getRowContentId());
        }

        List<Word<I>> getSuffixes() {
            return Collections.unmodifiableList(suffixes);
        }

        Collection<Row<I>> getShortPrefixRows() {
            return Collections.unmodifiableCollection(shortPrefixRows);
        }

        Collection<Row<I>> getLongPrefixRows() {
            return Collections.unmodifiableCollection(longPrefixRows);
        }

        public Collection<Row<I>> getAllRows() {
            List<Row<I>> rows = new ArrayList<>();
            rows.addAll(allRows.values());
            return rows;
        }

        boolean isInitialized() {
            return shortPrefixRows.size() != 0 || longPrefixRows.size() != 0;
        }

        RowImpl<I> getCanonicalRow(Row<I> row) {
            return canonicalRows.getOrDefault(row.getRowContentId(), null);
        }

        List<List<Row<I>>> seekUnclosedRows() {
            Map<Integer, List<Row<I>>> unclosed = new HashMap<>();
            for (RowImpl<I> row : longPrefixRows) {
                int contentId = row.getRowContentId();
                if (unclosed.containsKey(contentId)) {
                    unclosed.get(contentId).add(row);
                } else {
                    Row<I> canonical = getCanonicalRow(row);
                    if (canonical == null) {
                        List<Row<I>> unc = new ArrayList<>();
                        unc.add(row);
                        unclosed.put(contentId, unc);
                    }
                }
            }
            return new ArrayList<>(unclosed.values());
        }

        void addShortPrefixes(List<? extends Word<I>> shortPrefixes, MembershipOracle<I, D> oracle) {
            List<RowImpl<I>> newShortPrefixes = new ArrayList<>();

            for (Word<I> sp : shortPrefixes) {
                RowImpl<I> row = rowMap.get(sp);
                if (row != null) {
                    if (row.isShortPrefixRow()) {
                        continue;
                    }
                } else {
                    row = createSpRow(sp);
                }
                newShortPrefixes.add(row);
            }

            toShortPrefixes(newShortPrefixes, oracle);
        }

        void addAlphabetSymbol(I symbol, final MembershipOracle<I, D> oracle) {
            final int counterOperation = OCAUtil.counterOperation(symbol, alphabet);
            final int inputIdx = alphabet.getSymbolIndex(symbol);

            for (RowImpl<I> prefix : shortPrefixRows) {
                prefix.ensureInputCapacity(alphabetSize);
                final Word<I> newLongPrefix = prefix.getLabel().append(symbol);
                final int counterValueLongPrefix = counterValue + counterOperation;

                if (0 <= counterValueLongPrefix && counterValueLongPrefix <= counterLimit) {
                    RowImpl<I> longPrefixRow = stratifiedTables.get(counterValueLongPrefix).addLongPrefix(newLongPrefix,
                            oracle);
                    prefix.setSuccessor(inputIdx, longPrefixRow);
                }
            }
        }

        private RowImpl<I> createSpRow(Word<I> prefix) {
            RowImpl<I> row = StratifiedObservationTableWithCounterValues.this.createSpRow(prefix, counterValue);
            allRows.put(row.getRowId(), row);
            shortPrefixRows.add(row);
            rowMap.put(prefix, row);
            return row;
        }

        private RowImpl<I> createLpRow(Word<I> prefix) {
            RowImpl<I> row = StratifiedObservationTableWithCounterValues.this.createLpRow(prefix, counterValue);
            allRows.put(row.getRowId(), row);
            longPrefixRows.add(row);
            rowMap.put(prefix, row);
            return row;
        }

        private void makeShort(RowImpl<I> row) {
            StratifiedObservationTableWithCounterValues.this.makeShort(row);
            longPrefixRows.remove(row);
            shortPrefixRows.add(row);

            if (row.hasContents()) {
                int cid = row.getRowContentId();
                if (canonicalRows.get(cid) == null) {
                    canonicalRows.put(cid, row);
                }
            }
        }

        private boolean processContents(RowImpl<I> row, List<D> rowContents, boolean makeCanonical) {
            if (row.hasContents()) {
                int currentId = row.getRowContentId();
                List<D> currentContents = rowContents(row);
                rowByContentId.get(currentId).remove(row);

                if (canonicalRows.get(currentId) == row) {
                    canonicalRows.put(currentId, null);
                }

                if (rowByContentId.get(currentId).size() == 0) {
                    this.rowContents.remove(currentId);
                    rowContentIds.remove(currentContents);
                    freeRowIds.add(currentId);
                    rowByContentId.remove(currentId);
                } else if (canonicalRows.get(currentId) == null) {
                    for (RowImpl<I> r : rowByContentId.get(currentId)) {
                        if (r.isShortPrefixRow()) {
                            canonicalRows.put(currentId, r);
                        }
                    }
                }
            }

            int contentId = rowContentIds.getOrDefault(rowContents, NO_ENTRY);
            boolean added = false;

            if (contentId == NO_ENTRY) {
                if (freeRowIds.size() != 0) {
                    contentId = freeRowIds.iterator().next();
                    freeRowIds.remove(contentId);
                } else {
                    contentId = numberOfDistinctRows();
                }
                rowContentIds.put(rowContents, contentId);
                this.rowContents.put(contentId, rowContents);
                rowByContentId.put(contentId, new ArrayList<>());
                added = true;
                if (makeCanonical) {
                    canonicalRows.put(contentId, row);
                } else {
                    canonicalRows.put(contentId, null);
                }
            }

            if (!rowByContentId.get(contentId).contains(row)) {
                rowByContentId.get(contentId).add(row);
            }
            row.setRowContentId(contentId);
            return added;
        }
    }

    private static final int NO_ENTRY = -1;

    private final VPDAlphabet<I> alphabet;
    private int alphabetSize;

    private final List<StratifiedTable> stratifiedTables = new ArrayList<>();
    private final List<RowImpl<I>> allShortPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> allLongPrefixRows = new ArrayList<>();
    private final Map<Integer, RowImpl<I>> allRows = new HashMap<>();

    private final List<Word<I>> allSuffixes = new ArrayList<>();
    private final Set<Word<I>> allSuffixesSet = new HashSet<>();

    private boolean initialConsistencyCheckRequired = false;

    private int numRows;

    private int counterLimit;

    private static <I, D> void buildQueries(List<DefaultQuery<I, D>> queryList, Word<I> prefix,
            List<? extends Word<I>> suffixes) {
        for (Word<I> suffix : suffixes) {
            queryList.add(new DefaultQuery<>(prefix, suffix));
        }
    }

    private static <I, D> void buildRowQueries(List<DefaultQuery<I, D>> queryList, List<? extends Row<I>> rows,
            List<? extends Word<I>> suffixes) {
        for (Row<I> row : rows) {
            buildQueries(queryList, row.getLabel(), suffixes);
        }
    }

    private static <I> boolean checkInitialPrefixClosed(List<List<Word<I>>> initialShortPrefixes) {
        Set<Word<I>> prefixes = new HashSet<>();
        for (List<Word<I>> pref : initialShortPrefixes) {
            prefixes.addAll(pref);
        }

        for (List<Word<I>> initialPrefixes : initialShortPrefixes) {
            for (Word<I> pref : initialPrefixes) {
                if (!pref.isEmpty() && !prefixes.contains(pref.prefix(-1))) {
                    return false;
                }
            }
        }

        return true;
    }

    private static <I, D> void fetchResults(Iterator<DefaultQuery<I, D>> queryIt, List<D> output, int numSuffixes) {
        for (int i = 0; i < numSuffixes; i++) {
            DefaultQuery<I, D> query = queryIt.next();
            output.add(query.getOutput());
        }
    }

    public StratifiedObservationTableWithCounterValues(VPDAlphabet<I> alphabet) {
        this.alphabet = alphabet;
        this.alphabetSize = alphabet.size();
    }

    @Override
    public VPDAlphabet<I> getInputAlphabet() {
        return alphabet;
    }

    @Override
    public Collection<Row<I>> getShortPrefixRows() {
        return Collections.unmodifiableCollection(allShortPrefixRows);
    }

    @Override
    public Collection<Row<I>> getShortPrefixRows(int layer) {
        return stratifiedTables.get(layer).getShortPrefixRows();
    }

    @Override
    public Collection<Row<I>> getLongPrefixRows() {
        return Collections.unmodifiableCollection(allLongPrefixRows);
    }

    @Override
    public Collection<Row<I>> getLongPrefixRows(int layer) {
        return stratifiedTables.get(layer).getLongPrefixRows();
    }

    @Override
    public Row<I> getRow(int idx) {
        return allRows.get(idx);
    }

    @Override
    public int numberOfDistinctRows() {
        int number = 0;
        for (int i = 0; i <= counterLimit; i++) {
            number += stratifiedTables.get(i).numberOfDistinctRows();
        }
        return number;
    }

    @Override
    public int numberOfDistinctRows(int layer) {
        return stratifiedTables.get(layer).numberOfDistinctRows();
    }

    @Override
    public int numberOfLayers() {
        return counterLimit + 1;
    }

    @Override
    public List<Word<I>> getSuffixes() {
        return Collections.unmodifiableList(allSuffixes);
    }

    @Override
    public List<Word<I>> getSuffixes(int layer) {
        return stratifiedTables.get(layer).getSuffixes();
    }

    @Override
    public List<D> rowContents(Row<I> row) {
        return stratifiedTables.get(getCounterValueOfRow(row)).rowContents(row);
    }

    @Override
    public Word<I> transformAccessSequence(Word<I> word) {
        Row<I> current = allShortPrefixRows.get(0);
        assert current != null;

        for (I symbol : word) {
            current = getRowSuccessor(current, symbol);
            if (current == null) {
                return null;
            }
            current = stratifiedTables.get(getCounterValueOfRow(current)).getCanonicalRow(current);
            assert current != null;
        }

        return current.getLabel();
    }

    @Override
    public boolean isAccessSequence(Word<I> word) {
        Row<I> current = allShortPrefixRows.get(0);

        for (I symbol : word) {
            current = getRowSuccessor(current, symbol);
            if (current == null || !isCanonical(current)) {
                return false;
            }
        }

        return true;
    }

    private boolean isCanonical(Row<I> row) {
        if (!row.isShortPrefixRow()) {
            return false;
        }
        return stratifiedTables.get(getCounterValueOfRow(row)).getCanonicalRow(row) == row;
    }

    @Override
    public @Nullable Inconsistency<I> findInconsistency() {
        for (int i = 0; i <= counterLimit; i++) {
            Inconsistency<I> inconsistency = stratifiedTables.get(i).findInconsistency();
            if (inconsistency != null) {
                return inconsistency;
            }
        }
        return null;
    }

    /**
     * Initializes the table.
     * 
     * The prefixes and suffixes must be grouped by the layer in which they must be
     * added. That is, words in {@code initialShortPrefixes.get(0)} and in
     * {@code initialSuffixes.get(0)} will be added to the layer zero, and so on.
     * 
     * @param initialShortPrefixes The initial short prefixes
     * @param initialSuffixes      The initial suffixes
     * @param oracle               The membership oracle
     * @return Unclosed rows
     */
    public List<List<Row<I>>> initialize(List<List<Word<I>>> initialShortPrefixes, List<List<Word<I>>> initialSuffixes,
            MembershipOracle<I, D> oracle) {
        if (!allRows.isEmpty()) {
            throw new IllegalStateException("Called initialize, but there are already rows present");
        }

        if (!checkInitialPrefixClosed(initialShortPrefixes)) {
            throw new IllegalArgumentException("Initial short prefixes are not prefix-closed");
        }

        if (!initialShortPrefixes.get(0).get(0).isEmpty()) {
            throw new IllegalArgumentException("First initial short prefix MUST be the empty word!");
        }

        counterLimit = initialShortPrefixes.size() - 1;

        for (int i = 0; i < initialShortPrefixes.size(); i++) {
            for (Word<I> suffix : initialSuffixes.get(i)) {
                if (allSuffixesSet.add(suffix)) {
                    allSuffixes.add(suffix);
                }
            }

            StratifiedTable table = new StratifiedTable(i);
            table.initializeShortPrefixes(initialShortPrefixes.get(i), initialSuffixes.get(i), oracle);
            stratifiedTables.add(table);
        }

        for (int i = 0; i <= counterLimit; i++) {
            stratifiedTables.get(i).initializeLongPrefixes(oracle);
        }

        return seekUnclosedRows();
    }

    public boolean isInitialized() {
        return !allRows.isEmpty();
    }

    public boolean isInitialConsistencyCheckRequired() {
        return initialConsistencyCheckRequired;
    }

    /**
     * Increases the counter limit of the table and initializes the new layers with
     * the provided short prefixes and suffixes.
     * 
     * The short prefixes and suffixes must be grouped by the layer in which they
     * must go. That is, all words in {@code shortPrefixes.get(0)} and
     * {@code suffixes.get(0)} will be added to the layer corresponding to the
     * counter value of the short prefixes, and so on.
     * 
     * @param newCounterLimit The new counter limit
     * @param shortPrefixes   The short prefixes
     * @param suffixes        The suffixes
     * @param oracle          The membership oracle
     * @return The unclosed rows
     */
    public List<List<Row<I>>> increaseCounterLimit(int newCounterLimit, List<List<? extends Word<I>>> shortPrefixes,
            List<List<? extends Word<I>>> suffixes, MembershipOracle<I, D> oracle) {
        int oldCounterLimit = this.counterLimit;
        this.counterLimit = newCounterLimit;

        for (int i = oldCounterLimit + 1; i <= newCounterLimit; i++) {
            StratifiedTable table = new StratifiedTable(i);
            stratifiedTables.add(table);
        }

        List<Word<I>> unusedShortPrefixes = new ArrayList<>();
        for (int i = 0; i < shortPrefixes.size(); i++) {
            int counterValue = OCAUtil.computeCounterValue(shortPrefixes.get(i).get(0), alphabet);
            StratifiedTable table = stratifiedTables.get(counterValue);
            if (!table.isInitialized()) {
                table.initializeShortPrefixes(shortPrefixes.get(i), suffixes.get(i), oracle);
            } else {
                table.addSuffixes(suffixes.get(i), oracle);
                Collection<? extends Word<I>> sp = shortPrefixes.get(i);
                unusedShortPrefixes.addAll(sp);
            }
        }

        addShortPrefixes(unusedShortPrefixes, oracle);

        // We must update the table for oldCounterLimit as well because call symbols are
        // now allowed
        for (int i = oldCounterLimit; i <= newCounterLimit; i++) {
            stratifiedTables.get(i).initializeLongPrefixes(oracle);
        }

        return seekUnclosedRows();
    }

    /**
     * Add new suffixes in the table.
     * 
     * The suffixes must be grouped by the layer in which they must be added.
     * 
     * @param newSuffixes The suffixes
     * @param oracle      The membership oracle
     * @return The unclosed rows
     */
    public List<List<Row<I>>> addSuffixes(Map<Integer, List<? extends Word<I>>> newSuffixes,
            MembershipOracle<I, D> oracle) {
        for (Map.Entry<Integer, List<? extends Word<I>>> entry : newSuffixes.entrySet()) {
            stratifiedTables.get(entry.getKey()).addSuffixes(entry.getValue(), oracle);
        }
        return seekUnclosedRows();
    }

    /**
     * Adds a new suffix in the table.
     * 
     * The suffix is added in the layer {@code counterValue}.
     * 
     * @param newSuffix    The suffix to add
     * @param counterValue The counter value of the layer
     * @param oracle       The membership oracle
     * @return The unclosed rows
     */
    public List<List<Row<I>>> addSuffix(Word<I> newSuffix, int counterValue, MembershipOracle<I, D> oracle) {
        stratifiedTables.get(counterValue).addSuffixes(Collections.singletonList(newSuffix), oracle);
        return seekUnclosedRows();
    }

    /**
     * Adds short prefixes in the table.
     * 
     * The layer in which each prefix must be added is computed from the prefix.
     * That is, a word with counter value {@code i} is added in the layer {@code i}.
     * 
     * @param shortPrefixes The short prefixes.
     * @param oracle        The membership oracle
     * @return The unclosed rows
     */
    public List<List<Row<I>>> addShortPrefixes(List<? extends Word<I>> shortPrefixes, MembershipOracle<I, D> oracle) {
        Map<Integer, List<Word<I>>> wordsByCounterValue = new HashMap<>();
        for (Word<I> sp : shortPrefixes) {
            int counterValue = OCAUtil.computeCounterValue(sp, alphabet);
            if (!wordsByCounterValue.containsKey(counterValue)) {
                wordsByCounterValue.put(counterValue, new ArrayList<>());
            }
            wordsByCounterValue.get(counterValue).add(sp);
        }

        for (Map.Entry<Integer, List<Word<I>>> entry : wordsByCounterValue.entrySet()) {
            stratifiedTables.get(entry.getKey()).addShortPrefixes(entry.getValue(), oracle);
        }

        return seekUnclosedRows();
    }

    public List<List<Row<I>>> toShortPrefixes(List<Row<I>> lpRows, MembershipOracle<I, D> oracle) {
        Map<Integer, List<RowImpl<I>>> rowsByCounterValue = new HashMap<>();
        for (Row<I> row : lpRows) {
            int counterValue = OCAUtil.computeCounterValue(row.getLabel(), alphabet);
            if (!rowsByCounterValue.containsKey(counterValue)) {
                rowsByCounterValue.put(counterValue, new ArrayList<>());
            }
            rowsByCounterValue.get(counterValue).add(allRows.get(row.getRowId()));
        }

        for (Map.Entry<Integer, List<RowImpl<I>>> entry : rowsByCounterValue.entrySet()) {
            stratifiedTables.get(entry.getKey()).toShortPrefixes(entry.getValue(), oracle);
        }
        return seekUnclosedRows();
    }

    public List<List<Row<I>>> addAlphabetSymbol(I symbol, final MembershipOracle<I, D> oracle) {
        if (!alphabet.containsSymbol(symbol)) {
            Alphabets.toGrowingAlphabetOrThrowException(alphabet).addSymbol(symbol);
        }

        final int newAlphabetSize = alphabet.size();

        if (isInitialized() && this.alphabetSize < newAlphabetSize) {
            this.alphabetSize = newAlphabetSize;
            for (int i = 0; i <= counterLimit; i++) {
                stratifiedTables.get(counterLimit).addAlphabetSymbol(symbol, oracle);
            }

            return seekUnclosedRows();
        } else {
            return Collections.emptyList();
        }
    }

    public int getCounterValueOfRow(Row<I> row) {
        RowImpl<I> r = allRows.get(row.getRowId());
        return r.getCounterValue();
    }

    @Override
    public Collection<Row<I>> getAllRows() {
        List<Row<I>> rows = new ArrayList<>();
        rows.addAll(allRows.values());
        return rows;
    }

    @Override
    public Collection<Row<I>> getAllRows(int layer) {
        return stratifiedTables.get(layer).getAllRows();
    }

    private List<List<Row<I>>> seekUnclosedRows() {
        List<List<Row<I>>> unclosed = new ArrayList<>();
        for (int i = 0; i <= counterLimit; i++) {
            unclosed.addAll(stratifiedTables.get(i).seekUnclosedRows());
        }
        return unclosed;
    }

    private RowImpl<I> createSpRow(Word<I> prefix, int counterValue) {
        RowImpl<I> newRow = new RowImpl<>(prefix, numRows++, counterValue, alphabet.size());
        allRows.put(newRow.getRowId(), newRow);
        allShortPrefixRows.add(newRow);
        return newRow;
    }

    private RowImpl<I> createLpRow(Word<I> prefix, int counterValue) {
        RowImpl<I> newRow = new RowImpl<>(prefix, numRows++, counterValue);
        allRows.put(newRow.getRowId(), newRow);
        int idx = allLongPrefixRows.size();
        allLongPrefixRows.add(newRow);
        newRow.setLpIndex(idx);
        return newRow;
    }

    private void makeShort(RowImpl<I> row) {
        if (row.isShortPrefixRow()) {
            return;
        }

        int lastIdx = allLongPrefixRows.size() - 1;
        RowImpl<I> last = allLongPrefixRows.get(lastIdx);
        int rowIdx = row.getLpIndex();
        allLongPrefixRows.remove(lastIdx);
        if (last != row) {
            allLongPrefixRows.set(rowIdx, last);
            last.setLpIndex(rowIdx);
        }

        allShortPrefixRows.add(row);
        row.makeShort(alphabet.size());
    }
}
