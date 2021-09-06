package de.learnlib.filter.cache.roca;

import de.learnlib.api.oracle.MembershipOracle;

/**
 * A cache oracle for membership queries over an ROCA.
 * 
 * @param <I> Input symbol type
 * @author Gaëtan Staquet
 */
public class ROCAHashCacheOracle<I> extends AbstractHashCacheOracle<I, Boolean>
        implements MembershipOracle.ROCAMembershipOracle<I> {
    public ROCAHashCacheOracle(MembershipOracle.ROCAMembershipOracle<I> delegate) {
        super(delegate);
    }

}
