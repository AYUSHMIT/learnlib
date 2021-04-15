package de.learnlib.datastructure.observationtable;

import java.util.ArrayList;
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
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * An abstract observation table storing pairs (acceptance, counter value).
 * 
 * This table needs a counter value oracle on top of the membership oracle.
 * 
 * The table does not store a short prefix for the bin row. The definition of
 * the bin row depends on the actual implementation. See
 * {@link ObservationTableWithCounterValues} for an implementation for
 * {@link AutomatonWithCounterValues}.
 * 
 * @param <I> Input alphabet type
 * 
 * @author Gaëtan Staquet
 */
public abstract class GenericObservationTableWithCounterValues<I, D> implements MutableObservationTable<I, D> {

    /**
     * Stores a pair (output, counter value).
     */
    public static final class OutputAndCounterValue<D> {
        private final D output;
        private final Integer counterValue;

        public OutputAndCounterValue(D output, Integer counterValue) {
            this.output = output;
            this.counterValue = counterValue;
        }

        public D getOutput() {
            return output;
        }

        public Integer getCounterValue() {
            return counterValue;
        }

        @Override
        public String toString() {
            return "(" + output + ", " + counterValue + ")";
        }

        @Override
        public int hashCode() {
            return Objects.hash(output, counterValue);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj == null) {
                return false;
            }

            if (obj.getClass() != getClass()) {
                return false;
            }

            OutputAndCounterValue<?> o = (OutputAndCounterValue<?>) obj;

            return Objects.equals(getOutput(), o.getOutput()) && Objects.equals(getCounterValue(), o.getCounterValue());
        }
    }

    private static final int NO_ENTRY = -1;
    protected static final int NO_COUNTER_VALUE = -1;

    private final List<RowImpl<I>> shortPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> longPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> allRows = new ArrayList<>();
    private final Map<Integer, List<OutputAndCounterValue<D>>> allRowContents = new HashMap<>();
    private final Map<Integer, List<Row<I>>> rowByContentId = new HashMap<>();
    private final List<@Nullable RowImpl<I>> canonicalRows = new ArrayList<>();

    private final Map<List<OutputAndCounterValue<D>>, Integer> rowContentIds = new HashMap<>();
    private final Map<Word<I>, RowImpl<I>> rowMap = new HashMap<>();
    private final List<Word<I>> suffixes = new ArrayList<>();
    private final Set<Word<I>> suffixSet = new HashSet<>();

    // TODO: use a trie
    private final Set<Word<I>> prefixesOfL = new HashSet<>();

    private final Alphabet<I> alphabet;
    private int alphabetSize;
    private int numRows;
    private boolean initialConsistencyCheckRequired = false;

    private final MembershipOracle.CounterValueOracle<I> counterValueOracle;

    public GenericObservationTableWithCounterValues(Alphabet<I> alphabet,
            MembershipOracle.CounterValueOracle<I> counterValueOracle) {
        this.alphabet = alphabet;
        alphabetSize = alphabet.size();
        this.counterValueOracle = counterValueOracle;
    }

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

    private static <I> boolean checkPrefixClosed(Collection<? extends Word<I>> initialShortPrefixes) {
        Set<Word<I>> prefixes = new HashSet<>(initialShortPrefixes);

        for (Word<I> pref : initialShortPrefixes) {
            if (!pref.isEmpty() && !prefixes.contains(pref.prefix(-1))) {
                return false;
            }
        }

        return true;
    }

    private static <I, D> void fetchResults(Iterator<DefaultQuery<I, D>> queryIt, List<OutputAndCounterValue<D>> output,
            int numSuffixes) {
        for (int j = 0; j < numSuffixes; j++) {
            DefaultQuery<I, D> query = queryIt.next();
            output.add(new OutputAndCounterValue<>(query.getOutput(), null));
        }
    }

    protected abstract boolean isBinRow(Row<I> row);

    protected abstract boolean isAccepted(OutputAndCounterValue<D> outputAndCounterValue);

    @Override
    public List<List<Row<I>>> initialize(List<Word<I>> initialShortPrefixes, List<Word<I>> initialSuffixes,
            MembershipOracle<I, D> oracle) {
        if (!allRows.isEmpty()) {
            throw new IllegalStateException("Called initialize, but there are already rows present");
        }

        if (!checkPrefixClosed(initialShortPrefixes)) {
            throw new IllegalArgumentException("Initial short prefixes are not prefix-closed");
        }

        if (!initialShortPrefixes.get(0).isEmpty()) {
            throw new IllegalArgumentException("First initial short prefix MUST be the empty word!");
        }

        int numSuffixes = initialSuffixes.size();
        for (Word<I> suffix : initialSuffixes) {
            if (suffixSet.add(suffix)) {
                suffixes.add(suffix);
            }
        }

        int numPrefixes = alphabet.size() * initialShortPrefixes.size() + 1;

        // To fill the table, we need two passes: one for the membership queries, and
        // one for the counter values
        // PASS 1: Memberships
        List<DefaultQuery<I, D>> queries = new ArrayList<>(numPrefixes * numSuffixes);

        // PASS 1.1: Add short prefix rows
        for (Word<I> sp : initialShortPrefixes) {
            createSpRow(sp);
            buildQueries(queries, sp, suffixes);
        }

        // PASS 1.2: Add missing long prefix rows
        for (RowImpl<I> spRow : shortPrefixRows) {
            Word<I> sp = spRow.getLabel();
            for (int i = 0; i < alphabet.size(); i++) {
                I sym = alphabet.getSymbol(i);
                Word<I> lp = sp.append(sym);
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

        for (RowImpl<I> spRow : shortPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, rowContents, numSuffixes);
            if (!processContents(spRow, rowContents, true)) {
                initialConsistencyCheckRequired = true;
            }
        }

        for (RowImpl<I> spRow : shortPrefixRows) {
            for (int i = 0; i < alphabetSize; i++) {
                RowImpl<I> successorRow = spRow.getSuccessor(i);
                if (successorRow.isShortPrefixRow()) {
                    continue;
                }
                List<OutputAndCounterValue<D>> rowContents = new ArrayList<>(numSuffixes);
                fetchResults(queryIt, rowContents, numSuffixes);
                processContents(successorRow, rowContents, false);
            }
        }

        // PASS 2: the counter values.
        // We only ask counter values queries on words that are in the prefixes of L,
        // according to the table.
        for (RowImpl<I> spRow : shortPrefixRows) {
            List<OutputAndCounterValue<D>> newRowContents = new ArrayList<>(fullRowContents(spRow));
            for (int i = 0; i < numSuffixes; i++) {
                Word<I> word = spRow.getLabel().concat(suffixes.get(i));
                D output = newRowContents.get(i).getOutput();
                int counterValue = NO_COUNTER_VALUE;
                if (prefixesOfL.contains(word)) {
                    counterValue = counterValueOracle.answerQuery(word);
                }
                OutputAndCounterValue<D> newCellContent = new OutputAndCounterValue<>(output, counterValue);
                newRowContents.set(i, newCellContent);
            }
            if (!processContents(spRow, newRowContents, true)) {
                initialConsistencyCheckRequired = true;
            }
        }

        Map<Integer, List<Row<I>>> unclosed = new HashMap<>();

        for (RowImpl<I> lpRow : longPrefixRows) {
            List<OutputAndCounterValue<D>> newRowContents = new ArrayList<>(fullRowContents(lpRow));
            for (int i = 0; i < numSuffixes; i++) {
                Word<I> word = lpRow.getLabel().concat(suffixes.get(i));
                D output = newRowContents.get(i).getOutput();
                int counterValue = NO_COUNTER_VALUE;
                if (prefixesOfL.contains(word)) {
                    counterValue = counterValueOracle.answerQuery(word);
                }
                OutputAndCounterValue<D> newCellContent = new OutputAndCounterValue<>(output, counterValue);
                newRowContents.set(i, newCellContent);
            }
            if (processContents(lpRow, newRowContents, false) && !isBinRow(lpRow)) {
                unclosed.put(lpRow.getRowContentId(), new ArrayList<>());
            }

            if (unclosed.containsKey(lpRow.getRowContentId())) {
                unclosed.get(lpRow.getRowContentId()).add(lpRow);
            }
        }

        for (Map.Entry<Integer, List<Row<I>>> entry : checkForNewWordsInPrefixOfL().entrySet()) {
            unclosed.merge(entry.getKey(), entry.getValue(), (v1, v2) -> {
                List<Row<I>> row = new ArrayList<>(v1);
                row.addAll(v2);
                return row;
            });
        }

        return new ArrayList<>(unclosed.values());
    }

    @Override
    public boolean isInitialized() {
        return !allRows.isEmpty();
    }

    private RowImpl<I> createSpRow(Word<I> prefix) {
        RowImpl<I> newRow = new RowImpl<>(prefix, numRows++, alphabet.size());
        allRows.add(newRow);
        rowMap.put(prefix, newRow);
        shortPrefixRows.add(newRow);
        return newRow;
    }

    private RowImpl<I> createLpRow(Word<I> prefix) {
        RowImpl<I> newRow = new RowImpl<>(prefix, numRows++);
        allRows.add(newRow);
        rowMap.put(prefix, newRow);
        int idx = longPrefixRows.size();
        longPrefixRows.add(newRow);
        newRow.setLpIndex(idx);
        return newRow;
    }

    /**
     * If the rowContents is not yet in the table, creates a new content id.
     * 
     * It may happen that processing the contents leads to an unused id because the
     * rows that used that id now have different contents. In that case, the new id
     * is that unused id.
     * 
     * @param row           The row to update
     * @param rowContents   The new row contents
     * @param makeCanonical If true, the row is made canonical if the contents are
     *                      added in the table
     * @return True if the contents are added to the table (i.e., it is the first
     *         row with these contents).
     */
    private boolean processContents(RowImpl<I> row, List<OutputAndCounterValue<D>> rowContents, boolean makeCanonical) {
        int contentId;
        boolean added = false;
        contentId = rowContentIds.getOrDefault(rowContents, NO_ENTRY);
        if (contentId == NO_ENTRY) {
            if (row.hasContents()) {
                rowByContentId.get(row.getRowContentId()).remove(row);
                if (rowByContentId.get(row.getRowContentId()).size() == 0) {
                    contentId = row.getRowContentId();
                    allRowContents.remove(contentId);
                }
            }
            if (contentId == NO_ENTRY) {
                contentId = numberOfDistinctRows();
                rowByContentId.put(contentId, new ArrayList<>());
            }
            rowContentIds.put(rowContents, contentId);
            allRowContents.put(contentId, rowContents);
            added = true;
            if (makeCanonical) {
                canonicalRows.add(row);
            } else {
                canonicalRows.add(null);
            }
        }
        rowByContentId.get(contentId).add(row);
        row.setRowContentId(contentId);

        // We update the set of prefixes of L, according to the table
        for (int i = 0; i < numberOfSuffixes(); i++) {
            if (isAccepted(rowContents.get(i))) {
                Word<I> word = row.getLabel().concat(suffixes.get(i));
                for (Word<I> prefix : word.prefixes(false)) {
                    prefixesOfL.add(prefix);
                }
            }
        }

        return added;
    }

    private boolean isCanonical(Row<I> row) {
        if (!row.isShortPrefixRow()) {
            return false;
        }
        int contentId = row.getRowContentId();
        return canonicalRows.get(contentId) == row;
    }

    /**
     * Gives, if it exists, a short prefix row with the same row content than the
     * given row.
     * 
     * It is guaranteed that the function always returns the same representative, as
     * long as the table is not modified.
     * 
     * @param row The row
     * @return The representative for the row, or null if none exists.
     */
    public @Nullable Row<I> getRepresentativeForEquivalenceClass(Row<I> row) {
        if (isBinRow(row)) {
            return null;
        }

        return rowByContentId.get(row.getRowContentId()).get(0);
    }

    /**
     * Gives the set of representative short prefix rows.
     * 
     * It is guaranteed that the set is always the same, as long as the table is not
     * modified.
     * 
     * @return The set of representatives rows
     */
    public Set<Row<I>> getRepresentativeRows() {
        // @formatter:off
        return getShortPrefixRows().stream()
            .map(row -> getRepresentativeForEquivalenceClass(row))
            .filter(row -> row != null)
            .collect(Collectors.toSet());
        // @formatter:on
    }

    @Override
    public Alphabet<I> getInputAlphabet() {
        return alphabet;
    }

    @Override
    public Collection<Row<I>> getShortPrefixRows() {
        return Collections.unmodifiableList(shortPrefixRows);
    }

    @Override
    public Collection<Row<I>> getLongPrefixRows() {
        return Collections.unmodifiableList(longPrefixRows);
    }

    @Override
    public Row<I> getRow(int idx) {
        return allRows.get(idx);
    }

    @Override
    public int numberOfDistinctRows() {
        return allRowContents.size();
    }

    @Override
    public List<Word<I>> getSuffixes() {
        return suffixes;
    }

    @Override
    public List<D> rowContents(Row<I> row) {
        List<OutputAndCounterValue<D>> rowContents = fullRowContents(row);
        // formatter:off
        return rowContents.stream().map(contents -> contents.getOutput()).collect(Collectors.toList());
        // formatter:on
    }

    public List<OutputAndCounterValue<D>> fullRowContents(Row<I> row) {
        return allRowContents.get(row.getRowContentId());
    }

    public OutputAndCounterValue<D> fullCellContents(Row<I> row, int columnId) {
        return fullRowContents(row).get(columnId);
    }

    @Override
    public boolean isInitialConsistencyCheckRequired() {
        return initialConsistencyCheckRequired;
    }

    private Map<Integer, List<Row<I>>> checkForNewWordsInPrefixOfL() {
        Map<Integer, List<Row<I>>> unclosed = new HashMap<>();
        List<Word<I>> suffixes = getSuffixes();

        for (RowImpl<I> row : shortPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = new ArrayList<>(fullRowContents(row));
            boolean changed = false;
            for (int i = 0; i < suffixes.size(); i++) {
                OutputAndCounterValue<D> cell = rowContents.get(i);
                if (cell.getCounterValue() == NO_COUNTER_VALUE) {
                    Word<I> word = row.getLabel().concat(suffixes.get(i));
                    if (prefixesOfL.contains(word)) {
                        D output = cell.getOutput();
                        int counterValue = counterValueOracle.answerQuery(word);
                        rowContents.set(i, new OutputAndCounterValue<>(output, counterValue));
                        changed = true;
                    }
                }
            }

            if (changed) {
                processContents(row, rowContents, true);
            }
        }

        for (RowImpl<I> row : longPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < suffixes.size(); i++) {
                OutputAndCounterValue<D> cell = rowContents.get(i);
                Word<I> word = row.getLabel().concat(suffixes.get(i));
                if (cell.getCounterValue() == null || cell.getCounterValue() == NO_COUNTER_VALUE) {
                    if (prefixesOfL.contains(word)) {
                        D output = cell.getOutput();
                        int counterValue = counterValueOracle.answerQuery(word);
                        rowContents.set(i, new OutputAndCounterValue<>(output, counterValue));
                    }
                }
            }

            if (processContents(row, rowContents, false) && !isBinRow(row)) {
                unclosed.put(row.getRowContentId(), new ArrayList<>());
            }

            if (unclosed.containsKey(row.getRowContentId())) {
                unclosed.get(row.getRowContentId()).add(row);
            }
        }

        return unclosed;
    }

    @Override
    public List<List<Row<I>>> addSuffixes(Collection<? extends Word<I>> newSuffixes, MembershipOracle<I, D> oracle) {
        // we need a stable iteration order, and only List guarantees this
        List<Word<I>> newSuffixList = new ArrayList<>();
        for (Word<I> suffix : newSuffixes) {
            if (suffixSet.add(suffix)) {
                newSuffixList.add(suffix);
            }
        }

        if (newSuffixList.isEmpty()) {
            return Collections.emptyList();
        }

        int numNewSuffixes = newSuffixList.size();

        int numSpRows = shortPrefixRows.size();
        int rowCount = numSpRows + longPrefixRows.size();

        // PASS 1: membership queries
        List<DefaultQuery<I, D>> queries = new ArrayList<>(rowCount * numNewSuffixes);

        for (RowImpl<I> row : shortPrefixRows) {
            buildQueries(queries, row.getLabel(), newSuffixList);
        }

        for (RowImpl<I> row : longPrefixRows) {
            buildQueries(queries, row.getLabel(), newSuffixList);
        }

        oracle.processQueries(queries);

        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();
        int oldSuffixCount = suffixes.size();

        for (RowImpl<I> row : shortPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = allRowContents.get(row.getRowContentId());
            if (rowContents.size() == oldSuffixCount) {
                rowContentIds.remove(rowContents);
                fetchResults(queryIt, rowContents, numNewSuffixes);
                rowContentIds.put(rowContents, row.getRowContentId());
            } else {
                List<OutputAndCounterValue<D>> newContents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
                newContents.addAll(rowContents.subList(0, oldSuffixCount));
                fetchResults(queryIt, newContents, numNewSuffixes);
                processContents(row, newContents, true);
            }
        }

        numSpRows = numberOfDistinctRows();

        for (RowImpl<I> row : longPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = allRowContents.get(row.getRowContentId());
            if (rowContents.size() == oldSuffixCount) {
                rowContentIds.remove(rowContents);
                fetchResults(queryIt, rowContents, numNewSuffixes);
                rowContentIds.put(rowContents, row.getRowContentId());
            } else {
                List<OutputAndCounterValue<D>> newContents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
                newContents.addAll(rowContents.subList(0, oldSuffixCount));
                fetchResults(queryIt, newContents, numNewSuffixes);
                processContents(row, newContents, false);
            }
        }

        // PASS 2: counter values queries
        for (RowImpl<I> row : shortPrefixRows) {
            List<OutputAndCounterValue<D>> contents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
            contents.addAll(fullRowContents(row));
            for (int i = 0; i < numNewSuffixes; i++) {
                Word<I> word = row.getLabel().concat(newSuffixList.get(i));
                D output = contents.get(oldSuffixCount + i).getOutput();
                int counterValue = NO_COUNTER_VALUE;
                if (prefixesOfL.contains(word)) {
                    counterValue = counterValueOracle.answerQuery(word);
                }
                contents.set(oldSuffixCount + i, new OutputAndCounterValue<>(output, counterValue));
            }

            processContents(row, contents, true);
        }

        Map<Integer, List<Row<I>>> unclosed = new HashMap<>();
        for (RowImpl<I> row : longPrefixRows) {
            List<OutputAndCounterValue<D>> contents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
            contents.addAll(fullRowContents(row));
            for (int i = 0; i < numNewSuffixes; i++) {
                Word<I> word = row.getLabel().concat(newSuffixList.get(i));
                D output = contents.get(oldSuffixCount + i).getOutput();
                int counterValue = NO_COUNTER_VALUE;
                if (prefixesOfL.contains(word)) {
                    counterValue = counterValueOracle.answerQuery(word);
                }
                contents.set(oldSuffixCount + i, new OutputAndCounterValue<>(output, counterValue));
            }

            if (processContents(row, contents, false) && !isBinRow(row)) {
                unclosed.put(row.getRowContentId(), new ArrayList<>());
            }

            if (unclosed.containsKey(row.getRowContentId())) {
                unclosed.get(row.getRowContentId()).add(row);
            }
        }

        for (Map.Entry<Integer, List<Row<I>>> entry : checkForNewWordsInPrefixOfL().entrySet()) {
            unclosed.merge(entry.getKey(), entry.getValue(), (v1, v2) -> {
                List<Row<I>> row = new ArrayList<>(v1);
                row.addAll(v2);
                return row;
            });
        }

        this.suffixes.addAll(newSuffixList);

        return new ArrayList<>(unclosed.values());
    }

    @Override
    public List<List<Row<I>>> addShortPrefixes(List<? extends Word<I>> shortPrefixes, MembershipOracle<I, D> oracle) {
        List<Row<I>> newShortPrefixes = new ArrayList<>();

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

        return toShortPrefixes(newShortPrefixes, oracle);
    }

    @Override
    public List<List<Row<I>>> toShortPrefixes(List<Row<I>> lpRows, MembershipOracle<I, D> oracle) {
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

        // PASS 1: membership queries
        int numFreshRows = freshSpRows.size() + freshLpRows.size();
        List<DefaultQuery<I, D>> queries = new ArrayList<>(numFreshRows * numSuffixes);
        buildRowQueries(queries, freshSpRows, suffixes);
        buildRowQueries(queries, freshLpRows, suffixes);

        oracle.processQueries(queries);
        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

        for (RowImpl<I> row : freshSpRows) {
            List<OutputAndCounterValue<D>> contents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, contents, numSuffixes);
            processContents(row, contents, true);
        }

        for (RowImpl<I> row : freshLpRows) {
            List<OutputAndCounterValue<D>> contents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, contents, numSuffixes);
            processContents(row, contents, false);
        }

        // PASS 2: counter value queries
        for (RowImpl<I> row : freshSpRows) {
            List<OutputAndCounterValue<D>> contents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < numSuffixes; i++) {
                Word<I> word = row.getLabel().concat(suffixes.get(i));
                OutputAndCounterValue<D> cell = contents.get(i);
                D output = cell.getOutput();
                int counterValue = NO_COUNTER_VALUE;
                if (prefixesOfL.contains(word)) {
                    counterValue = counterValueOracle.answerQuery(word);
                }
                OutputAndCounterValue<D> newCell = new OutputAndCounterValue<>(output, counterValue);
                contents.set(i, newCell);
            }

            processContents(row, contents, true);
        }

        Map<Integer, List<Row<I>>> unclosed = new HashMap<>();

        for (RowImpl<I> row : freshLpRows) {
            List<OutputAndCounterValue<D>> contents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < numSuffixes; i++) {
                Word<I> word = row.getLabel().concat(suffixes.get(i));
                OutputAndCounterValue<D> cell = contents.get(i);
                D output = cell.getOutput();
                int counterValue = NO_COUNTER_VALUE;
                if (prefixesOfL.contains(word)) {
                    counterValue = counterValueOracle.answerQuery(word);
                }
                OutputAndCounterValue<D> newCell = new OutputAndCounterValue<>(output, counterValue);
                contents.set(i, newCell);
            }

            if (processContents(row, contents, false) && !isBinRow(row)) {
                unclosed.put(row.getRowContentId(), new ArrayList<>());
            }

            if (unclosed.containsKey(row.getRowContentId())) {
                unclosed.get(row.getRowContentId()).add(row);
            }
        }

        for (Map.Entry<Integer, List<Row<I>>> entry : checkForNewWordsInPrefixOfL().entrySet()) {
            unclosed.merge(entry.getKey(), entry.getValue(), (v1, v2) -> {
                List<Row<I>> row = new ArrayList<>(v1);
                row.addAll(v2);
                return row;
            });
        }

        return new ArrayList<>(unclosed.values());
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

        if (row.hasContents()) {
            int cid = row.getRowContentId();
            if (canonicalRows.get(cid) == null) {
                canonicalRows.set(cid, row);
            }
        }
    }

    public List<List<Row<I>>> increaseCounterLimit(MembershipOracle<I, D> oracle) {
        List<Word<I>> suffixes = getSuffixes();
        for (RowImpl<I> row : shortPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < suffixes.size(); i++) {
                if (rowContents.get(i).getCounterValue() == NO_COUNTER_VALUE) {
                    Word<I> word = row.getLabel().concat(suffixes.get(i));
                    if (prefixesOfL.contains(word)) {
                        D output = oracle.answerQuery(word);
                        int counterValue = counterValueOracle.answerQuery(word);
                        rowContents.set(i, new OutputAndCounterValue<>(output, counterValue));
                    }
                }
            }

            processContents(row, rowContents, true);
        }

        Map<Integer, List<Row<I>>> unclosed = new HashMap<>();

        for (RowImpl<I> row : longPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < suffixes.size(); i++) {
                if (rowContents.get(i).getCounterValue() == NO_COUNTER_VALUE) {
                    Word<I> word = row.getLabel().concat(suffixes.get(i));
                    if (prefixesOfL.contains(word)) {
                        D output = oracle.answerQuery(word);
                        int counterValue = counterValueOracle.answerQuery(word);
                        rowContents.set(i, new OutputAndCounterValue<>(output, counterValue));
                    }
                }
            }

            if (processContents(row, rowContents, false) && !isBinRow(row)) {
                unclosed.put(row.getRowContentId(), new ArrayList<>());
            }

            if (unclosed.containsKey(row.getRowContentId())) {
                unclosed.get(row.getRowContentId()).add(row);
            }
        }

        for (Map.Entry<Integer, List<Row<I>>> entry : checkForNewWordsInPrefixOfL().entrySet()) {
            unclosed.merge(entry.getKey(), entry.getValue(), (v1, v2) -> {
                List<Row<I>> row = new ArrayList<>(v1);
                row.addAll(v2);
                return row;
            });
        }
        return new ArrayList<>(unclosed.values());
    }

    @Override
    public Word<I> transformAccessSequence(Word<I> word) {
        Row<I> current = shortPrefixRows.get(0);
        assert current != null;

        for (I symbol : word) {
            current = getRowSuccessor(current, symbol);
            current = canonicalRows.get(current.getRowContentId());
            assert current != null;
        }

        return current.getLabel();
    }

    @Override
    public boolean isAccessSequence(Word<I> word) {
        Row<I> current = shortPrefixRows.get(0);

        for (I symbol : word) {
            current = getRowSuccessor(current, symbol);
            if (!isCanonical(current)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public List<List<Row<I>>> addAlphabetSymbol(I symbol, MembershipOracle<I, D> oracle) {
        if (!alphabet.containsSymbol(symbol)) {
            Alphabets.toGrowingAlphabetOrThrowException(alphabet).addSymbol(symbol);
        }

        final int newAlphabetSize = alphabet.size();

        if (isInitialized() && alphabetSize < newAlphabetSize) {
            alphabetSize = newAlphabetSize;
            final int newSymbolIndex = alphabet.getSymbolIndex(symbol);

            final List<RowImpl<I>> shortPrefixes = shortPrefixRows;
            final List<RowImpl<I>> newLongPrefixes = new ArrayList<>(shortPrefixes.size());

            for (RowImpl<I> prefix : shortPrefixes) {
                prefix.ensureInputCapacity(newAlphabetSize);

                final Word<I> newLongPrefix = prefix.getLabel().append(symbol);
                final RowImpl<I> longPrefixRow = createLpRow(newLongPrefix);

                newLongPrefixes.add(longPrefixRow);
                prefix.setSuccessor(newSymbolIndex, longPrefixRow);
            }

            final int numLongPrefixes = newLongPrefixes.size();
            List<Word<I>> suffixes = getSuffixes();
            final int numSuffixes = suffixes.size();

            // PASS 1: membership queries
            final List<DefaultQuery<I, D>> queries = new ArrayList<>(numLongPrefixes * numSuffixes);

            buildRowQueries(queries, newLongPrefixes, suffixes);
            oracle.processQueries(queries);

            final Iterator<DefaultQuery<I, D>> queryIterator = queries.iterator();

            for (RowImpl<I> row : newLongPrefixes) {
                final List<OutputAndCounterValue<D>> contents = new ArrayList<>(numSuffixes);

                fetchResults(queryIterator, contents, numSuffixes);

                processContents(row, contents, false);
            }

            // PASS 2: counter value queries
            final Map<Integer, List<Row<I>>> unclosed = new HashMap<>();
            for (RowImpl<I> row : newLongPrefixes) {
                final List<OutputAndCounterValue<D>> contents = new ArrayList<>(fullRowContents(row));
                for (int i = 0; i < numSuffixes; i++) {
                    Word<I> word = row.getLabel().concat(suffixes.get(i));
                    D output = contents.get(i).getOutput();
                    int counterValue = NO_COUNTER_VALUE;
                    if (prefixesOfL.contains(word)) {
                        counterValue = counterValueOracle.answerQuery(word);
                    }
                    contents.set(i, new OutputAndCounterValue<>(output, counterValue));
                }

                if (processContents(row, contents, false) && !isBinRow(row)) {
                    unclosed.put(row.getRowContentId(), new ArrayList<>());
                }

                if (unclosed.containsKey(row.getRowContentId())) {
                    unclosed.get(row.getRowContentId()).add(row);
                }
            }

            for (Map.Entry<Integer, List<Row<I>>> entry : checkForNewWordsInPrefixOfL().entrySet()) {
                unclosed.merge(entry.getKey(), entry.getValue(), (v1, v2) -> {
                    List<Row<I>> row = new ArrayList<>(v1);
                    row.addAll(v2);
                    return row;
                });
            }

            return new ArrayList<>(unclosed.values());
        } else {
            return Collections.emptyList();
        }
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
    public ObservationTable<I, OutputAndCounterValue<D>> toClassicObservationTable() {
        class Table implements ObservationTable<I, OutputAndCounterValue<D>> {

            private final GenericObservationTableWithCounterValues<I, D> table;

            public Table(GenericObservationTableWithCounterValues<I, D> table) {
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
            public List<OutputAndCounterValue<D>> rowContents(Row<I> row) {
                return table.fullRowContents(row);
            }

        }

        return new Table(this);
    }
}
