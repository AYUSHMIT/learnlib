package de.learnlib.oracle.membership.roca;

import java.security.InvalidParameterException;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.SingleQueryOracle;
import de.learnlib.filter.statistic.oracle.CounterOracle;
import net.automatalib.automata.oca.automatoncountervalues.AcceptingOrExit;
import net.automatalib.words.Word;

/**
 * A specific counter oracle for a {@link SingleQueryOracle.SingleQueryOracleAutomatonWithCounterValues}.
 * 
 * @author Gaëtan Staquet
 */
public class AutomatonCounterValuesCounterOracle<I> extends CounterOracle<I, AcceptingOrExit>
        implements SingleQueryOracle.SingleQueryOracleAutomatonWithCounterValues<I> {

    private SingleQueryOracleAutomatonWithCounterValues<I> nextOracle;

    public AutomatonCounterValuesCounterOracle(SingleQueryOracleAutomatonWithCounterValues<I> nextOracle, String name) {
        super(nextOracle, name);
        this.nextOracle = nextOracle;
    }

    @Override
    public AcceptingOrExit answerQuery(Word<I> prefix, Word<I> suffix) {
        return nextOracle.answerQuery(prefix, suffix);
    }

    @Override
    public void incrementCounterLimit() {
        nextOracle.incrementCounterLimit();
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
    public void setNext(MembershipOracle<I, AcceptingOrExit> next) {
        if (!SingleQueryOracleAutomatonWithCounterValues.class.isInstance(next)) {
            throw new InvalidParameterException("The oracle used for automata with counter values must implement SingleQueryOracleAutomatonWithCounterValues");
        }
        nextOracle = (SingleQueryOracleAutomatonWithCounterValues<I>) next;
        super.setNext(next);
    }
    
    @Override
    protected MembershipOracle<I, AcceptingOrExit> getNextOracle() {
        return nextOracle;
    }
}
