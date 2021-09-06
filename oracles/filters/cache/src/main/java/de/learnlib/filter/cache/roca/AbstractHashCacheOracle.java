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
 * Abstract cache oracle.
 * 
 * @param <I> Input alphabet type
 * @param <O> Output type
 * @author Gaëtan Staquet
 */
abstract class AbstractHashCacheOracle<I, O> implements MembershipOracle<I, O> {
    private final MembershipOracle<I, O> delegate;
    private final Map<Word<I>, O> cache = new HashMap<>();
    private final Lock cacheLock = new ReentrantLock();

    AbstractHashCacheOracle(MembershipOracle<I, O> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void processQueries(Collection<? extends Query<I, O>> queries) {
        List<ProxyQuery<I, O>> misses = new ArrayList<>();

        cacheLock.lock();
        try {
            for (Query<I, O> query : queries) {
                Word<I> input = query.getInput();
                O answer = cache.get(input);
                if (answer != null) {
                    query.answer(answer);
                } else {
                    misses.add(new ProxyQuery<>(query));
                }
            }
        } finally {
            cacheLock.unlock();
        }

        delegate.processQueries(misses);

        cacheLock.lock();
        try {
            for (ProxyQuery<I, O> miss : misses) {
                cache.put(miss.getInput(), miss.getAnswer());
            }
        } finally {
            cacheLock.unlock();
        }
    }
}
