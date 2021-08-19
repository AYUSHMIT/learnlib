package de.learnlib.algorithms.lstar.roca;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.MembershipOracle;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import static de.learnlib.algorithms.lstar.roca.ObservationTableWithCounterValuesROCA.UNKNOWN_COUNTER_VALUE;

/**
 * A node in a prefix tree used by {@link ObservationTableWithCounterValuesROCA}
 * to actually store the observations.
 * 
 * This implementation assumes the target ROCA accepts by final state and
 * counter value equal to zero.
 * 
 * A node stores four elements:
 * <ul>
 * <li>The answers to the membership and counter value queries, noted L(w) and
 * C(w).</li>
 * <li>Whether the word is in the prefix of the language L_l, noted P(w).</li>
 * <li>Whether the node is actually used in the table. Since the tree is closed
 * by prefix, there are nodes that are not actually used by the learner's
 * table.</li>
 * </ul>
 * 
 * Note that the learner does not immediately access to the stored information.
 * Instead, the returned values (for outputs and counter values) depend on
 * whether the word is in the known prefix, and whether one of the ancestors of
 * the node has a counter value exceeding the limit. We note these values L_l(w)
 * and C_l(w).
 * 
 * Once an operation over the tree is finished, we have the following
 * invariants, with l the current counter limit:
 * <ul>
 * <li>Over a branch from the root to a leaf, we have a (potentially empty)
 * sequence of P(w) = true followed by a (potentially empty) sequence of P(w) =
 * false.</li>
 * <li>Over a branch from the root to a leaf, we have a (potentially empty)
 * sequence of C_l(w) in {0, ..., l} U {UNKNOWN_COUNTER_VALUE} followed by a
 * (potentially empty) sequence of C_l(w) = OUTSIDE_COUNTER_LIMIT.</li>
 * <li>If L_l(w) is not UNKNOWN and P(w) is true, then C_l(w) is not
 * UNKNOWN_COUNTER_VALUE.</li>
 * <li>If L(w) = false, then L_l(w) = false. If L(w) = true, then L_l(w) is
 * either true or false (i.e., not UNKNOWN).</li>
 * <li>If C(w) is not UNKNOWN_COUNTER_VALUE and C(w) <= l, then C_l(w) = C(w).
 * If C(w) is not UNKNOWN_COUNTER_VALUE and C(w) > l, then C_l(w) is
 * OUTSIDE_COUNTER_LIMIT.</li>
 * <li>If C_l(w) is OUTSIDE_COUNTER_LIMIT, then L_l(w) = false. If C_l(w) > 0,
 * then L_l(w) = false.</li>
 * <li>If P(w) = true, then C_l(w) is not OUTSIDE_COUNTER_LIMIT.</li>
 * </ul>
 * 
 * @param <I> Input symbol type
 * @author Gaëtan Staquet
 */
class ObservationTreeNode<I> {
    static enum Output {
        ACCEPTED, REJECTED, UNKNOWN
    }

    static class TableCell<I> {
        private final RowImpl<I> row;
        private final int suffixIndex;

        public TableCell(RowImpl<I> row, int suffixIndex) {
            this.row = row;
            this.suffixIndex = suffixIndex;
        }

        public RowImpl<I> getRow() {
            return row;
        }

        public int getSuffixIndex() {
            return suffixIndex;
        }
    }

    private final @Nullable ObservationTreeNode<I> parent;
    private final Map<Integer, ObservationTreeNode<I>> successors = new HashMap<>();

    private final Alphabet<I> alphabet;

    private final Word<I> prefix;
    private final PairCounterValueOutput<Output> cvOutput;

    private final List<TableCell<I>> tableCells = new ArrayList<>();

    private boolean inPrefix = false;
    private boolean isOutsideCounterLimit = false;

    ObservationTreeNode(Word<I> prefix, ObservationTreeNode<I> parent, Alphabet<I> alphabet) {
        this.parent = parent;
        this.alphabet = alphabet;
        this.prefix = prefix;
        this.cvOutput = new PairCounterValueOutput<>(Output.UNKNOWN, UNKNOWN_COUNTER_VALUE);
    }

    public void initializeAsRoot(MembershipOracle<I, Boolean> membershipOracle,
            MembershipOracle.CounterValueOracle<I> counterValueOracle) {
        assert prefix.equals(Word.epsilon());
        boolean accepted = membershipOracle.answerQuery(Word.epsilon());
        setOutput(accepted);
        setOutsideCounterLimit(false);
        // We cheat a little for the root and we assume it is always in the prefix of
        // the language
        setInPrefix(true);
        // We assume that the counter value of the initial state is always zero
        setCounterValue(0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }

        ObservationTreeNode<?> o = (ObservationTreeNode<?>) obj;
        return Objects.equals(o.prefix, this.prefix) && Objects.equals(o.cvOutput, this.cvOutput);
    }

    private void setSuccessor(I symbol, ObservationTreeNode<I> successor) {
        successors.put(alphabet.getSymbolIndex(symbol), successor);
    }

    @Nullable
    ObservationTreeNode<I> getSuccessor(I symbol) {
        if (!alphabet.containsSymbol(symbol)) {
            return null;
        }
        return successors.get(alphabet.getSymbolIndex(symbol));
    }

    public Collection<ObservationTreeNode<I>> getSuccessors() {
        return successors.values();
    }

    ObservationTreeNode<I> getParent() {
        return parent;
    }

    boolean getOutput() {
        // isInPrefix() => !isOutsideCounterLimit()
        assert !isInPrefix() || !isOutsideCounterLimit();
        if (parent != null && parent.isOutsideCounterLimit()) {
            assert isOutsideCounterLimit();
        }
        if (isOutsideCounterLimit() || !isInPrefix()) {
            return false;
        }
        if (getStoredOutput() == Output.ACCEPTED) {
            // Being in the prefix and used for full information implies that the counter
            // value must be zero
            assert !(isInPrefix() && !isOnlyForLanguage()) || getCounterValue() == 0;
        }
        return getStoredOutput() == Output.ACCEPTED;
    }

    int getCounterValue() {
        if (isOutsideCounterLimit() || !isInPrefix() || isOnlyForLanguage()) {
            return UNKNOWN_COUNTER_VALUE;
        }
        return getStoredCounterValue();
    }

    PairCounterValueOutput<Boolean> getCounterValueOutput() {
        return new PairCounterValueOutput<>(getOutput(), getCounterValue());
    }

    private void setOutput(boolean output) {
        assert prefix.equals(Word.epsilon()) || isInTable();
        cvOutput.setOutput(output ? Output.ACCEPTED : Output.REJECTED);
    }

    private Output getStoredOutput() {
        return cvOutput.getOutput();
    }

    private void setCounterValue(int counterValue) {
        cvOutput.setCounterValue(counterValue);
    }

    private int getStoredCounterValue() {
        return cvOutput.getCounterValue();
    }

    boolean isInPrefix() {
        return inPrefix;
    }

    private void setInPrefix(boolean inPrefix) {
        this.inPrefix = inPrefix;
    }

    boolean isOutsideCounterLimit() {
        return isOutsideCounterLimit;
    }

    private void setOutsideCounterLimit(boolean outsideCounterLimit) {
        this.isOutsideCounterLimit = outsideCounterLimit;
    }

    boolean isInTable() {
        return tableCells.size() != 0;
    }

    boolean isOnlyForLanguage() {
        for (TableCell<I> cell : tableCells) {
            if (!cell.getRow().getTable().isSuffixOnlyForLanguage(cell.getSuffixIndex())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds nodes in the subtree corresponding to a new prefix in the table, and
     * returns the final reached node.
     * 
     * This function does not ask queries. It just creates the path in the tree to
     * the node corresponding to the new prefix.
     * 
     * If the path is already present, the tree is not modified.
     * 
     * @param prefix        The new prefix
     * @param indexInPrefix How much symbols have been read so far
     * @return The node corresponding to the prefix
     */
    ObservationTreeNode<I> getPrefix(Word<I> prefix, int indexInPrefix) {
        if (indexInPrefix == prefix.length()) {
            return this;
        } else {
            I symbol = prefix.getSymbol(indexInPrefix);
            ObservationTreeNode<I> successor = getOrCreateSuccessor(symbol);
            return successor.getPrefix(prefix, indexInPrefix + 1);
        }
    }

    private ObservationTreeNode<I> getOrCreateSuccessor(I symbol) {
        ObservationTreeNode<I> successor = getSuccessor(symbol);
        if (successor == null) {
            return createSuccessor(symbol);
        }
        return successor;
    }

    private ObservationTreeNode<I> createSuccessor(I symbol) {
        ObservationTreeNode<I> successor = new ObservationTreeNode<>(prefix.append(symbol), this, alphabet);
        setSuccessor(symbol, successor);
        // If we already know that the current node is outside of the counter limit (due
        // to an ancestor's counter value), we want that information to be also present
        // in the successors
        if (isOutsideCounterLimit()) {
            successor.setOutsideCounterLimit(true);
        }
        return successor;
    }

    Word<I> getPrefix() {
        return prefix;
    }

    /**
     * Asks a counter value query to fill C(w).
     * 
     * @param counterValueOracle The oracle
     * @param counterLimit       The counter value
     * @return True iff the counter value is lower or equal to the limit
     */
    private boolean askCounterValueQuery(MembershipOracle.CounterValueOracle<I> counterValueOracle, int counterLimit) {
        if (getStoredCounterValue() == UNKNOWN_COUNTER_VALUE) {
            if (getStoredOutput() == Output.ACCEPTED) {
                setCounterValue(0);
                return true;
            }
            int counterValue = counterValueOracle.answerQuery(getPrefix());
            setCounterValue(counterValue);
            return counterValue <= counterLimit;
        } else {
            return getStoredCounterValue() <= counterLimit;
        }
    }

    private void addToTableCells(RowImpl<I> row, int suffixIndex) {
        for (TableCell<I> cell : tableCells) {
            if (cell.getRow() == row && cell.getSuffixIndex() == suffixIndex) {
                return;
            }
        }
        tableCells.add(new TableCell<>(row, suffixIndex));
    }

    ObservationTreeNode<I> addSuffixInTable(Word<I> suffix, int indexInSuffix,
            MembershipOracle<I, Boolean> membershipOracle, MembershipOracle.CounterValueOracle<I> counterValueOracle,
            int counterLimit, RowImpl<I> row, int suffixIndex) {
        if (indexInSuffix == suffix.length()) {
            if (!row.getTable().isSuffixOnlyForLanguage(suffixIndex) && isOnlyForLanguage()) {
                if (getStoredOutput() != Output.UNKNOWN && isInPrefix()
                        && getStoredCounterValue() == UNKNOWN_COUNTER_VALUE) {
                    setCounterValue(counterValueOracle.answerQuery(prefix));
                }
            }
            addToTableCells(row, suffixIndex);
            int previousCV = getCounterValue();
            // We know that the current node was not just created (i.e., it was not added in
            // the tree thanks to the suffix)
            // Once we have read the whole suffix, we have three possibilities:
            // 1. The node is already used in the table (that is, the output is known).
            // The node may have been added only for language purpose. In that case, we have
            // to ask a counter value query (if we are using the node for full information)
            if (getStoredOutput() != Output.UNKNOWN) {
                // Being in the prefix implies that we are outside the counter limit
                assert (!isInPrefix() || !isOutsideCounterLimit()) : prefix;
                if (!isOnlyForLanguage()) {
                    if (isInPrefix() && getCounterValue() == UNKNOWN_COUNTER_VALUE) {
                        askCounterValueQuery(counterValueOracle, counterLimit);
                    }
                    assert (!(isInPrefix() && !isOnlyForLanguage()) || getCounterValue() != UNKNOWN_COUNTER_VALUE);
                }
                row.outputChange();
                if (previousCV != getCounterValue()) {
                    notifyCounterValueChange();
                } else if (getCounterValue() != UNKNOWN_COUNTER_VALUE) {
                    row.counterValueChange();
                }
                return this;
            }

            // 2. It is the first time the table uses the node (that is, the output is not
            // known).
            // In that case, we ask a membership query. Two cases can arise.
            if (getStoredOutput() == Output.UNKNOWN) {
                boolean actualOutput;
                // If we have already asked a counter value query (to check whether the counter
                // exceeded the limit), we can deduce an easy case from it
                if (getStoredCounterValue() > 0 && getStoredCounterValue() != UNKNOWN_COUNTER_VALUE) {
                    setOutput(false);
                    actualOutput = false;
                } else {
                    actualOutput = membershipOracle.answerQuery(prefix);
                    setOutput(actualOutput);
                }

                // 2.1. The word is not in L. Therefore, it is not in L_l. If the word is in the
                // known prefix, we ask a counter value.
                if (!actualOutput) {
                    if (isInPrefix() && !isOnlyForLanguage()) {
                        askCounterValueQuery(counterValueOracle, counterLimit);
                        // If a word is in the prefix for the counter limit l, then the actual counter
                        // limit can not exceed l
                        assert getStoredCounterValue() <= counterLimit;
                        if (parent != null) {
                            assert !parent.isOutsideCounterLimit();
                        }
                    }
                }
                // 2.2. The word is in L.
                // We must determine whether the word is in L_l. If it is, we need to update the
                // known prefix
                else {
                    // 2.2.1. If the word is in the prefix of the language (then, the word is in
                    // L_l), it's easy.
                    // We just have to ask a counter value query.
                    if (isInPrefix() && !isOnlyForLanguage()) {
                        askCounterValueQuery(counterValueOracle, counterLimit);
                    }
                    // 2.2.2. If the word is not in the prefix of the language, then the counter
                    // value can not be OUTSIDE_COUNTER_LIMIT
                    // We try to ask as few counter value queries as needed, i.e., some of the nodes
                    // will not be changed.
                    else {
                        // 2.2.2.1. We do not yet known the counter value of the word. See the called
                        // function for the explanation.
                        if (getStoredCounterValue() == UNKNOWN_COUNTER_VALUE) {
                            boolean outside_limit = determineIfAncestorExceedsCounterLimit(counterValueOracle,
                                    counterLimit);

                            if (!outside_limit) {
                                // We could not find an ancestor z'' with a counter value outside the limit.
                                // So, we know that the word is in L_l and we can ask a counter value query.
                                // Moreover, we have to update the ancestors to reflect the newly found prefix
                                // of the language.
                                setInPrefix(true);
                                setOutsideCounterLimit(false);

                                if (!isOnlyForLanguage()) {
                                    setCounterValue(0);
                                    assert !isOutsideCounterLimit();
                                }

                                if (parent != null) {
                                    parent.inPrefixUpdate(membershipOracle, counterValueOracle, counterLimit);
                                }
                            } else {
                                markExceedingCounterLimit();
                            }
                        }
                        // 2.2.2.2. The counter value is already known and is zero.
                        // Therefore, the word is in L_l (as the counter value can not be known if an
                        // ancestor exceeds the counter limit)
                        else if (getCounterValue() == 0) {
                            setOutput(true);
                            setInPrefix(true);
                            setOutsideCounterLimit(false);
                            if (parent != null) {
                                parent.inPrefixUpdate(membershipOracle, counterValueOracle, counterLimit);
                            }
                        }
                    }
                }
            }

            row.outputChange();
            if (previousCV != getCounterValue()) {
                notifyCounterValueChange();
            } else if (getCounterValue() != UNKNOWN_COUNTER_VALUE) {
                row.counterValueChange();
            }
            return this;
        } else {
            // If we have not yet read everything, we keep going down the tree
            I symbol = suffix.getSymbol(indexInSuffix);
            ObservationTreeNode<I> successor = getSuccessor(symbol);
            if (successor == null) {
                successor = createSuccessor(symbol);
                return successor.addSuffixInTableNewInTree(suffix, indexInSuffix + 1, membershipOracle,
                        counterValueOracle, counterLimit, row, suffixIndex);
            } else {
                return successor.addSuffixInTable(suffix, indexInSuffix + 1, membershipOracle, counterValueOracle,
                        counterLimit, row, suffixIndex);
            }
        }
    }

    private ObservationTreeNode<I> addSuffixInTableNewInTree(Word<I> suffix, int indexInSuffix,
            MembershipOracle<I, Boolean> membershipOracle, MembershipOracle.CounterValueOracle<I> counterValueOracle,
            int counterLimit, RowImpl<I> row, int suffixIndex) {
        if (indexInSuffix == suffix.length()) {
            addToTableCells(row, suffixIndex);
            // We know that the current node has just been added in the tree.
            // So, we ask a membership query. If the answer is negative, we have nothing to
            // do (the node and the newly added ancestors are not in the prefix of the
            // language).
            // If the answer is positive, we have to determine whether the word is in L_l,
            // i.e., if there is an ancestor with a counter value exceeding the counter
            // limit.
            // This is the similar to case 2.2.2.1. of the main function.
            boolean actualOutput = membershipOracle.answerQuery(prefix);
            setOutput(actualOutput);

            if (actualOutput) {
                boolean outside_limit = determineIfAncestorExceedsCounterLimit(counterValueOracle, counterLimit);

                if (!outside_limit) {
                    // We could not find an ancestor z'' with a counter value outside the limit.
                    // So, we know that the word is in L_l and we can ask a counter value query.
                    // Moreover, we have to update the ancestors to reflect the newly found prefix
                    // of the language.
                    setInPrefix(true);
                    setOutsideCounterLimit(false);

                    if (!isOnlyForLanguage()) {
                        setCounterValue(0);
                    }

                    if (parent != null) {
                        parent.inPrefixUpdate(membershipOracle, counterValueOracle, counterLimit);
                    }
                } else {
                    markExceedingCounterLimit();
                }
            } else if (isInPrefix() && isOnlyForLanguage()) {
                askCounterValueQuery(counterValueOracle, counterLimit);
            }

            // We know that there is only row using this node
            row.outputChange();
            if (getCounterValue() != UNKNOWN_COUNTER_VALUE) {
                row.counterValueChange();
            }
            return this;
        } else {
            I symbol = suffix.getSymbol(indexInSuffix);
            ObservationTreeNode<I> successor = createSuccessor(symbol);
            return successor.addSuffixInTableNewInTree(suffix, indexInSuffix + 1, membershipOracle, counterValueOracle,
                    counterLimit, row, suffixIndex);
        }
    }

    private List<ObservationTreeNode<I>> getAncestorsStartingFromLastKnownCounterValue() {
        if (getStoredCounterValue() != UNKNOWN_COUNTER_VALUE) {
            List<ObservationTreeNode<I>> l = new ArrayList<>();
            l.add(this);
            return l;
        } else {
            List<ObservationTreeNode<I>> l = parent.getAncestorsStartingFromLastKnownCounterValue();
            l.add(this);
            return l;
        }
    }

    /**
     * Determines if one the ancestor of this node has an actual counter limit
     * strictly greater than the counter limit.
     * 
     * In other words, it determine whether the current node can potentially be in
     * L_l or if it must be immediately rejected.
     * 
     * @param counterValueOracle The counter value oracle
     * @param counterLimit       The counter limit
     * @return True iff one of the ancestor exceeds the counter limit
     */
    private boolean determineIfAncestorExceedsCounterLimit(MembershipOracle.CounterValueOracle<I> counterValueOracle,
            int counterLimit) {
        if (parent != null && parent.isOutsideCounterLimit()) {
            return true;
        }
        // We do not yet known the counter value of the word. Let's call the current
        // node z.
        // In that case, there exists an ancestor z' with a known counter value (and all
        // the nodes between z and z' do not know the counter value).
        // Say the counter value of z' is c. We know that c is in {0, ..., l}.
        // Therefore, the first node z'' that may have a counter value outside of the
        // counter limit is ((l + 1) - c) deeper than z' (i.e., it's a descendant of z'
        // and an ancestor of z).
        // If such a node exists, we ask a counter value query. If the counter value is
        // outside the limit, then we have found a witness.
        // Otherwise, we repeat with z' = z'' (i.e., we search again but deeper in the
        // tree).
        // If we could not have a z'' with a counter value outside of the limit, we can
        // deduce that the word is in L_l.
        // z is an ancestor of z
        List<ObservationTreeNode<I>> ancestors = getAncestorsStartingFromLastKnownCounterValue();
        int currentAncestorId = 0;
        while (currentAncestorId < ancestors.size()) {
            ObservationTreeNode<I> ancestor = ancestors.get(currentAncestorId);
            // The first ancestor must have a known counter value
            assert ancestor.getStoredCounterValue() != UNKNOWN_COUNTER_VALUE;

            // z' exceeds the counter limit. So, we already have a witness
            if (ancestor != this && ancestor.isOutsideCounterLimit()) {
                if (currentAncestorId + 1 < ancestors.size()) {
                    ancestors.get(currentAncestorId + 1).markExceedingCounterLimit();
                }
                return true;
            }

            int firstPotentialOutsideCVAncestorIndex = (counterLimit + 1) - ancestor.getStoredCounterValue()
                    + currentAncestorId;
            // If the first potential ancestor exceeding the counter limit is actually a
            // descendant of z, we know that the counter limit can not be exceeded.
            if (firstPotentialOutsideCVAncestorIndex >= ancestors.size()) {
                return false;
            }
            ObservationTreeNode<I> potentialOutsideCVAncestor = ancestors.get(firstPotentialOutsideCVAncestorIndex);
            assert potentialOutsideCVAncestor.getStoredCounterValue() == UNKNOWN_COUNTER_VALUE;

            boolean insideCounterLimit = potentialOutsideCVAncestor.askCounterValueQuery(counterValueOracle,
                    counterLimit);

            if (!insideCounterLimit) {
                potentialOutsideCVAncestor.markExceedingCounterLimit();
                return true;
            } else {
                currentAncestorId = firstPotentialOutsideCVAncestorIndex;
                potentialOutsideCVAncestor.notifyCounterValueChange();
                if (potentialOutsideCVAncestor.getStoredCounterValue() > 0 && potentialOutsideCVAncestor.isInTable()) {
                    potentialOutsideCVAncestor.setOutput(false);
                }
            }
        }
        return false;
    }

    void oneCellNowUsesNodeForCounterValue(MembershipOracle.CounterValueOracle<I> counterValueOracle,
            int counterLimit) {
        // Determining whether the node actually belongs to the language is done at the
        // node's creation (i.e., we already know if one of the ancestors exceed the
        // counter limit)
        // So, we just need to ask a new counter value query
        if (isInPrefix()) {
            int previousCV = getCounterValue();
            if (getStoredOutput() == Output.ACCEPTED) {
                setCounterValue(0);
            } else {
                askCounterValueQuery(counterValueOracle, counterLimit);
            }
            if (previousCV != getCounterValue()) {
                notifyCounterValueChange();
            }
        }
    }

    void increaseCounterLimit(MembershipOracle<I, Boolean> membershipOracle,
            MembershipOracle.CounterValueOracle<I> counterValueOracle, int counterLimit) {
        // To increase the counter limit in the tree, we have to go down to the nodes
        // that are ACCEPTED.
        // For each seen node, we remove the flag "outside counter limit".
        // If during the descent, we reach a state for which the counter value is known
        // and that is such that the counter value exceeds the limit, we already know
        // that the subtree must be marked as outside the counter limit.

        // Invariant: the parent's counter value does not exceed the counter limit (or
        // if it does, we do not yet have a witness)
        assert parent == null || !parent.isOutsideCounterLimit();
        if (getCounterValue() == UNKNOWN_COUNTER_VALUE) {
            if (getStoredOutput() == Output.UNKNOWN && isInTable()) {
                setOutput(membershipOracle.answerQuery(prefix));
            }
            if (getStoredOutput() == Output.ACCEPTED) {
                // We verify if the counter value of one of the ancestors exceeds the limit
                if (!determineIfAncestorExceedsCounterLimit(counterValueOracle, counterLimit)) {
                    setInPrefix(true);
                    setOutsideCounterLimit(false);
                    if (!isOnlyForLanguage()) {
                        setCounterValue(0);
                    }
                    notifyOutputChange();
                    if (parent != null) {
                        parent.inPrefixUpdate(membershipOracle, counterValueOracle, counterLimit);
                    }
                } else {
                    setInPrefix(false);
                    markExceedingCounterLimit();
                }
            }

            if (getStoredCounterValue() > counterLimit) {
                markExceedingCounterLimit();
            } else if (!parent.isOutsideCounterLimit()) {
                setOutsideCounterLimit(false);
                notifyCounterValueChange();
            }
        }

        if (!isOutsideCounterLimit()) {
            for (ObservationTreeNode<I> successor : successors.values()) {
                if (!isOutsideCounterLimit()) {
                    successor.increaseCounterLimit(membershipOracle, counterValueOracle, counterLimit);
                }
            }
        }
    }

    private void inPrefixUpdate(MembershipOracle<I, Boolean> membershipOracle,
            MembershipOracle.CounterValueOracle<I> counterValueOracle, int counterLimit) {
        // If we already know the node is in the prefix of the language, we do not need
        // to keep climbing up
        if (isInPrefix()) {
            return;
        }
        setInPrefix(true);

        if (isInTable()) {
            if (getStoredOutput() == Output.UNKNOWN) {
                boolean output = membershipOracle.answerQuery(prefix);
                setOutput(output);
            }
            if (getStoredOutput() == Output.ACCEPTED) {
                setCounterValue(0);
                notifyOutputChange();
                notifyCounterValueChange();
            }
            if (getStoredCounterValue() == UNKNOWN_COUNTER_VALUE && !isOnlyForLanguage()) {
                boolean insideCounterLimit = askCounterValueQuery(counterValueOracle, counterLimit);
                if (!insideCounterLimit) {
                    markExceedingCounterLimit();
                } else {
                    notifyCounterValueChange();
                }
            }
        }

        // We keep climbing up the tree to update the ancestors
        if (parent != null) {
            parent.inPrefixUpdate(membershipOracle, counterValueOracle, counterLimit);
        }
    }

    private void markExceedingCounterLimit() {
        if (isOutsideCounterLimit()) {
            return;
        }
        setOutsideCounterLimit(true);
        for (ObservationTreeNode<I> child : successors.values()) {
            child.markExceedingCounterLimit();
        }
    }

    List<TableCell<I>> getCellsInTableUsingNode() {
        return tableCells;
    }

    private void notifyOutputChange() {
        tableCells.stream().forEach(c -> c.getRow().outputChange());
    }

    private void notifyCounterValueChange() {
        tableCells.stream().forEach(c -> c.getRow().counterValueChange());
    }

    /**
     * Creates a pretty display of the tree.
     * 
     * @return A string containing the pretty display.
     */
    public String toStringRepresentation() {
        StringBuilder builder = new StringBuilder();
        createRepresentation(builder, "", "");
        return builder.toString();
    }

    @Override
    public String toString() {
        return toStringRepresentation();
    }

    private void createRepresentation(StringBuilder buffer, String prefix, String childrenPrefix) {
        buffer.append(prefix);
        buffer.append(this.prefix + " " + this.cvOutput + " " + isInPrefix() + " " + isInTable() + " "
                + isOutsideCounterLimit() + " " + isOnlyForLanguage());
        buffer.append('\n');
        for (Iterator<ObservationTreeNode<I>> it = successors.values().iterator(); it.hasNext();) {
            ObservationTreeNode<I> next = it.next();
            if (it.hasNext()) {
                next.createRepresentation(buffer, childrenPrefix + "├── ", childrenPrefix + "│   ");
            } else {
                next.createRepresentation(buffer, childrenPrefix + "└── ", childrenPrefix + "    ");
            }
        }
    }
}
