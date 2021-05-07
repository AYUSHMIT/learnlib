package de.learnlib.datastructure.observationtable.onecounter;

import java.util.Objects;

/**
 * A pair (output, counter value) to be used in an
 * {@link AbstractObservationTableWithCounterValues}.
 * 
 * Two pairs are equals iff they have exactly the same contents.
 * 
 * @param <D> Output type
 * 
 * @author Gaëtan Staquet
 */
public class PairCounterValueOutput<D> {
    private final D output;
    private final int counterValue;

    public PairCounterValueOutput(D output, int counterValue) {
        this.output = output;
        this.counterValue = counterValue;
    }

    public D getOutput() {
        return output;
    }

    public int getCounterValue() {
        return counterValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(output, counterValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }

        PairCounterValueOutput<?> o = (PairCounterValueOutput<?>) obj;
        return Objects.equals(o.getOutput(), getOutput()) && Objects.equals(o.getCounterValue(), getCounterValue());
    }

    @Override
    public String toString() {
        return "(" + output + ", " + counterValue + ")";
    }
}
