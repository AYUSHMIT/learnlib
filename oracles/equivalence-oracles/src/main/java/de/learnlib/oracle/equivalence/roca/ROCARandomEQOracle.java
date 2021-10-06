package de.learnlib.oracle.equivalence.roca;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.State;
import net.automatalib.automata.oca.automatoncountervalues.ROCAFromDescription;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * Equivalence oracles for ROCAs based on a complete exploration that goes further and further, repeated a random number of times.
 * 
 * It is assumed the hypothesis is constructed from a periodic description.
 * 
 * The oracle may not finish for a correct hypothesis.
 * However, if the hypothesis is incorrect, it is guaranteed to finish (but may not necessarily return a counterexample).
 * 
 * @param <I> Alphabet type
 * @author Gaëtan Staquet
 */
public class ROCARandomEQOracle<I> implements EquivalenceOracle.ROCAEquivalenceOracle<I> {

    private final ROCA<?, I> reference;
    private final Alphabet<I> alphabet;
    private final Random random;

    public ROCARandomEQOracle(final ROCA<?, I> reference) {
        this(reference, reference.getAlphabet());
    }

    public ROCARandomEQOracle(final ROCA<?, I> reference, final Alphabet<I> alphabet) {
        this(reference, alphabet, new Random());
    }

    public ROCARandomEQOracle(final ROCA<?, I> reference, final Alphabet<I> alphabet, final Random rand) {
        this.reference = reference;
        this.alphabet = alphabet;
        this.random = rand;
    }
    
    /**
     * Finds a word that distinguishes roca1 and roca2.
     * 
     * That is, it finds a word that is accepted by roca1 but not by roca2, or
     * vice-versa. If the ROCAs are equivalent, it returns null.
     * 
     * @param <L1>     The location type of the first ROCA
     * @param <L2>     The location type of the second ROCA
     * @param roca1    The first ROCA
     * @param roca2    The second ROCA
     * @param alphabet The alphabet
     * @return A word that separated both ROCAs, or null if the ROCAs are
     *         equivalent.
     */
    private <L1, L2> @Nullable Word<I> findSeparatingWordByExploration(final ROCA<L1, I> roca1, final ROCA<L2, I> roca2,
            final Alphabet<I> alphabet, final long maxCounterValue) {
        // The idea is to explore the state space of both ROCAs at once with a parallel
        // BFS, up to the given maximal counter value.
        // If, at some point, we see a pair of non-equivalent states, then we have a
        // separating word.

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
        }

        if (roca1.isAccepting(roca1.getInitialState()) != roca2.isAccepting(roca2.getInitialState())) {
            return Word.epsilon();
        }

        final Queue<InQueue> toExplore = new LinkedList<>();
        final Set<InQueue> seen = new HashSet<>();

        toExplore.add(new InQueue(Word.epsilon(), roca1.getInitialState(), roca2.getInitialState()));

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

    @Override
    public @Nullable DefaultQuery<I, Boolean> findCounterExample(ROCAFromDescription<?, I> hypothesis,
            Collection<? extends I> inputs) {
        long maxCounterValue = (long) Math.pow(hypothesis.size() * reference.size(), 2);
        boolean cont = true;
        while (cont) {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                return null;
            }
            Word<I> separator = findSeparatingWordByExploration(reference, hypothesis, alphabet, maxCounterValue);
            if (separator != null) {
                return new DefaultQuery<>(separator, reference.accepts(separator));
            }
            maxCounterValue += hypothesis.getPeriod();
            cont = random.nextBoolean();
        }
        return null;
    }
}
