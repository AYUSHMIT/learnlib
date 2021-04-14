package de.learnlib.oracle.membership.roca;

import java.security.InvalidParameterException;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.SingleQueryOracle;
import de.learnlib.filter.statistic.oracle.CounterOracle;
import net.automatalib.words.Word;

/**
 * A specific counter oracle for a {@link SingleQueryOracle.SingleQueryOracleRestrictedAutomaton}.
 * 
 * @author Gaëtan Staquet
 */
public class RestrictedAutomatonCounterOracle<I> extends CounterOracle<I, Boolean>
        implements SingleQueryOracle.SingleQueryOracleRestrictedAutomaton<I> {

    private SingleQueryOracleRestrictedAutomaton<I> nextOracle;

    public RestrictedAutomatonCounterOracle(SingleQueryOracleRestrictedAutomaton<I> nextOracle, String name) {
        super(nextOracle, name);
        this.nextOracle = nextOracle;
    }

    @Override
    public Boolean answerQuery(Word<I> prefix, Word<I> suffix) {
        getCounter().increment();
        return nextOracle.answerQuery(prefix, suffix);
    }

    @Override
    public void setCounterLimit(int counterLimit) {
        nextOracle.setCounterLimit(counterLimit);
    }

    @Override
    public int getCounterLimit() {
        return nextOracle.getCounterLimit();
    }

    @Override
    public void setNext(MembershipOracle<I, Boolean> next) {
        if (!SingleQueryOracleRestrictedAutomaton.class.isInstance(next)) {
            throw new InvalidParameterException("The oracle used for automata with counter values must implement SingleQueryOracleAutomatonWithCounterValues");
        }
        nextOracle = (SingleQueryOracleRestrictedAutomaton<I>) next;
        super.setNext(next);
    }
    
    @Override
    protected MembershipOracle<I, Boolean> getNextOracle() {
        return nextOracle;
    }
}
