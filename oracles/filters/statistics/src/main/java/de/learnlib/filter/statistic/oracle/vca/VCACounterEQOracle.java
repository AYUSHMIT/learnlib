package de.learnlib.filter.statistic.oracle.vca;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.filter.statistic.oracle.CounterEQOracle;
import net.automatalib.automata.oca.automatoncountervalues.VCAFromDescription;

public class VCACounterEQOracle<I> extends CounterEQOracle<VCAFromDescription<?, I>, I, Boolean> implements EquivalenceOracle.VCAEquivalenceOracle<I> {

    public VCACounterEQOracle(EquivalenceOracle<VCAFromDescription<?, I>, I, Boolean> equivalenceOracle,
            String name) {
        super(equivalenceOracle, name);
    }
    
}
