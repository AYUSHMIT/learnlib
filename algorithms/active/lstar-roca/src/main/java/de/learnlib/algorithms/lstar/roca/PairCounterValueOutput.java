package de.learnlib.algorithms.lstar.roca;

import java.util.Objects;

/**
 * A pair (output, counter value) to be used in an
 * {@link ObservationTableWithCounterValuesROCA}.
 * 
 * Two pairs are equals iff they have exactly the same contents.
 * 
 * @param <D> Output type
 * 
 * @author Gaëtan Staquet
 */
public class PairCounterValueOutput<D> {
    private D output;
    private int counterValue;

    public PairCounterValueOutput(D output, int counterValue) {
        this.output = output;
        this.counterValue = counterValue;
    }

    public D getOutput() {
        return output;
    }

    public void setOutput(D output) {
        this.output = output;
    }

    public int getCounterValue() {
        return counterValue;
    }

    public void setCounterValue(int counterValue) {
        this.counterValue = counterValue;
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
