package de.learnlib.algorithms.lstar.roca;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.onecounter.AbstractObservationTableWithCounterValues;
import de.learnlib.datastructure.observationtable.onecounter.PairCounterValueOutput;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * An observation table where observations are pair (output, counter values) for
 * ROCAs.
 * 
 * The counter value of a word is determined by a query to an oracle, if and
 * only if the word is in the known prefix of the language. If the word is not
 * in the prefix, then the value -1 is put in the table.
 * 
 * For each row r of the table, a set Approx(r) is computed. Approx(r) is
 * defined as the set of short prefix rows u such that, for all prefixes w, the
 * outputs of r and u agree, and, if the counter values of rw and uw are both
 * not -1, then the two counter values must be equal.
 * 
 * <ul>
 * <li>The table is said closed when each long prefix row r has a non-empty
 * Approx(r).</li>
 * <li>The table said is consistent if and only two short prefix rows u and v
 * such that Approx(u) = Approx(v) are such that for all input symbol a,
 * Approx(ua) = Approx(va).</li>
 * </ul>
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
public final class ObservationTableWithCounterValuesROCA<I>
        extends AbstractObservationTableWithCounterValues<I, Boolean> {

    private static final int NO_COUNTER_VALUE = -1;

    private final MembershipOracle.CounterValueOracle<I> counterValueOracle;

    // TODO: use a trie
    private final Set<Word<I>> prefixOfL = new HashSet<>();

    private final List<Set<Integer>> approx = new ArrayList<>();
    private final Map<Set<Integer>, Integer> approxToApproxId = new HashMap<>();
    private final Map<Integer, Integer> rowIdToApproxId = new HashMap<>();
    // Approx id -> canonical row
    private final Map<Integer, Row<I>> canonicalRows = new HashMap<>();

    protected ObservationTableWithCounterValuesROCA(Alphabet<I> alphabet,
            MembershipOracle.CounterValueOracle<I> counterValueOracle) {
        super(alphabet);
        this.counterValueOracle = counterValueOracle;
    }

    @Override
    public @Nullable Row<I> getCanonicalRow(Row<I> row) {
        if (isCoAccessibleRow(row)) {
            return canonicalRows.get(rowIdToApproxId.get(row.getRowId()));
        } else {
            return null;
        }
    }

    @Override
    public List<Row<I>> getCanonicalRows() {
        return new ArrayList<>(canonicalRows.values());
    }

    @Override
    protected List<List<Row<I>>> findUnclosedRows() {
        Map<Integer, List<Row<I>>> unclosed = new HashMap<>();
        for (Row<I> lpRow : getLongPrefixRows()) {
            if (unclosed.containsKey(lpRow.getRowContentId())) {
                unclosed.get(lpRow.getRowContentId()).add(lpRow);
            } else if (isCoAccessibleRow(lpRow) && getCanonicalRow(lpRow) == null) {
                ArrayList<Row<I>> unc = new ArrayList<>();
                unc.add(lpRow);
                unclosed.put(lpRow.getRowContentId(), unc);
            }
        }
        return new ArrayList<>(unclosed.values());
    }

    @Override
    public @Nullable Row<I> findUnclosedRow() {
        for (Row<I> lpRow : getLongPrefixRows()) {
            if (isCoAccessibleRow(lpRow) && getCanonicalRow(lpRow) == null) {
                return lpRow;
            }
        }
        return null;
    }

    @Override
    public @Nullable Inconsistency<I> findInconsistency() {
        final Alphabet<I> alphabet = getInputAlphabet();

        for (Row<I> startRow : getCanonicalRows()) {
            int startApproxId = rowIdToApproxId.get(startRow.getRowId());
            Set<Integer> startApprox = approx.get(startApproxId);
            if (startApprox.size() == 1) {
                continue;
            }

            for (int i = 0; i < alphabet.size(); i++) {
                Row<I> successorRow = startRow.getSuccessor(i);
                int successorApproxId = rowIdToApproxId.getOrDefault(successorRow.getRowId(), -1);
                if (successorApproxId == -1) {
                    for (Integer equivalentRowId : startApprox) {
                        Row<I> equivalentRow = getRow(equivalentRowId);
                        Row<I> equivalentSuccessorRow = equivalentRow.getSuccessor(i);
                        int equivalenceSuccessorApproxId = rowIdToApproxId
                                .getOrDefault(equivalentSuccessorRow.getRowId(), -1);
                        if (equivalenceSuccessorApproxId != -1) {
                            return new Inconsistency<>(startRow, equivalentRow, alphabet.getSymbol(i));
                        }
                    }
                } else {
                    Set<Integer> intersection = new HashSet<>();
                    intersection.addAll(approx.get(successorApproxId));
                    for (Integer equivalentRowId : startApprox) {
                        Row<I> equivalentRow = getRow(equivalentRowId);
                        Row<I> equivalentSuccessorRow = equivalentRow.getSuccessor(i);
                        int equivalenceSuccessorApproxId = rowIdToApproxId
                                .getOrDefault(equivalentSuccessorRow.getRowId(), -1);
                        if (equivalenceSuccessorApproxId == -1) {
                            return new Inconsistency<>(startRow, equivalentRow, alphabet.getSymbol(i));
                        }

                        // Both successors have an approx
                        intersection.retainAll(approx.get(equivalenceSuccessorApproxId));
                        if (intersection.size() == 0) {
                            return new Inconsistency<>(startRow, equivalentRow, alphabet.getSymbol(i));
                        }
                    }
                }
            }
        }

        return null;
    }

    @Override
    protected boolean shouldChangeCounterValue(Word<I> prefix, Word<I> suffix, int currentCounterValue) {
        return currentCounterValue == NO_COUNTER_VALUE;
    }

    @Override
    protected int getCounterValue(Word<I> prefix, Word<I> suffix) {
        Word<I> word = prefix.concat(suffix);

        if (prefixOfL.contains(word)) {
            return counterValueOracle.answerQuery(prefix, suffix);
        } else {
            return NO_COUNTER_VALUE;
        }
    }

    @Override
    protected boolean shouldChangeOutput(Word<I> prefix, Word<I> suffix, PairCounterValueOutput<Boolean> currentCell) {
        return !currentCell.getOutput() && currentCell.getCounterValue() == NO_COUNTER_VALUE;
    }

    @Override
    protected void processContentsMembershipQueries(Row<I> row, List<PairCounterValueOutput<Boolean>> rowContents,
            boolean canChangeCounterValues) {
        Word<I> prefix = row.getLabel();
        for (int i = 0; i < rowContents.size(); i++) {
            if (rowContents.get(i).getOutput()) {
                Word<I> suffix = getSuffix(i);
                Word<I> word = prefix.concat(suffix);
                for (Word<I> pref : word.prefixes(true)) {
                    if (prefixOfL.add(pref)) {
                        Row<I> r = getRow(pref);
                        if (canChangeCounterValues && r != null) {
                            updateCounterValues(r);
                        }
                    } else {
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected void updateCanonicalRows() {
        canonicalRows.clear();
        approx.clear();
        approxToApproxId.clear();
        rowIdToApproxId.clear();
        for (Row<I> row : getAllRows()) {
            Set<Integer> approxOfRow = computeApprox(row);
            if (approxOfRow.size() == 0) {
                continue;
            }

            Integer approxId = approxToApproxId.get(approxOfRow);
            if (approxId == null) {
                Integer rowId = approxOfRow.iterator().next();
                approxId = approx.size();
                approx.add(approxOfRow);
                canonicalRows.put(approxId, getRow(rowId));
                approxToApproxId.put(approxOfRow, approxId);
            }

            rowIdToApproxId.put(row.getRowId(), approxId);
        }
    }

    private Set<Integer> computeApprox(Row<I> row) {
        Set<Integer> result = new HashSet<>();
        List<PairCounterValueOutput<Boolean>> rowContents = fullRowContents(row);
        for (Row<I> spRow : getShortPrefixRows()) {
            List<PairCounterValueOutput<Boolean>> spContents = fullRowContents(spRow);
            boolean isApprox = true;
            for (int i = 0; i < numberOfSuffixes(); i++) {
                PairCounterValueOutput<Boolean> spCell = spContents.get(i);
                PairCounterValueOutput<Boolean> rowCell = rowContents.get(i);
                if (row.isShortPrefixRow()) {
                    if (!Objects.equals(spCell, rowCell)) {
                        isApprox = false;
                        break;
                    }
                } else {
                    if (spCell.getOutput() != rowCell.getOutput()) {
                        isApprox = false;
                        break;
                    }
                    if (spCell.getCounterValue() != -1 && rowCell.getCounterValue() != -1
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

    private boolean isCoAccessibleRow(Row<I> row) {
        // TODO: wouldn't it be enough to test if row.getLabel() is in prefixOfL?
        List<PairCounterValueOutput<Boolean>> rowContents = fullRowContents(row);
        for (PairCounterValueOutput<Boolean> cell : rowContents) {
            if (cell.getCounterValue() != NO_COUNTER_VALUE) {
                return true;
            }
        }
        return false;
    }
}
