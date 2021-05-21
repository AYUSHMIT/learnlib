package de.learnlib.filter.cache.roca;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.Query;
import net.automatalib.words.Word;

/**
 * A cache oracle for counter value queries.
 * 
 * @param <I> Input symbol type
 * @author Gaëtan Staquet
 */
public class CounterValueHashCacheOracle<I> implements MembershipOracle.CounterValueOracle<I> {

    private final MembershipOracle<I, Integer> delegate;
    private final Map<Word<I>, Integer> cache;
    private final Lock cacheLock;

    public CounterValueHashCacheOracle(MembershipOracle<I, Integer> delegate) {
        this.delegate = delegate;
        this.cache = new HashMap<>();
        this.cacheLock = new ReentrantLock();
    }

    public void processQueries(Collection<? extends Query<I, Integer>> queries) {
        List<ProxyQuery<I>> misses = new ArrayList<>();

        cacheLock.lock();
        try {
            for (Query<I, Integer> query : queries) {
                Word<I> input = query.getInput();
                Integer answer = cache.get(input);
                if (answer != null) {
                    query.answer(answer);
                }
                else {
                    misses.add(new ProxyQuery<>(query));
                }
            }
        }
        finally {
            cacheLock.unlock();
        }

        delegate.processQueries(misses);

        cacheLock.lock();
        try {
            for (ProxyQuery<I> miss : misses) {
                cache.put(miss.getInput(), miss.getAnswer());
            }
        }
        finally {
            cacheLock.unlock();
        }
    }
}
