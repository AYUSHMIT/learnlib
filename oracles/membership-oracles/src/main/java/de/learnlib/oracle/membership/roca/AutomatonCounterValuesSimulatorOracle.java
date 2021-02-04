package de.learnlib.oracle.membership.roca;

import de.learnlib.api.oracle.SingleQueryOracle;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.automatoncountervalues.AcceptingOrExit;
import net.automatalib.automata.oca.automatoncountervalues.AutomatonWithCounterValues;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.Word;

/**
 * A membership oracle for an {@link AutomatonWithCounterValues}.
 * 
 * Since such an automaton is constructed from a ROCA for a given maximal
 * counter value, that counter value limit must be explicitly increased by the
 * experiment after each round.
 * 
 * @author Gaëtan Staquet
 */
public final class AutomatonCounterValuesSimulatorOracle<I> implements SingleQueryOracle.SingleQueryOracleAutomatonWithCounterValues<I> {

    private final ROCA<?, I> roca;
    private AutomatonWithCounterValues<?, I> reference;
    private int counterLimit = 0;

    public AutomatonCounterValuesSimulatorOracle(final ROCA<?, I> roca) {
        this.roca = roca;
        reference = OCAUtil.constructRestrictedAutomaton(roca, 0);
    }

    @Override
    public void incrementCounterLimit() {
        setCounterLimit(counterLimit + 1);
    }

    @Override
    public void setCounterLimit(int counterLimit) {
        this.counterLimit = counterLimit;
        reference = OCAUtil.constructRestrictedAutomaton(roca, counterLimit);
    }

    @Override
    public int getCounterLimit() {
        return counterLimit;
    }

    @Override
    public AcceptingOrExit answerQuery(Word<I> prefix, Word<I> suffix) {
        return reference.computeSuffixOutput(prefix, suffix);
    }
    
}
