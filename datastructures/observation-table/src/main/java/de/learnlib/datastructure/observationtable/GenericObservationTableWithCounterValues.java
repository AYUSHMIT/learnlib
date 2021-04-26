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
    private static final int NO_BIN_ROW = -1;

    private final List<RowImpl<I>> shortPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> longPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> allRows = new ArrayList<>();
    private final Map<Integer, List<OutputAndCounterValue<D>>> allRowContents = new HashMap<>();
    private final Map<Integer, List<RowImpl<I>>> rowByContentId = new HashMap<>();
    private final Set<Integer> freeRowIds = new HashSet<>();
    private final Map<Integer, RowImpl<I>> canonicalRows = new HashMap<>();

    private final Map<List<OutputAndCounterValue<D>>, Integer> rowContentIds = new HashMap<>();
    private final Map<Word<I>, RowImpl<I>> rowMap = new HashMap<>();
    private final List<Word<I>> suffixes = new ArrayList<>();
    private final Set<Word<I>> suffixSet = new HashSet<>();

    // TODO: use a trie
    private final Set<Word<I>> prefixesOfL = new HashSet<>();

    private final Alphabet<I> alphabet;
    private int alphabetSize;
    private int numRows;
    private int binRowContentsId = NO_BIN_ROW;
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

    /**
     * Decides whether a row is a bin row, i.e., a row that should not have a
     * representative.
     * 
     * @param row The row
     * @return True if the row should not have a representative
     */
    protected boolean isBinRow(Row<I> row) {
        return Objects.equals(row.getRowContentId(), binRowContentsId);
    }

    /**
     * Decides whether the provided content is the content of a bin row, i.e., a row
     * that should not have a representative.
     * 
     * @param contents The contents
     * @return True iff the content is the content of a bin row
     */
    protected abstract boolean isBinContents(List<OutputAndCounterValue<D>> contents);

    /**
     * Decides whether the observation stored in the OutputAndCounterValue is an
     * accepted observation.
     * 
     * @param outputAndCounterValue The cell content
     * @return True if the cell contains an accepted observation
     */
    protected abstract boolean isAccepted(OutputAndCounterValue<D> outputAndCounterValue);

    /**
     * Asks counter value queries for all cells with missing counter values in the
     * provided rows, at the condition that the cell corresponds to a word that is
     * in the prefix of the language.
     * 
     * That is, if a cell has a null or NO_COUNTER_VALUE counter value, this
     * function asks a counter value query.
     * 
     * @param shortPrefixRows The short prefix rows
     * @param longPrefixRows  The long prefix rows
     */
    protected void askCounterValueQueries(List<RowImpl<I>> shortPrefixRows, List<RowImpl<I>> longPrefixRows) {
        for (RowImpl<I> row : shortPrefixRows) {
            List<OutputAndCounterValue<D>> contents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < numberOfSuffixes(); i++) {
                OutputAndCounterValue<D> cell = contents.get(i);
                if (cell.getCounterValue() == null || cell.getCounterValue() == NO_COUNTER_VALUE) {
                    Word<I> word = row.getLabel().concat(getSuffix(i));
                    D output = cell.getOutput();
                    int counterValue = NO_COUNTER_VALUE;
                    if (prefixesOfL.contains(word)) {
                        counterValue = counterValueOracle.answerQuery(word);
                    }
                    contents.set(i, new OutputAndCounterValue<>(output, counterValue));
                }
            }

            processContents(row, contents, true);
        }

        for (RowImpl<I> row : longPrefixRows) {
            List<OutputAndCounterValue<D>> contents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < numberOfSuffixes(); i++) {
                OutputAndCounterValue<D> cell = contents.get(i);
                if (cell.getCounterValue() == null || cell.getCounterValue() == NO_COUNTER_VALUE) {
                    Word<I> word = row.getLabel().concat(getSuffix(i));
                    D output = cell.getOutput();
                    int counterValue = NO_COUNTER_VALUE;
                    if (prefixesOfL.contains(word)) {
                        counterValue = counterValueOracle.answerQuery(word);
                    }
                    contents.set(i, new OutputAndCounterValue<>(output, counterValue));
                }
            }

            processContents(row, contents, false);
        }
    }

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
        askCounterValueQueries(shortPrefixRows, longPrefixRows);

        return seekUnclosedRows();
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
        // Since it may happen that short prefix rows' IDs change during the course of
        // the algorithm (due to newly identified prefixes of the target language), we
        // need to keep track of the changes
        if (row.hasContents()) {
            int currentId = row.getRowContentId();
            List<OutputAndCounterValue<D>> currentContents = fullRowContents(row);
            rowByContentId.get(currentId).remove(row);

            if (canonicalRows.get(currentId) == row) {
                canonicalRows.put(currentId, null);
            }

            if (rowByContentId.get(currentId).size() == 0) {
                allRowContents.remove(currentId);
                rowContentIds.remove(currentContents);
                freeRowIds.add(currentId);
                rowByContentId.remove(currentId);
                if (currentId == binRowContentsId) {
                    binRowContentsId = NO_BIN_ROW;
                }
            } else if (canonicalRows.get(currentId) == null) {
                canonicalRows.put(currentId, rowByContentId.get(currentId).get(0));
            }
        }

        int contentId;
        contentId = rowContentIds.getOrDefault(rowContents, NO_ENTRY);
        boolean added = false;

        if (contentId == NO_ENTRY) {
            if (freeRowIds.size() != 0) {
                contentId = freeRowIds.iterator().next();
                freeRowIds.remove(contentId);
            } else {
                contentId = numberOfDistinctRows();
            }
            rowContentIds.put(rowContents, contentId);
            allRowContents.put(contentId, rowContents);
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

        // We update the set of prefixes of L, according to the table
        for (int i = 0; i < numberOfSuffixes(); i++) {
            if (isAccepted(rowContents.get(i))) {
                Word<I> word = row.getLabel().concat(suffixes.get(i));
                for (Word<I> prefix : word.prefixes(false)) {
                    prefixesOfL.add(prefix);
                }
            }
        }

        if (added && isBinContents(rowContents)) {
            binRowContentsId = row.getRowContentId();
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

        return canonicalRows.get(getClassId(row));
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
        return Collections.unmodifiableList(suffixes);
    }

    @Override
    public List<D> rowContents(Row<I> row) {
        List<OutputAndCounterValue<D>> rowContents = fullRowContents(row);
        // @formatter:off
        return rowContents.stream()
            .map(contents -> contents.getOutput())
            .collect(Collectors.toList());
        // @formatter:on
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

    /**
     * Iterates over the table to check for words that are now in the prefix of the
     * language.
     * 
     * It only asks counter value queries, as membership queries' answers can only
     * change upon a counter limit increase.
     * 
     * @return A map content id -> list of unclosed rows
     */
    private void checkForNewWordsInPrefixOfL() {
        for (RowImpl<I> row : shortPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < numberOfSuffixes(); i++) {
                OutputAndCounterValue<D> cell = rowContents.get(i);
                if (cell.getCounterValue() == NO_COUNTER_VALUE) {
                    Word<I> word = row.getLabel().concat(getSuffix(i));
                    if (prefixesOfL.contains(word)) {
                        D output = cell.getOutput();
                        int counterValue = counterValueOracle.answerQuery(word);
                        OutputAndCounterValue<D> newCell = new OutputAndCounterValue<>(output, counterValue);
                        rowContents.set(i, newCell);
                    }
                }
            }

            processContents(row, rowContents, true);
        }

        for (RowImpl<I> row : longPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < numberOfSuffixes(); i++) {
                OutputAndCounterValue<D> cell = rowContents.get(i);
                Word<I> word = row.getLabel().concat(getSuffix(i));
                if (cell.getCounterValue() == null || cell.getCounterValue() == NO_COUNTER_VALUE) {
                    if (prefixesOfL.contains(word)) {
                        D output = cell.getOutput();
                        int counterValue = counterValueOracle.answerQuery(word);
                        OutputAndCounterValue<D> newCell = new OutputAndCounterValue<>(output, counterValue);
                        rowContents.set(i, newCell);
                    }
                }
            }

            processContents(row, rowContents, false);
        }
    }

    protected boolean areEquivalent(Row<I> row1, Row<I> row2) {
        return Objects.equals(getClassId(row1), getClassId(row2));
    }

    @Nullable
    protected Integer getClassId(Row<I> row) {
        if (row.getRowContentId() == binRowContentsId) {
            return binRowContentsId;
        } else if (row.isShortPrefixRow()) {
            return row.getRowContentId();
        }

        List<OutputAndCounterValue<D>> rowContents = fullRowContents(row);

        Integer classID = null;
        int numberMatches = 0;

        for (Row<I> spRow : shortPrefixRows) {
            if (row.getRowContentId() == spRow.getRowContentId()) {
                return row.getRowContentId();
            }

            boolean equivalent = true;
            int currentMatches = 0;
            List<OutputAndCounterValue<D>> spRowContents = fullRowContents(spRow);
            for (int i = 0; i < numberOfSuffixes(); i++) {
                OutputAndCounterValue<D> rowCell = rowContents.get(i);
                OutputAndCounterValue<D> spRowCell = spRowContents.get(i);
                if (Objects.equals(rowCell.getOutput(), spRowCell.getOutput())) {
                    if (Objects.equals(rowCell.getCounterValue(), spRowCell.getCounterValue())) {
                        currentMatches++;
                    }
                    else if (rowCell.getCounterValue() != NO_COUNTER_VALUE) {
                        equivalent = false;
                        break;
                    }
                } else {
                    equivalent = false;
                    break;
                }
            }

            if (equivalent && currentMatches > numberMatches) {
                classID = spRow.getRowContentId();
                numberMatches = currentMatches;
            }
        }

        return classID;
    }

    /**
     * Seeks in the table the unclosed rows.
     * 
     * Since it can happen that short prefixes rows change their output, it may
     * happen that previously closed rows are no longer closed, even though they
     * were not modified.
     * 
     * @return A list of list of unclosed rows, grouped by contents id
     */
    private List<List<Row<I>>> seekUnclosedRows() {
        Map<Integer, List<Row<I>>> unclosed = new HashMap<>();
        for (Row<I> row : longPrefixRows) {
            Integer classId = getClassId(row);

            if (classId == null) {
                if (!unclosed.containsKey(row.getRowContentId())) {
                    ArrayList<Row<I>> list = new ArrayList<>();
                    list.add(row);
                    unclosed.put(row.getRowContentId(), list);
                } else {
                    unclosed.get(row.getRowContentId()).add(row);
                }
            }
        }
        return new ArrayList<>(unclosed.values());
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

        int rowCount = numberOfShortPrefixRows() + longPrefixRows.size();

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
        int oldSuffixCount = numberOfSuffixes();
        this.suffixes.addAll(newSuffixList);

        for (RowImpl<I> row : shortPrefixRows) {
            // Unlike in the GenericObservationTable's implementation, we want to call
            // processContents in all cases as processContents does more steps than setting
            // the content id.
            List<OutputAndCounterValue<D>> currentContents = fullRowContents(row);
            List<OutputAndCounterValue<D>> newContents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
            newContents.addAll(currentContents.subList(0, oldSuffixCount));
            fetchResults(queryIt, newContents, numNewSuffixes);
            processContents(row, newContents, true);
        }

        for (RowImpl<I> row : longPrefixRows) {
            List<OutputAndCounterValue<D>> currentContents = fullRowContents(row);
            List<OutputAndCounterValue<D>> newContents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
            newContents.addAll(currentContents.subList(0, oldSuffixCount));
            fetchResults(queryIt, newContents, numNewSuffixes);
            processContents(row, newContents, false);
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

            processContents(row, contents, false);
        }

        // PASS 3: check for words that are now in the prefix of the language
        checkForNewWordsInPrefixOfL();

        return seekUnclosedRows();
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

        int numSuffixes = numberOfSuffixes();

        // PASS 1: membership queries
        int numFreshRows = freshSpRows.size() + freshLpRows.size();
        List<DefaultQuery<I, D>> queries = new ArrayList<>(numFreshRows * numSuffixes);
        buildRowQueries(queries, freshSpRows, getSuffixes());
        buildRowQueries(queries, freshLpRows, getSuffixes());

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
        askCounterValueQueries(shortPrefixRows, longPrefixRows);

        // PASS 3: check for words that are now in the prefix of the language
        checkForNewWordsInPrefixOfL();

        return seekUnclosedRows();
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
                canonicalRows.put(cid, row);
            }
        }
    }

    public List<List<Row<I>>> increaseCounterLimit(MembershipOracle<I, D> oracle) {
        // PASS 1: ask membership queries for all words that potentially can be accepted
        // now.
        // That is, we aks a query for every cell such that the counter value is
        // NO_COUNTER_VALUE
        for (RowImpl<I> row : shortPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < numberOfSuffixes(); i++) {
                if (rowContents.get(i).getCounterValue() == NO_COUNTER_VALUE) {
                    Word<I> word = row.getLabel().concat(getSuffix(i));
                    D output = oracle.answerQuery(word);
                    rowContents.set(i, new OutputAndCounterValue<>(output, NO_COUNTER_VALUE));
                }
            }

            processContents(row, rowContents, true);
        }

        for (RowImpl<I> row : longPrefixRows) {
            List<OutputAndCounterValue<D>> rowContents = new ArrayList<>(fullRowContents(row));
            for (int i = 0; i < numberOfSuffixes(); i++) {
                if (rowContents.get(i).getCounterValue() == NO_COUNTER_VALUE) {
                    Word<I> word = row.getLabel().concat(getSuffix(i));
                    D output = oracle.answerQuery(word);
                    rowContents.set(i, new OutputAndCounterValue<>(output, NO_COUNTER_VALUE));
                }
            }

            processContents(row, rowContents, false);
        }

        // PASS 2: counter value queries
        askCounterValueQueries(shortPrefixRows, longPrefixRows);

        // PASS 3: check for words that are now in the prefix of the language
        checkForNewWordsInPrefixOfL();

        return seekUnclosedRows();
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
            askCounterValueQueries(Collections.emptyList(), newLongPrefixes);

            // PASS 3: check for words that are now in the prefix of the language
            checkForNewWordsInPrefixOfL();

            return seekUnclosedRows();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public @Nullable Inconsistency<I> findInconsistency() {
        final Map<Integer, Row<I>> canonicalRows = new HashMap<>();
        final Alphabet<I> alphabet = getInputAlphabet();

        for (Row<I> spRow : getShortPrefixRows()) {
            int contentId = getClassId(spRow);

            if (!canonicalRows.containsKey(contentId)) {
                canonicalRows.put(contentId, spRow);
            } else {
                Row<I> canRow = canonicalRows.get(contentId);

                for (int i = 0; i < alphabet.size(); i++) {
                    int spSuccContent = getClassId(spRow.getSuccessor(i));
                    int canSuccContent = getClassId(canRow.getSuccessor(i));
                    if (spSuccContent != canSuccContent) {
                        return new Inconsistency<>(canRow, spRow, alphabet.getSymbol(i));
                    }
                }
            }
        }

        return null;
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
