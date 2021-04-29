package de.learnlib.oracle.membership.roca;

import de.learnlib.api.oracle.SingleQueryOracle;
import net.automatalib.automata.oca.ROCA;
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
public final class RestrictedAutomatonROCASimulatorOracle<I>
        implements SingleQueryOracle.SingleQueryOracleRestrictedAutomaton<I> {

    private final ROCA<?, I> roca;
    private AutomatonWithCounterValues<?, I, ROCA<?, I>> reference;

    public RestrictedAutomatonROCASimulatorOracle(final ROCA<?, I> roca) {
        this(roca, 0);
    }

    public RestrictedAutomatonROCASimulatorOracle(final ROCA<?, I> roca, int counterLimit) {
        this.roca = roca;
        setCounterLimit(counterLimit);
    }

    @Override
    public void setCounterLimit(int counterLimit) {
        reference = OCAUtil.constructRestrictedAutomaton(roca, counterLimit);
    }

    @Override
    public Boolean answerQuery(Word<I> prefix, Word<I> suffix) {
        return reference.computeSuffixOutput(prefix, suffix);
    }

}
