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
