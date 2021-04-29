package de.learnlib.algorithms.lstar.vca;

import net.automatalib.words.VPDAlphabet;
import net.automatalib.words.Word;

/**
 * A class to ease the computation of the (maximal) counter value of a word over
 * a pushdown alphabet
 * 
 * @author Gaëtan Staquet
 */
class VCALearningUtils {
    /**
     * Special value indicating that a word has a prefix such that the number of
     * returns is strictly greater than the number of calls.
     */
    public static final int INVALID_WORD_COUNTER_VALUE = -2;

    /**
     * Computes the counter value obtained after reading the whole word.
     * 
     * @param <I>      Input alphabet type
     * @param word     The word
     * @param alphabet The pushdown alphabet
     * @return The counter value of the word
     */
    public static <I> int computeCounterValue(Word<I> word, VPDAlphabet<I> alphabet) {
        int counterValue = 0;
        for (I symbol : word) {
            switch (alphabet.getSymbolType(symbol)) {
            case CALL:
                counterValue++;
                break;
            case RETURN:
                if (counterValue == 0) {
                    return INVALID_WORD_COUNTER_VALUE;
                }
                counterValue--;
                break;
            case INTERNAL:
                break;
            default:
                break;
            }
        }
        return counterValue;
    }

    /**
     * Compute the maximal counter value seen while reading a word.
     * 
     * This value is also called the height of the word.
     * 
     * @param <I>      Input alphabet type
     * @param word     The word
     * @param alphabet The pushdown alpĥabet
     * @return The maximal counter value seen
     */
    public static <I> int computeMaximalCounterValue(Word<I> word, VPDAlphabet<I> alphabet) {
        int counterValue = 0;
        int maxCounterValue = 0;
        for (I symbol : word) {
            switch (alphabet.getSymbolType(symbol)) {
            case CALL:
                counterValue++;
                break;
            case RETURN:
                if (counterValue == 0) {
                    return INVALID_WORD_COUNTER_VALUE;
                }
                counterValue--;
                break;
            case INTERNAL:
                break;
            default:
                break;
            }

            maxCounterValue = Math.max(counterValue, maxCounterValue);
        }
        return maxCounterValue;
    }
}
