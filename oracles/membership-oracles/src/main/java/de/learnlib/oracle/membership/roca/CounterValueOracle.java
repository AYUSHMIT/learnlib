package de.learnlib.oracle.membership.roca;

import de.learnlib.api.oracle.SingleQueryOracle;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.words.Word;

/**
 * An oracle to query the counter value of a word, using a reference ROCA.
 * 
 * The oracle only guarantees a valid output if the provided word is in the
 * prefix of the language accepted by the ROCA.
 * 
 * @author Gaëtan Staquet
 */
public class CounterValueOracle<I> implements SingleQueryOracle.SingleQueryCounterValueOracle<I> {

    private final ROCA<?, I> roca;

    public CounterValueOracle(ROCA<?, I> roca) {
        this.roca = roca;
    }

    @Override
    public Integer answerQuery(Word<I> prefix, Word<I> suffix) {
        return roca.getCounterValue(prefix.concat(suffix));
    }

}
