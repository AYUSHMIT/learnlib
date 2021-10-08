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
package de.learnlib.filter.cache.roca;

import de.learnlib.api.query.Query;
import net.automatalib.words.Word;

final class ProxyQuery<I, O> extends Query<I, O> {

    private final Query<I, O> origQuery;
    private O answer;

    /**
     * Constructor.
     *
     * @param origQuery
     *         the original query to forward the answer to
     */
    ProxyQuery(Query<I, O> origQuery) {
        this.origQuery = origQuery;
    }

    @Override
    public void answer(O output) {
        origQuery.answer(output);
        this.answer = output;
    }

    @Override
    public Word<I> getPrefix() {
        return origQuery.getPrefix();
    }

    @Override
    public Word<I> getSuffix() {
        return origQuery.getSuffix();
    }

    @Override
    public String toString() {
        return origQuery.toString();
    }

    /**
     * Retrieves the answer that this oracle received.
     *
     * @return the answer that was received
     */
    public O getAnswer() {
        return answer;
    }

}
