/* Copyright (C) 2021 – University of Mons, University Antwerpen
 * This file is part of LearnLib, http://www.learnlib.de/..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
