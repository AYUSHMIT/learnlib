package de.learnlib.oracle.equivalence.roca;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.filter.statistic.oracle.CounterEQOracle;
import net.automatalib.automata.fsa.DFA;

/**
 * A {@link CounterEQOracle} for equivalence queries over a restricted automaton, up to a counter limit.
 * 
 * @author Gaëtan Staquet
 */
public class RestrictedAutomatonCounterEQOracle<I> extends CounterEQOracle<DFA<?, I>, I, Boolean> implements EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> {

    EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> oracle;

    public RestrictedAutomatonCounterEQOracle(EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> equivalenceOracle, String name) {
        super(equivalenceOracle, name);
        this.oracle = equivalenceOracle;
    }

    @Override
    protected EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> getNextOracle() {
        return oracle;
    }

    @Override
    public void setCounterLimit(int counterLimit) {
        getNextOracle().setCounterLimit(counterLimit);
    }

    @Override
    public int getCounterLimit() {
        return getNextOracle().getCounterLimit();
    }
    
}
