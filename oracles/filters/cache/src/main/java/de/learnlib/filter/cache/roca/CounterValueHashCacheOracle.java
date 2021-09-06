package de.learnlib.filter.cache.roca;

import de.learnlib.api.oracle.MembershipOracle;

/**
 * A cache oracle for counter value queries.
 * 
 * @param <I> Input symbol type
 * @author Gaëtan Staquet
 */
public class CounterValueHashCacheOracle<I> extends AbstractHashCacheOracle<I, Integer>
        implements MembershipOracle.CounterValueOracle<I> {

    public CounterValueHashCacheOracle(MembershipOracle.CounterValueOracle<I> delegate) {
        super(delegate);
    }
}
