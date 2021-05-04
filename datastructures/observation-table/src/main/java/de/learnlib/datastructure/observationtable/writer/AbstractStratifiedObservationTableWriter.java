package de.learnlib.datastructure.observationtable.writer;

import java.util.Objects;
import java.util.function.Function;

import org.checkerframework.checker.initialization.qual.UnknownInitialization;

import net.automatalib.words.Word;

/**
 * Abstract base class for writers for stratified observation table.
 * 
 * @param <I> Input alphabet type
 * @param <D> Output type
 * @author Gaëtan Staquet
 */
public abstract class AbstractStratifiedObservationTableWriter<I, D> implements StratifiedObservationTableWriter<I, D> {
    protected Function<? super Word<? extends I>, ? extends String> wordToString;
    protected Function<? super D, ? extends String> outputToString;

    public AbstractStratifiedObservationTableWriter() {
        this(Objects::toString, Objects::toString);
    }

    public AbstractStratifiedObservationTableWriter(Function<? super Word<? extends I>, ? extends String> wordToString,
            Function<? super D, ? extends String> outputToString) {
        this.wordToString = safeToStringFunction(wordToString);
        this.outputToString = safeToStringFunction(outputToString);
    }

    protected static <T> Function<? super T, ? extends String> safeToStringFunction(
            Function<? super T, ? extends String> toStringFunction) {
        if (toStringFunction != null) {
            return toStringFunction;
        }
        return Objects::toString;
    }

    public void setWordToString(
            @UnknownInitialization(AbstractStratifiedObservationTableWriter.class) AbstractStratifiedObservationTableWriter<I, D> this,
            Function<? super Word<? extends I>, ? extends String> wordToString) {
        this.wordToString = safeToStringFunction(wordToString);
    }

    public void setOutputToString(Function<? super D, ? extends String> outputToString) {
        this.outputToString = safeToStringFunction(outputToString);
    }

    protected String wordToString(Word<? extends I> word) {
        return wordToString.apply(word);
    }

    protected String outputToString(D output) {
        return outputToString.apply(output);
    }
}
