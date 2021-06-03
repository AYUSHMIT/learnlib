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

/**
 * A node in a prefix tree used by {@link ObservationTableWithCounterValuesROCA}
 * to actually store the observations.
 * 
 * This implementation assumes the target ROCA accepts by final state and
 * counter value equal to zero.
 * 
 * A node stores four elements:
 * <ul>
 * <li>The actual answers to the membership and counter value queries (i.e.,
 * whether the word belongs in L and the counter value associated to the word),
 * noted L(w) and C(w).</li>
 * <li>The output and counter value, up to the counter limit (i.e., whether the
 * word belongs in L_l and the counter value), noted L_l(w) and C_l(w). It may
 * happen than the L(w) = true but the L_l(w) = false because a prefix of the
 * word exceeds the counter limit. The values of C_l(w) can be internally in {0,
 * ..., l} U {OUTSIDE_COUNTER_LIMIT, UNKNOWN_COUNTER_VALUE} (with l the counter
 * limit). However, the values visible to the learner are in {0, ..., l} U
 * {UNKNOWN_COUNTER_VALUE}. That is, the value OUTSIDE_COUNTER_LIMIT is merged
 * into UNKNOWN_COUNTER_VALUE when the learner processes the table.</li>
 * <li>Whether the word is in the prefix of the language L_l, noted P(w).</li>
 * <li>Whether the node is actually used in the table. Since the tree is closed
 * by prefix, there are nodes that are not actually used by the learner's
 * table.</li>
 * </ul>
 * 
 * Storing the actual answers to the queries allow us to reduce the number of
 * asked queries (since we do not have to ask multiple times the same queries).
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

    final static int UNKNOWN_COUNTER_VALUE = -1;
    final static int OUTSIDE_COUNTER_LIMIT = -2;

    private final @Nullable ObservationTreeNode<I> parent;
    private final Map<Integer, ObservationTreeNode<I>> successors = new HashMap<>();

    private final Alphabet<I> alphabet;

    private final Word<I> prefix;
    private final PairCounterValueOutput<Output> actualCvOutput;
    private final PairCounterValueOutput<Output> cvOutput;

    boolean inPrefix = false;
    private boolean inTable = false;

    ObservationTreeNode(Word<I> prefix, ObservationTreeNode<I> parent, Alphabet<I> alphabet) {
        this.parent = parent;
        this.alphabet = alphabet;
        this.prefix = prefix;
        this.cvOutput = new PairCounterValueOutput<>(Output.UNKNOWN, UNKNOWN_COUNTER_VALUE);
        this.actualCvOutput = new PairCounterValueOutput<>(Output.UNKNOWN, UNKNOWN_COUNTER_VALUE);

        if (parent != null && parent.getCounterValue() == OUTSIDE_COUNTER_LIMIT) {
            markOutsideCounterLimit();
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(inPrefix, cvOutput, prefix);
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
        return o.inPrefix == this.inPrefix && Objects.equals(o.prefix, prefix)
                && Objects.equals(o.cvOutput, this.cvOutput);
    }

    private void setSuccessor(I symbol, ObservationTreeNode<I> successor) {
        successors.put(alphabet.getSymbolIndex(symbol), successor);
    }

    @Nullable
    ObservationTreeNode<I> getSuccessor(I symbol) {
        return successors.get(alphabet.getSymbolIndex(symbol));
    }

    public Collection<ObservationTreeNode<I>> getSuccessors() {
        return successors.values();
    }

    ObservationTreeNode<I> getParent() {
        return parent;
    }

    int getCounterValue() {
        return cvOutput.getCounterValue();
    }

    int getSimplifiedCounterValue() {
        int cv = getCounterValue();
        if (cv == OUTSIDE_COUNTER_LIMIT) {
            return UNKNOWN_COUNTER_VALUE;
        } else {
            return cv;
        }
    }

    void setCounterValue(int counterValue) {
        cvOutput.setCounterValue(counterValue);
    }

    int getActualCounterValue() {
        return actualCvOutput.getCounterValue();
    }

    void setActualCounterValue(int counterValue) {
        actualCvOutput.setCounterValue(counterValue);
    }

    boolean getOutput() {
        return cvOutput.getOutput() == Output.ACCEPTED;
    }

    boolean getActualOutput() {
        return actualCvOutput.getOutput() == Output.ACCEPTED;
    }

    void setOutput(boolean output) {
        cvOutput.setOutput(output ? Output.ACCEPTED : Output.REJECTED);
    }

    void setActualOutput(boolean output) {
        actualCvOutput.setOutput(output ? Output.ACCEPTED : Output.REJECTED);
    }

    PairCounterValueOutput<Output> getCvOutput() {
        return cvOutput;
    }

    PairCounterValueOutput<Output> getActualCvOutput() {
        return actualCvOutput;
    }

    boolean isInPrefix() {
        return inPrefix;
    }

    boolean isInTable() {
        return inTable;
    }

    PairCounterValueOutput<Boolean> getCounterValueOutput() {
        return new PairCounterValueOutput<>(cvOutput.getOutput() == Output.ACCEPTED, cvOutput.getCounterValue());
    }

    PairCounterValueOutput<Boolean> getSimplifiedCounterValueOutput() {
        return new PairCounterValueOutput<>(getOutput(), getSimplifiedCounterValue());
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
            ObservationTreeNode<I> successor = getSuccessor(symbol);
            if (successor == null) {
                successor = new ObservationTreeNode<>(prefix.subWord(0, indexInPrefix + 1), this, alphabet);
                setSuccessor(symbol, successor);
            }

            return successor.getPrefix(prefix, indexInPrefix + 1);
        }
    }

    Word<I> getPrefix() {
        return prefix;
    }

    ObservationTreeNode<I> addSuffixInTable(Word<I> suffix, int indexInSuffix,
            MembershipOracle<I, Boolean> membershipOracle, MembershipOracle<I, Integer> counterValueOracle,
            int counterLimit) {
        if (indexInSuffix == suffix.length()) {
            inTable = true;
            // We know that the current node was not just created (i.e., it was not added in
            // the tree thanks to the suffix)
            // Once we have read the whole suffix, we have three possibilities:
            // 1. The node is already used in the table (that is, the output is known).
            // In that case, we have nothing to do.
            if (cvOutput.getOutput() != Output.UNKNOWN) {
                assert (!inPrefix || cvOutput.getCounterValue() != UNKNOWN_COUNTER_VALUE);
                return this;
            }

            // 2. It is the first time the table uses the node (that is, the output is not
            // known).
            // In that case, we ask a membership query. Two cases can arise.
            if (cvOutput.getOutput() == Output.UNKNOWN) {
                assert actualCvOutput.getOutput() == Output.UNKNOWN;

                boolean actualOutput = membershipOracle.answerQuery(prefix);
                setActualOutput(actualOutput);

                // 2.1. The word is not in L. Therefore, it is not in L_l. If the word is in the
                // known prefix, we ask a counter value.
                if (!actualOutput) {
                    setOutput(false);
                    if (inPrefix && cvOutput.getCounterValue() == UNKNOWN_COUNTER_VALUE) {
                        // If a word is in the prefix for the counter limit l, then the actual counter
                        // limit can not exceed l
                        int actualCounterValue = counterValueOracle.answerQuery(prefix);
                        assert actualCounterValue <= counterLimit;
                        setActualCounterValue(actualCounterValue);
                        setCounterValue(actualCounterValue);
                        if (parent != null) {
                            assert parent.getCounterValue() != OUTSIDE_COUNTER_LIMIT;
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
                    if (inPrefix) {
                        setOutput(true);
                        if (cvOutput.getCounterValue() == UNKNOWN_COUNTER_VALUE) {
                            int actualCounterValue = counterValueOracle.answerQuery(prefix);
                            assert actualCounterValue <= counterLimit;
                            setActualCounterValue(actualCounterValue);
                            setCounterValue(actualCounterValue);
                            assert getCounterValue() == 0;
                            if (parent != null) {
                                assert parent.getCounterValue() != OUTSIDE_COUNTER_LIMIT;
                            }
                        }
                    }
                    // 2.2.2. If the word is not in the prefix of the language, then the counter
                    // value can not be OUTSIDE_COUNTER_LIMIT
                    // We try to ask as few counter value queries as needed, i.e., some of the nodes
                    // will not be changed.
                    else {
                        assert getCounterValue() != OUTSIDE_COUNTER_LIMIT;
                        // 2.2.2.1. We do not yet known the counter value of the word. See the called
                        // function for the explanation.
                        if (getCounterValue() == UNKNOWN_COUNTER_VALUE) {
                            boolean outside_limit = determineIfAncestorExceedsCounterLimit(counterValueOracle,
                                    counterLimit);

                            if (!outside_limit) {
                                // We could not find an ancestor z'' with a counter value outside the limit.
                                // So, we know that the word is in L_l and we can ask a counter value query.
                                // Moreover, we have to update the ancestors to reflect the newly found prefix
                                // of the language.
                                setOutput(true);
                                int actualCounterValue = counterValueOracle.answerQuery(prefix);
                                assert actualCounterValue <= counterLimit;
                                setActualCounterValue(actualCounterValue);
                                setCounterValue(actualCounterValue);
                                assert getCounterValue() == 0;
                                if (parent != null) {
                                    assert parent.getCounterValue() != OUTSIDE_COUNTER_LIMIT;
                                }

                                inPrefix = true;
                                if (parent != null) {
                                    parent.inPrefixUpdate(membershipOracle, counterValueOracle, counterLimit);
                                }
                            }
                        }
                        // 2.2.2.2. The counter value is already known and is zero.
                        // Therefore, the word is in L_l (as the counter value can not be known if an
                        // ancestor exceeds the counter limit)
                        else if (getCounterValue() == 0) {
                            setOutput(true);
                            inPrefix = true;
                            if (parent != null) {
                                parent.inPrefixUpdate(membershipOracle, counterValueOracle, counterLimit);
                            }
                        } else {
                            setOutput(false);
                        }
                    }
                }
            }

            return this;
        } else {
            // If we have not yet read everything, we keep going down the tree
            I symbol = suffix.getSymbol(indexInSuffix);
            ObservationTreeNode<I> successor = getSuccessor(symbol);
            if (successor == null) {
                successor = new ObservationTreeNode<>(prefix.append(symbol), this, alphabet);
                setSuccessor(symbol, successor);
                return successor.addSuffixInTableNewInTree(suffix, indexInSuffix + 1, membershipOracle,
                        counterValueOracle, counterLimit);
            } else {
                return successor.addSuffixInTable(suffix, indexInSuffix + 1, membershipOracle, counterValueOracle,
                        counterLimit);
            }
        }
    }

    private ObservationTreeNode<I> addSuffixInTableNewInTree(Word<I> suffix, int indexInSuffix,
            MembershipOracle<I, Boolean> membershipOracle, MembershipOracle<I, Integer> counterValueOracle,
            int counterLimit) {
        if (indexInSuffix == suffix.length()) {
            // We know that the current node has just been added in the tree.
            // So, we ask a membership query. If the answer is negative, we have nothing to
            // do (the node and the newly added ancestors are not in the prefix of the
            // language).
            // If the answer is positive, we have to determine whether the word is in L_l,
            // i.e., if there is an ancestor with a counter value exceeding the counter
            // limit.
            // This is the similar to case 2.2.2.1. of the main function.
            inTable = true;
            boolean actualOutput = membershipOracle.answerQuery(prefix);
            setActualOutput(actualOutput);

            if (actualOutput) {
                boolean outside_limit = determineIfAncestorExceedsCounterLimit(counterValueOracle, counterLimit);

                if (!outside_limit) {
                    // We could not find an ancestor z'' with a counter value outside the limit.
                    // So, we know that the word is in L_l and we can ask a counter value query.
                    // Moreover, we have to update the ancestors to reflect the newly found prefix
                    // of the language.
                    setOutput(true);
                    int actualCounterValue = counterValueOracle.answerQuery(prefix);
                    setActualCounterValue(actualCounterValue);
                    setCounterValue(actualCounterValue);
                    assert getCounterValue() == 0;

                    inPrefix = true;
                    if (parent != null) {
                        parent.inPrefixUpdate(membershipOracle, counterValueOracle, counterLimit);
                    }
                }
            } else {
                setOutput(false);
                if (inPrefix) {
                    int actualCounterValue = counterValueOracle.answerQuery(prefix);
                    assert actualCounterValue <= counterLimit;
                    setActualCounterValue(actualCounterValue);
                    setCounterValue(actualCounterValue);
                }
            }

            return this;
        } else {
            I symbol = suffix.getSymbol(indexInSuffix);
            ObservationTreeNode<I> successor = new ObservationTreeNode<>(prefix.append(symbol), this, alphabet);
            setSuccessor(symbol, successor);
            return successor.addSuffixInTableNewInTree(suffix, indexInSuffix + 1, membershipOracle, counterValueOracle,
                    counterLimit);
        }
    }

    private List<ObservationTreeNode<I>> getAncestorsStartingFromLastKnownCounterValue() {
        if (getCounterValue() != UNKNOWN_COUNTER_VALUE) {
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
    private boolean determineIfAncestorExceedsCounterLimit(MembershipOracle<I, Integer> counterValueOracle,
            int counterLimit) {
        if (parent != null && parent.getCounterValue() == OUTSIDE_COUNTER_LIMIT) {
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
            int counterValueAncestor = ancestors.get(currentAncestorId).getCounterValue();
            // The first ancestor must have a known counter value
            assert counterValueAncestor != UNKNOWN_COUNTER_VALUE;

            // z' exceeds the counter limit. So, we already have a witness
            if (counterValueAncestor == OUTSIDE_COUNTER_LIMIT) {
                if (currentAncestorId + 1 < ancestors.size()) {
                    ancestors.get(currentAncestorId + 1).markOutsideCounterLimit();
                }
                return true;
            }

            int firstPotentialOutsideCVAncestorIndex = (counterLimit + 1) - counterValueAncestor + currentAncestorId;
            // If the first potential ancestor exceeding the counter limit is actually a
            // descendant of z, we know that the counter limit can not be exceeded.
            if (firstPotentialOutsideCVAncestorIndex >= ancestors.size()) {
                return false;
            }
            ObservationTreeNode<I> potentialOutsideCVAncestor = ancestors.get(firstPotentialOutsideCVAncestorIndex);
            assert potentialOutsideCVAncestor.getActualCounterValue() == UNKNOWN_COUNTER_VALUE;

            int actualCounterValueAncestor = counterValueOracle.answerQuery(potentialOutsideCVAncestor.getPrefix());
            potentialOutsideCVAncestor.setActualCounterValue(actualCounterValueAncestor);

            if (actualCounterValueAncestor > counterLimit) {
                potentialOutsideCVAncestor.markOutsideCounterLimit();
                return true;
            } else {
                currentAncestorId = firstPotentialOutsideCVAncestorIndex;
                potentialOutsideCVAncestor.setCounterValue(actualCounterValueAncestor);
                if (actualCounterValueAncestor > 0) {
                    potentialOutsideCVAncestor.setOutput(false);
                }
            }
        }
        return false;
    }

    private void markOutsideCounterLimit() {
        if (getCounterValue() == OUTSIDE_COUNTER_LIMIT) {
            return;
        }
        setCounterValue(OUTSIDE_COUNTER_LIMIT);
        setOutput(false);
        for (ObservationTreeNode<I> successor : successors.values()) {
            successor.markOutsideCounterLimit();
        }
    }

    ObservationTreeNode<I> getSuffix(Word<I> suffix, int indexInSuffix) {
        if (indexInSuffix == suffix.length()) {
            return this;
        }
        I symbol = suffix.getSymbol(indexInSuffix);
        ObservationTreeNode<I> successor = getSuccessor(symbol);
        if (successor == null) {
            return null;
        }
        return successor.getSuffix(suffix, indexInSuffix + 1);
    }

    void increaseCounterLimit(MembershipOracle<I, Boolean> membershipOracle,
            MembershipOracle<I, Integer> counterValueOracle, int counterLimit) {
        if (getCounterValue() == OUTSIDE_COUNTER_LIMIT && getActualCounterValue() <= counterLimit) {
            if (parent == null || parent.getCounterValue() != OUTSIDE_COUNTER_LIMIT) {
                setCounterValue(getActualCounterValue());

                if (actualCvOutput.getOutput() == Output.UNKNOWN) {
                    boolean output = membershipOracle.answerQuery(prefix);
                    setActualOutput(output);
                }

                if (!determineIfAncestorExceedsCounterLimit(counterValueOracle, counterLimit)) {
                    cvOutput.setOutput(actualCvOutput.getOutput());

                    if (getOutput()) {
                        inPrefix = true;

                        if (getCounterValue() == UNKNOWN_COUNTER_VALUE) {
                            int counterValue = counterValueOracle.answerQuery(prefix);
                            assert counterValue <= counterLimit;
                            setCounterValue(counterValue);
                            setActualCounterValue(counterValue);
                            if (parent != null) {
                                assert parent.getCounterValue() != OUTSIDE_COUNTER_LIMIT;
                            }
                        }

                        if (parent != null) {
                            parent.inPrefixUpdate(membershipOracle, counterValueOracle, counterLimit);
                        }
                    }
                }
            }
        }

        if (getActualCounterValue() <= counterLimit) {
            for (ObservationTreeNode<I> successor : successors.values()) {
                successor.increaseCounterLimit(membershipOracle, counterValueOracle, counterLimit);
            }
        }
    }

    private void inPrefixUpdate(MembershipOracle<I, Boolean> membershipOracle,
            MembershipOracle<I, Integer> counterValueOracle, int counterLimit) {
        // If we already know the node is in the prefix of the language, we do not need
        // to keep climbing up
        if (inPrefix) {
            return;
        }
        inPrefix = true;

        if (getCounterValue() == UNKNOWN_COUNTER_VALUE) {
            boolean output = membershipOracle.answerQuery(prefix);
            setActualOutput(output);
            setOutput(output);
            int counterValue = counterValueOracle.answerQuery(prefix);
            assert counterValue <= counterLimit;
            setActualCounterValue(counterValue);
            setCounterValue(counterValue);
            if (parent != null) {
                assert parent.getCounterValue() != OUTSIDE_COUNTER_LIMIT;
            }
        }

        // We keep climbing up the tree to update the ancestors
        if (parent != null) {
            parent.inPrefixUpdate(membershipOracle, counterValueOracle, counterLimit);
        }
    }

    /**
     * Creates a pretty display of the tree.
     * 
     * @return A string containing the pretty display.
     */
    public String toStringRepresentation() {
        StringBuilder builder = new StringBuilder();
        print(builder, "", "");
        return builder.toString();
    }

    @Override
    public String toString() {
        return toStringRepresentation();
    }

    private void print(StringBuilder buffer, String prefix, String childrenPrefix) {
        buffer.append(prefix);
        buffer.append(this.prefix + " " + this.cvOutput + " " + this.actualCvOutput + " " + this.inPrefix + " "
                + this.inTable);
        buffer.append('\n');
        for (Iterator<ObservationTreeNode<I>> it = successors.values().iterator(); it.hasNext();) {
            ObservationTreeNode<I> next = it.next();
            if (it.hasNext()) {
                next.print(buffer, childrenPrefix + "├── ", childrenPrefix + "│   ");
            } else {
                next.print(buffer, childrenPrefix + "└── ", childrenPrefix + "    ");
            }
        }
    }
}
