package de.learnlib.filter.statistic.oracle.roca;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.filter.statistic.oracle.CounterEQOracle;
import net.automatalib.automata.oca.automatoncountervalues.ROCAFromDescription;

public class ROCACounterEQOracle<I> extends CounterEQOracle<ROCAFromDescription<?, I>, I, Boolean> implements EquivalenceOracle.ROCAEquivalenceOracle<I> {

    public ROCACounterEQOracle(EquivalenceOracle<ROCAFromDescription<?, I>, I, Boolean> equivalenceOracle,
            String name) {
        super(equivalenceOracle, name);
    }
    
}
