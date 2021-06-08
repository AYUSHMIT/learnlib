package de.learnlib.algorithms.lstar.roca;

import static de.learnlib.algorithms.lstar.roca.ObservationTreeNode.UNKNOWN_COUNTER_VALUE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.words.Word;

/**
 * Special inconsistency for {@link ObservationTableWithCounterValuesROCA} where an inconsistency is defined by an approx identifier and a symbol.
 * 
 * The approx class and the symbol define an inconsistency when the successors' classes have nothing in common.
 * That is, the intersection of all Approx(va) (with v such that Approx(v) = Approx(u)) is empty.
 * In that case, we can find new suffixes to add in the table.
 * 
 * @param <I> Alphabet input type
 * 
 * @author Gaëtan Staquet
 */
final class ApproxInconsistency<I> extends Inconsistency<I> {

    private final int approxId;
    private final ObservationTableWithCounterValuesROCA<I> table;

    public ApproxInconsistency(int approxId, I symbol, ObservationTableWithCounterValuesROCA<I> table) {
        super(null, null, symbol);
        this.approxId = approxId;
        this.table = table;
    }

    public int getApproxId() {
        return approxId;
    }

    public Set<Word<I>> getAllSuffixes() {
        Set<Word<I>> suffixes = new HashSet<>();

        // For every Approx class of short prefixes, let i be its identifier and z its representative.
        // Let A be the union of all Approx(va) for every v in the same Approx class as u.
        // If i is in A, then at least one new suffix can be found.
        for (Integer classId : table.getAllShortPrefixesApproxIds()) {
            Row<I> canonicalForClassId = table.getShortPrefixCanonicalRow(classId);
            List<PairCounterValueOutput<Boolean>> canonicalContents = table.fullRowContents(canonicalForClassId);

            Set<Integer> unionApprox = new HashSet<>();

            for (Row<I> row : table.getRowsInApprox(approxId)) {
                Row<I> successorRow = row.getSuccessor(table.getInputAlphabet().getSymbolIndex(getSymbol()));
                Set<Integer> successorApprox = table.getUsedApproxClasses(successorRow);
                unionApprox.addAll(successorApprox);
            }

            if (unionApprox.contains(classId)) {
                for (Row<I> row : table.getRowsInApprox(approxId)) {
                    Row<I> successorRow = row.getSuccessor(table.getInputAlphabet().getSymbolIndex(getSymbol()));
                    List<PairCounterValueOutput<Boolean>> successorContents = table.fullRowContents(successorRow);

                    for (int i = 0; i < table.numberOfSuffixes(); i++) {
                        Word<I> suffix = table.getSuffix(i);
                        Word<I> newSuffix = suffix.prepend(getSymbol());
                        PairCounterValueOutput<Boolean> canonicalCell = canonicalContents.get(i);
                        PairCounterValueOutput<Boolean> successorCell = successorContents.get(i);

                        if (canonicalCell.getOutput() != successorCell.getOutput()) {
                            suffixes.add(newSuffix);
                        }
                        if (successorRow.isShortPrefixRow()) {
                            if (canonicalCell.getCounterValue() != successorCell.getCounterValue()) {
                                suffixes.add(newSuffix);
                            }
                        } else {
                            if (canonicalCell.getCounterValue() != UNKNOWN_COUNTER_VALUE
                                    && successorCell.getCounterValue() != UNKNOWN_COUNTER_VALUE
                                    && canonicalCell.getCounterValue() != successorCell.getCounterValue()) {
                                suffixes.add(newSuffix);
                            }
                        }
                    }
                }
            }
        }

        return suffixes;
    }
}
