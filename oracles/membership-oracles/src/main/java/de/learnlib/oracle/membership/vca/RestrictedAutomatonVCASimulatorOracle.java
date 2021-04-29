package de.learnlib.oracle.membership.vca;

import de.learnlib.api.oracle.SingleQueryOracle;
import net.automatalib.automata.oca.VCA;
import net.automatalib.automata.oca.automatoncountervalues.AutomatonWithCounterValues;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Word;

public class RestrictedAutomatonVCASimulatorOracle<I> implements SingleQueryOracle.SingleQueryOracleRestrictedAutomaton<I> {

    private final VCA<?, I> vca;
    private AutomatonWithCounterValues<?, I, VCA<?, I>> reference;

    public RestrictedAutomatonVCASimulatorOracle(VCA<?, I> vca) {
        this(vca, 0);
    }

    public RestrictedAutomatonVCASimulatorOracle(VCA<?, I> vca, int counterLimit) {
        this.vca = vca;
        setCounterLimit(counterLimit);
    }

    @Override
    public Boolean answerQuery(Word<I> prefix, Word<I> suffix) {
        return reference.accepts(prefix.concat(suffix));
    }

    @Override
    public void setCounterLimit(int counterLimit) {
        reference = OCAUtil.constructRestrictedAutomaton(vca, counterLimit);
    }
    
}
