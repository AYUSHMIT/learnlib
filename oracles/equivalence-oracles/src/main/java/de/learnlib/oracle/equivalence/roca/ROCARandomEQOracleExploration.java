package de.learnlib.oracle.equivalence.roca;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.State;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * The actual exploration used in {@link ROCARandomEQOracle}.
 * 
 * That is, both ROCAs are explored in parallel (same transitions must be seen
 * at the same time in both ROCAs) up to a fixed counter limit.
 * 
 * @param <L1> The location type of the first ROCA
 * @param <L2> The location type of the second ROCA
 */
class ROCARandomEQOracleExploration<I, L1, L2> {
    private final ROCA<L1, I> roca1;
    private final ROCA<L2, I> roca2;
    private final Alphabet<I> alphabet;
    private final Set<InQueue> frontier = new HashSet<>();

    public ROCARandomEQOracleExploration(final ROCA<L1, I> roca1, final ROCA<L2, I> roca2, final Alphabet<I> alphabet) {
        this.roca1 = roca1;
        this.roca2 = roca2;
        this.alphabet = alphabet;
    }

    class InQueue {
        public Word<I> word;
        public State<L1> state1;
        public State<L2> state2;

        InQueue(Word<I> word, State<L1> state1, State<L2> state2) {
            this.word = word;
            this.state1 = state1;
            this.state2 = state2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(state1, state2);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj == this) {
                return true;
            }

            if (obj.getClass() != getClass()) {
                return false;
            }
            @SuppressWarnings("unchecked")
            InQueue o = (InQueue) obj;
            return Objects.equals(o.state1, this.state1) && Objects.equals(o.state2, this.state2);
        }

        @Override
        public String toString() {
            return "(" + word + ", " + state1 + ", " + state2 + ")";
        }
    }

    /**
     * Finds a word that distinguishes roca1 and roca2.
     * 
     * That is, it finds a word that is accepted by roca1 but not by roca2, or
     * vice-versa. If the ROCAs are equivalent, it returns null. The exploration is
     * limited to the maximum counter value.
     * 
     * @param maxCounterValue The maximal counter value for the exploration
     * @return A word that separated both ROCAs, or null if the ROCAs are
     *         equivalent.
     */
    public @Nullable Word<I> findSeparatingWordByExploration(final long maxCounterValue) {
        // The idea is to explore the state space of both ROCAs at once with a parallel
        // BFS, up to the given maximal counter value.
        // If, at some point, we see a pair of non-equivalent states, then we have a
        // separating word.
        if (roca1.isAccepting(roca1.getInitialState()) != roca2.isAccepting(roca2.getInitialState())) {
            return Word.epsilon();
        }

        final Queue<InQueue> toExplore = new LinkedList<>();
        final Set<InQueue> seen = new HashSet<>();

        if (frontier.isEmpty()) {
            toExplore.add(new InQueue(Word.epsilon(), roca1.getInitialState(), roca2.getInitialState()));
        } else {
            toExplore.addAll(frontier);
            frontier.clear();
        }

        while (toExplore.size() != 0) {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                return null;
            }
            final InQueue current = toExplore.poll();

            for (final I a : alphabet) {
                State<L1> target1 = roca1.getTransition(current.state1, a);
                State<L2> target2 = roca2.getTransition(current.state2, a);
                Word<I> newWord = current.word.append(a);
                if (roca1.isAccepting(target1) != roca2.isAccepting(target2)) {
                    return newWord;
                }

                if (target1 == null && target2 == null) {
                    continue;
                }

                if (target1 != null && target1.getCounterValue() > maxCounterValue) {
                    frontier.add(new InQueue(newWord, target1, target2));
                }
                if (target2 != null && target2.getCounterValue() > maxCounterValue) {
                    frontier.add(new InQueue(newWord, target1, target2));
                }

                if ((target1 == null && target2.getCounterValue() <= maxCounterValue)
                        || (target2 == null && target1.getCounterValue() <= maxCounterValue)
                        || (target1 != null && target2 != null && target1.getCounterValue() <= maxCounterValue
                                && target2.getCounterValue() <= maxCounterValue)) {
                    InQueue next = new InQueue(newWord, target1, target2);
                    if (!seen.contains(next)) {
                        toExplore.add(next);
                        seen.add(next);
                    }
                }
            }
        }

        return null;
    }
}
