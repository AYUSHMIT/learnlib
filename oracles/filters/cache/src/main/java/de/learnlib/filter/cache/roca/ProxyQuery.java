package de.learnlib.filter.cache.roca;

import de.learnlib.api.query.Query;
import net.automatalib.words.Word;

final class ProxyQuery<I> extends Query<I, Integer> {

    private final Query<I, Integer> origQuery;
    private Integer answer;

    /**
     * Constructor.
     *
     * @param origQuery
     *         the original query to forward the answer to
     */
    ProxyQuery(Query<I, Integer> origQuery) {
        this.origQuery = origQuery;
    }

    @Override
    public void answer(Integer output) {
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
    public Integer getAnswer() {
        return answer;
    }

}
