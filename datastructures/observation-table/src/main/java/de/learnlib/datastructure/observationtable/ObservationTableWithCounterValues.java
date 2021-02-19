package de.learnlib.datastructure.observationtable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.oca.automatoncountervalues.AcceptingOrExit;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * An observation table where each row has associated possible counter values.
 * 
 * The observation table encodes a restricted automaton, i.e., an
 * {@link AutomatonWithCounterValues} up to a counter limit. The observation
 * table can hold three values:
 * <ul>
 * <li>Accepting, when the word must be accepted</li>
 * <li>Rejecting, when the word can be reached without exceeding the counter
 * limit and must be rejected</li>
 * <li>Exit, when the word can not be reached without exceeding the counter
 * limit. This also means the word must be rejected.</li>
 * </ul>
 * 
 * TODO: clear the code OR change equivalence definition
 * 
 * @param <I> Input alphabet type
 * 
 * @author Gaëtan Staquet
 */
public class ObservationTableWithCounterValues<I> extends GenericObservationTable<I, AcceptingOrExit> {

    /**
     * A class mapping a counter value to each row of an observation table
     * 
     * @author Gaëtan Staquet
     */
    public static final class CounterValueAssignment<I> {
        private final Map<Integer, Integer> assignment;

        public CounterValueAssignment() {
            this.assignment = new HashMap<>();
        }

        public void setValue(Row<I> state, int value) {
            assignment.put(state.getRowId(), value);
        }

        public int getValue(Row<I> state) {
            return assignment.getOrDefault(state.getRowId(), -1);
        }

        public boolean hasValue(Row<I> state) {
            return assignment.containsKey(state.getRowId());
        }

        public void removeValue(Row<I> state) {
            assignment.remove(state.getRowId());
        }

        public void addAll(CounterValueAssignment<? extends I> other) {
            assignment.putAll(other.assignment);
        }

        @Override
        public String toString() {
            return assignment.toString();
        }
    }

    private int counterLimit;

    private final Map<Integer, Set<Integer>> counterValues;

    public ObservationTableWithCounterValues(Alphabet<I> alphabet) {
        super(alphabet);
        counterValues = new HashMap<>();
    }

    /**
     * Returns each feasible counter value assignment (that is, each row is
     * associated with a counter value).
     * 
     * @return The feasible counter value assignments
     */
    public List<CounterValueAssignment<I>> getAllPossibleCounterValuesAssignments() {
        computeCounterValues(true);

        List<CounterValueAssignment<I>> assignments = new LinkedList<>();
        assignments.add(new CounterValueAssignment<>());
        LinkedList<Row<I>> rows = new LinkedList<>();
        rows.addAll(getRepresentativeRows());
        createAllAssignments(assignments, rows.listIterator());

        // The recursive function always create one assignment that is not properly
        // defined
        assignments.remove(assignments.size() - 1);

        return assignments;
    }

    private CounterValueAssignment<I> createAllAssignments(List<CounterValueAssignment<I>> assignments,
            ListIterator<Row<I>> nextRows) {
        CounterValueAssignment<I> currentAssignment = assignments.get(assignments.size() - 1);
        if (!nextRows.hasNext()) {
            // We have exhausted every row.
            // We create a new assignment to use later.
            CounterValueAssignment<I> newAssignment = new CounterValueAssignment<>();
            newAssignment.addAll(currentAssignment);
            assignments.add(newAssignment);
            return newAssignment;
        }
        Row<I> row = nextRows.next();

        Set<Integer> cvRow = counterValues.get(row.getRowId());
        if (cvRow.size() == 0) {
            // We just ignore the current row
            currentAssignment = createAllAssignments(assignments, nextRows);
        } else {
            for (Integer cv : cvRow) {
                currentAssignment.setValue(row, cv);
                currentAssignment = createAllAssignments(assignments, nextRows);
                currentAssignment.removeValue(row);
            }
        }

        nextRows.previous();
        return currentAssignment;
    }

    public int getCounterLimit() {
        return counterLimit;
    }

    /**
     * Increases the counter limit of the table.
     * 
     * This has the effect of replacing the "Exit" in the table, when possible.
     * 
     * @param oracle The membership oracle
     * @return The unclosed rows
     */
    public List<List<Row<I>>> incrementCounterLimit(MembershipOracle.RestrictedAutomatonMembershipOracle<I> oracle) {
        counterLimit++;
        // We update the table to try to remove Exits.
        // It may happen that we have to add new rows (but we never add new suffixes)
        List<DefaultQuery<I, AcceptingOrExit>> queries = new ArrayList<>();
        for (Row<I> spRow : getShortPrefixRows()) {
            buildQueries(queries, spRow.getLabel(), getSuffixes());
        }
        for (Row<I> lpRow : getLongPrefixRows()) {
            buildQueries(queries, lpRow.getLabel(), getSuffixes());
        }

        oracle.processQueries(queries);
        Iterator<DefaultQuery<I, AcceptingOrExit>> queryIt = queries.iterator();
        Set<Integer> seenContentIds = new HashSet<>();

        for (Row<I> spRow : getShortPrefixRows()) {
            RowImpl<I> row = getRow(spRow.getRowId());
            List<AcceptingOrExit> newRowContents = new ArrayList<>(numberOfSuffixes());
            fetchResults(queryIt, newRowContents, numberOfSuffixes());
            List<AcceptingOrExit> currentRowContents = rowContents(row);

            if (!seenContentIds.contains(row.getRowContentId())) {
                processContents(row, newRowContents, true);
            } else {
                for (int i = 0; i < numberOfSuffixes(); i++) {
                    if (newRowContents.get(i) != currentRowContents.get(i)) {
                        processContents(row, newRowContents, true);
                        break;
                    }
                }
            }
            seenContentIds.add(row.getRowContentId());
        }

        List<List<Row<I>>> unclosed = new ArrayList<>();
        int numSpRows = numberOfDistinctRows();

        for (Row<I> lpRow : getLongPrefixRows()) {
            RowImpl<I> row = getRow(lpRow.getRowId());
            List<AcceptingOrExit> newRowContents = new ArrayList<>(numberOfSuffixes());
            fetchResults(queryIt, newRowContents, numberOfSuffixes());
            List<AcceptingOrExit> currentRowContents = rowContents(row);

            if (!seenContentIds.contains(row.getRowContentId())) {
                processContents(row, newRowContents, false);
            } else {
                for (int i = 0; i < numberOfSuffixes(); i++) {
                    if (currentRowContents.get(i) != newRowContents.get(i)) {
                        if (processContents(row, newRowContents, false)) {
                            unclosed.add(new ArrayList<>());
                        }

                        if (row.getRowContentId() >= numSpRows) {
                            unclosed.get(row.getRowContentId() - numSpRows).add(row);
                        }
                    }
                }
            }
            seenContentIds.add(row.getRowContentId());
        }

        return unclosed;
    }

    /**
     * Computes the possible counter values for each row.
     * 
     * @param refine If true, the possible counter values are further refined, i.e.,
     *               the set of possible values is as small as possible.
     */
    public void computeCounterValues(boolean refine) {
        // First, we initialize the possible counter values for the new rows
        for (Row<I> row : getAllRows()) {
            int id = row.getRowId();
            // We compute the default counter values only if the row id is not yet known
            if (counterValues.containsKey(id) && counterValues.get(id).size() != 0) {
                continue;
            }
            List<AcceptingOrExit> rowContents = rowContents(row);
            Set<Integer> counterValuesRow = new HashSet<>();
            if (rowContents.get(0) == AcceptingOrExit.ACCEPTING || row.getLabel().equals(Word.epsilon())) {
                // We explicitly assume the ROCA accepts with counter value equal to 0
                counterValuesRow.add(0);
            } else if (rowContents.get(0) == AcceptingOrExit.REJECTING) {
                for (int i = 0; i <= getCounterLimit(); i++) {
                    counterValuesRow.add(i);
                }
            } else {
                Word<I> label = row.getLabel();
                Word<I> prefix = label.prefix(label.size() - 1);
                List<AcceptingOrExit> prefixContents = rowContents(getRow(prefix));
                // If the prefixContents[0] is Exit, we let the set empty
                if (prefixContents.get(0) != AcceptingOrExit.EXIT) {
                    counterValuesRow.add(getCounterLimit() + 1);
                    Set<Integer> prefixCounterValues = new HashSet<>();
                    prefixCounterValues.add(getCounterLimit());
                    counterValues.put(getRow(prefix).getRowId(), prefixCounterValues);
                }
            }

            counterValues.put(id, counterValuesRow);
        }

        if (refine) {
            // Second, we try to reduce the size of each set
            refineCounterValues();
        }
    }

    // @formatter:off
    // @Override
    // protected boolean processContents(RowImpl<I> row, List<AcceptingOrExit> rowContents, boolean makeCanonical) {
    //     int contentId;
    //     boolean added = false;
    //     contentId = rowContentIds.getOrDefault(rowContents, NO_ENTRY);
    //     if (contentId == NO_ENTRY) {
    //         contentId = numberOfDistinctRows();
    //         rowContentIds.put(rowContents, contentId);
    //         allRowContents.add(rowContents);
    //         added = true;
    //         if (makeCanonical) {
    //             canonicalRows.add(row);
    //         } else {
    //             canonicalRows.add(null);
    //         }
    //     }

    //     // if (row.getRowContentId() != NO_ENTRY) {
    //     //     final long nRowsWithSameContentId = getAllRows().stream().
    //     //         filter(r -> r.getRowContentId() == row.getRowContentId()).
    //     //         count();
    //     //     if (nRowsWithSameContentId == 1) {
    //     //         // There is only one row that was using that content ID.
    //     //         // So, we remove the old row content.
    //     //         rowContentIds.remove(rowContents(row));
    //     //     }
    //     // }
    //     row.setRowContentId(contentId);
    //     return added;
    // }
    // @formatter:on

    private void refineCounterValues() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Row<I> row : getAllRows()) {
                Word<I> label = row.getLabel();
                int rowId = row.getRowId();
                if (!label.equals(Word.epsilon()) && counterValues.get(rowId).size() != 0) {
                    Set<Integer> cvRow = counterValues.get(rowId);
                    Set<Integer> possibleCVPrefix = new HashSet<>();
                    for (Integer cv : cvRow) {
                        possibleCVPrefix.add(cv - 1);
                        possibleCVPrefix.add(cv);
                        possibleCVPrefix.add(cv + 1);
                    }

                    Word<I> prefix = label.prefix(label.size() - 1);
                    Row<I> prefixRow = getRow(prefix);
                    Set<Integer> cvPrefix = counterValues.get(prefixRow.getRowId());
                    Set<Integer> possibleCVRow = new HashSet<>();
                    for (Integer cv : cvPrefix) {
                        possibleCVRow.add(cv - 1);
                        possibleCVRow.add(cv);
                        possibleCVRow.add(cv + 1);
                    }

                    boolean rowChanged = cvRow.retainAll(possibleCVRow);
                    boolean prefixedChanged = cvPrefix.retainAll(possibleCVPrefix);
                    changed = changed || rowChanged || prefixedChanged;
                }
            }
        }
    }

    // @formatter:off
    // @Override
    // public @Nullable Row<I> findUnclosedRow() {
    //     for (Row<I> lpRow : getLongPrefixRows()) {
    //         for (Row<I> spRow : getShortPrefixRows()) {
    //             if (!areEquivalent(lpRow, spRow)) {
    //                 return lpRow;
    //             }
    //         }
    //     }
    //     return null;
    // }

    // @Override
    // public @Nullable Inconsistency<I> findInconsistency() {
    //     Alphabet<I> alphabet = getInputAlphabet();
    //     List<Row<I>> spRows = getShortPrefixRows();
    //     for (int i = 0 ; i < spRows.size() ; i++) {
    //         Row<I> row1 = spRows.get(i);
    //         for (int j = i + 1 ; j < spRows.size() ; j++) {
    //             Row<I> row2 = spRows.get(j);
    //             if (areEquivalent(row1, row2)) {
    //                 for (int k = 0 ; k < alphabet.size() ; k++) {
    //                     Row<I> lpRow1 = row1.getSuccessor(k);
    //                     Row<I> lpRow2 = row2.getSuccessor(k);
    //                     if (!areEquivalent(lpRow1, lpRow2)) {
    //                         return new Inconsistency<>(row1, row2, alphabet.getSymbol(k));
    //                     }
    //                 }
    //             }
    //         }
    //     }

    //     return null;
    // }
    // @formatter:on

    /**
     * Gives, if it exists, a short prefix row with the same row content than the
     * given row.
     * 
     * It is guaranteed that the function always returns the same representative for
     * each equivalence class defined in the table.
     * 
     * @param row The row
     * @return The representative for the row, or null if none exists.
     */
    public @Nullable Row<I> getRepresentativeForEquivalenceClass(Row<I> row) {
        for (Row<I> r : getShortPrefixRows()) {
            if (areEquivalent(r, row)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Tests whether two rows belong in the same equivalence class, according to the
     * table.
     * 
     * @param row1 The first row
     * @param row2 The second row
     * @return True iff row1 and row2 are equivalent
     */
    public boolean areEquivalent(Row<I> row1, Row<I> row2) {
        return row1.getRowContentId() == row2.getRowContentId();
        // @formatter:off
        // List<AcceptingOrExit> rowContents1 = rowContents(row1);
        // List<AcceptingOrExit> rowContents2 = rowContents(row2);
        // for (int i = 0 ; i < numberOfSuffixes() ; i++) {
        //     // We consider Rejecting and Exit equivalent here
        //     if (rowContents1.get(i) != rowContents2.get(i) && (rowContents1.get(i) == AcceptingOrExit.ACCEPTING || rowContents2.get(i) == AcceptingOrExit.ACCEPTING)) {
        //         return false;
        //     }
        // }
        // return true;
        // @formatter:on
    }

    /**
     * Returns whether the given row is such that there is an Exit in the first
     * column.
     * 
     * @param row The row
     * @return True iff the first column for the row is "Exit"
     */
    public boolean isExitRow(Row<I> row) {
        return rowContents(row).get(0) == AcceptingOrExit.EXIT;
    }

    /**
     * Returns whether the given row is such that there is an Accepting in the first
     * column.
     * 
     * @param row The row
     * @return True iff the first column for the row is "Accepting"
     */
    public boolean isAcceptingRow(Row<I> row) {
        return rowContents(row).get(0) == AcceptingOrExit.ACCEPTING;
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
            .collect(Collectors.toSet());
        // @formatter:on
    }
}
