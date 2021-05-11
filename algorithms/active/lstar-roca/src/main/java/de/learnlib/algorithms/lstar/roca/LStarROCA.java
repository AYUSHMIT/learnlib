package de.learnlib.algorithms.lstar.roca;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import de.learnlib.api.algorithm.feature.GlobalSuffixLearner;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.OTLearner;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.onecounter.PairCounterValueOutput;
import de.learnlib.util.MQUtil;
import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.automatoncountervalues.AutomatonWithCounterValues;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValuesROCA;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValuesState;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * A learner based on L* for ROCAs.
 * 
 * More precisely, the learner learns a DFA accepting a sub-language of the
 * target language up to a certain counter limit. This sub-language is called
 * the restricted language (and a DFA accepting that language is called a
 * restricted automaton, see {@link AutomatonWithCounterValues}). On top of
 * that, the learner associates possible counter values to each row. Once the
 * restricted automaton is correctly learnt and the counter values computed, the
 * learner proceeds to construct every possible ROCA using that automaton and
 * the values.
 * 
 * Note that this means that the learner can yield multiple hypotheses by
 * learning round.
 * 
 * It is important that every provided counterexample for refinements is in the
 * target language. Otherwise, the observation table will not work properly and
 * the learning process will not behave as expected.
 * 
 * @author Gaëtan Staquet
 */
public final class LStarROCA<I>
        implements OTLearner.OTLearnerROCA<I>, GlobalSuffixLearner<ROCA<?, I>, I, Boolean>, SupportsGrowingAlphabet<I> {

    private final Alphabet<I> alphabet;

    private final MembershipOracle.RestrictedAutomatonMembershipOracle<I> membershipOracle;
    private final MembershipOracle.CounterValueOracle<I> counterValueOracle;
    private final EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> restrictedAutomatonEquivalenceOracle;

    private final ObservationTableWithCounterValuesROCA<I> table;
    private DefaultAutomatonWithCounterValuesROCA<I> hypothesis;
    private int counterLimit;

    public LStarROCA(MembershipOracle.RestrictedAutomatonMembershipOracle<I> membershipOracle,
            MembershipOracle.CounterValueOracle<I> counterValueOracle,
            EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> automatonWithCounterValuesEquivalenceOracle,
            Alphabet<I> alphabet) {
        this.membershipOracle = membershipOracle;
        this.counterValueOracle = counterValueOracle;
        this.restrictedAutomatonEquivalenceOracle = automatonWithCounterValuesEquivalenceOracle;
        this.alphabet = alphabet;
        this.table = new ObservationTableWithCounterValuesROCA<>(alphabet, counterValueOracle);
        counterLimit = 0;
        hypothesis = new DefaultAutomatonWithCounterValuesROCA<>(alphabet);
        hypothesis.addInitialState(false, 0);
    }

    @Override
    public int getCounterLimit() {
        return counterLimit;
    }

    @Override
    public List<ROCA<?, I>> getHypothesisModels() {
        if (!table.isInitialized()) {
            return Collections.singletonList(hypothesis.asAutomaton());
        }

        // @formatter:off
        List<ROCA<?, I>> goodRocas = hypothesis.toAutomata(counterLimit).stream()
            .filter(roca -> isConsistent(roca))
            .collect(Collectors.toList());
        // @formatter:on
        return goodRocas;
    }

    private boolean isConsistent(ROCA<?, I> roca) {
        // We want the ROCA to be correct with regards to the information stored in the
        // table.
        // That is, the ROCA and the table must agree on the acceptance of the words.
        for (Row<I> row : table.getAllRows()) {
            Word<I> prefix = row.getLabel();
            List<PairCounterValueOutput<Boolean>> rowContent = table.fullRowContents(row);
            for (int i = 0; i < table.numberOfSuffixes(); i++) {
                Word<I> word = prefix.concat(table.getSuffix(i));
                if (roca.accepts(word) != rowContent.get(i).getOutput()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void startLearning() {
        // During the first round, we only want to test if the language is empty
        // Since the hypothesis is initialized as an ROCA accepting nothing, we do not have to do anything
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Boolean> ceQuery) {
        // 1. We compute the new counter limit
        List<DefaultQuery<I, Integer>> counterValueQueries = new ArrayList<>(ceQuery.getInput().length());
        for (Word<I> prefix : ceQuery.getInput().prefixes(false)) {
            DefaultQuery<I, Integer> query = new DefaultQuery<>(prefix);
            counterValueQueries.add(query);
        }
        counterValueOracle.processQueries(counterValueQueries);
        // @formatter:off
        counterLimit = counterValueQueries.stream()
            .map(query -> query.getOutput())
            .max(Integer::compare)
            .get();
        // @formatter:on

        // 2. We increase the counter limit in the oracles
        membershipOracle.setCounterLimit(counterLimit);
        restrictedAutomatonEquivalenceOracle.setCounterLimit(counterLimit);

        // 3. If it's the first time we refine the hypothesis, we initialize the table
        // using the counter-example.
        // If it is not the firs time, we refine the table
        List<List<Row<I>>> unclosed;
        if (!table.isInitialized()) {
            final List<Word<I>> initialPrefixes = ceQuery.getInput().prefixes(false);
            final List<Word<I>> initialSuffixes = initialSuffixes();

            unclosed = table.initialize(initialPrefixes, initialSuffixes, membershipOracle);
            completeConsistentTable(unclosed, table.isInitialConsistencyCheckRequired());
        } else if (!MQUtil.isCounterexample(ceQuery, hypothesis)) {
            return false;
        } else {
            Word<I> counterexample = ceQuery.getInput();
            unclosed = table.increaseCounterLimit(counterLimit, counterexample.prefixes(false), Collections.emptyList(),
                    membershipOracle);
            completeConsistentTable(unclosed, true);
        }

        // 4. We learn the DFA up to the counter value
        learnDFA();

        return true;
    }

    /**
     * Learns the DFA for the restricted language up to the counter limit.
     */
    private void learnDFA() {
        while (true) {
            updateHypothesis();
            DefaultQuery<I, Boolean> ce = restrictedAutomatonEquivalenceOracle.findCounterExample(hypothesis, alphabet);

            if (ce == null) {
                return;
            }

            int oldDistinctRows = table.numberOfDistinctRows();
            int oldSuffixes = table.numberOfSuffixes();

            // Since we want that for all cells in the table with label u, u is in the
            // prefix of the target language, we can not add the prefixes of the
            // counterexample as representatives.
            // Indeed, we can not be sure that the provided counterexample should be
            // accepted.
            // Therefore, we instead add the suffixes as separators.
            List<Word<I>> suffixes = ce.getInput().suffixes(false);
            List<List<Row<I>>> unclosed = table.addSuffixes(suffixes, membershipOracle);

            completeConsistentTable(unclosed, true);

            assert table.numberOfDistinctRows() > oldDistinctRows || table.numberOfSuffixes() > oldSuffixes
                    : "Nothing was learnt during the last iteration for DFA";
        }
    }

    @Override
    public ObservationTableWithCounterValuesROCA<I> getObservationTable() {
        return table;
    }

    private List<Word<I>> initialSuffixes() {
        return Collections.singletonList(Word.epsilon());
    }

    private boolean completeConsistentTable(List<List<Row<I>>> unclosed, boolean checkConsistency) {
        boolean refined = false;
        List<List<Row<I>>> unclosedIter = unclosed;
        do {
            while (!unclosedIter.isEmpty()) {
                List<Row<I>> closingRows = selectClosingRows(unclosedIter);
                unclosedIter = table.toShortPrefixes(closingRows, membershipOracle);
                refined = true;
            }

            if (checkConsistency) {
                Inconsistency<I> inconsistency;

                do {
                    inconsistency = table.findInconsistency();
                    if (inconsistency != null) {
                        Word<I> newSuffix = analyzeInconsistency(inconsistency);
                        unclosedIter = table.addSuffix(newSuffix, membershipOracle);
                    }
                } while (unclosedIter.isEmpty() && inconsistency != null);
            }
        } while (!unclosedIter.isEmpty());

        return refined;
    }

    private List<Row<I>> selectClosingRows(List<List<Row<I>>> unclosed) {
        List<Row<I>> closingRows = new ArrayList<>(unclosed.size());

        for (List<Row<I>> rowList : unclosed) {
            if (rowList.size() != 0) {
                closingRows.add(rowList.get(0));
            }
        }

        return closingRows;
    }

    private Word<I> analyzeInconsistency(Inconsistency<I> inconsistency) {
        int inputIdx = alphabet.getSymbolIndex(inconsistency.getSymbol());

        Row<I> successorRow1 = inconsistency.getFirstRow().getSuccessor(inputIdx);
        Row<I> successorRow2 = inconsistency.getSecondRow().getSuccessor(inputIdx);

        int numSuffixes = table.getSuffixes().size();

        for (int i = 0; i < numSuffixes; i++) {
            PairCounterValueOutput<Boolean> val1 = table.fullCellContents(successorRow1, i);
            PairCounterValueOutput<Boolean> val2 = table.fullCellContents(successorRow2, i);
            if (!Objects.equals(val1, val2)) {
                I sym = alphabet.getSymbol(inputIdx);
                Word<I> suffix = table.getSuffixes().get(i);
                if (!table.getSuffixes().contains(suffix.prepend(sym))) {
                    return suffix.prepend(sym);
                }
            }
        }

        throw new IllegalArgumentException("Bogus inconsistency");
    }

    @Override
    public Collection<Word<I>> getGlobalSuffixes() {
        return Collections.unmodifiableCollection(table.getSuffixes());
    }

    @Override
    public boolean addGlobalSuffixes(Collection<? extends Word<I>> globalSuffixes) {
        List<List<Row<I>>> unclosed = table.addSuffixes(globalSuffixes, membershipOracle);
        if (unclosed.isEmpty()) {
            return false;
        }
        return completeConsistentTable(unclosed, false);
    }

    @Override
    public void addAlphabetSymbol(I symbol) {
        if (!this.alphabet.containsSymbol(symbol)) {
            Alphabets.toGrowingAlphabetOrThrowException(this.alphabet).addSymbol(symbol);
        }

        final List<List<Row<I>>> unclosed = this.table.addAlphabetSymbol(symbol, membershipOracle);
        completeConsistentTable(unclosed, true);
    }

    @Override
    public ROCA<?, I> getLearntDFAAsROCA() {
        return hypothesis.asAutomaton();
    }

    private void updateHypothesis() {
        assert table.isInitialized();

        Map<Integer, StateInfo<DefaultAutomatonWithCounterValuesState, I>> stateInfos = new HashMap<>();
        hypothesis = new DefaultAutomatonWithCounterValuesROCA<>(alphabet);

        for (Row<I> sp : table.getCanonicalRows()) {
            int id = sp.getRowContentId();
            StateInfo<DefaultAutomatonWithCounterValuesState, I> info = stateInfos.getOrDefault(id, null);
            PairCounterValueOutput<Boolean> cell = table.fullCellContents(sp, 0);
            boolean acceptance = cell.getOutput();
            int counterValue = cell.getCounterValue();

            if (info != null) {
                if (info.getRow() == sp) {
                    hypothesis.setStateAcceptance(info.getState(), acceptance);
                }
            } else {
                DefaultAutomatonWithCounterValuesState state;
                if (sp.getLabel().equals(Word.epsilon())) {
                    state = hypothesis.addInitialState(acceptance, 0);
                } else {
                    state = hypothesis.addState(acceptance, counterValue);
                }

                stateInfos.put(id, new StateInfo<>(sp, state));
            }
        }

        for (StateInfo<DefaultAutomatonWithCounterValuesState, I> info : stateInfos.values()) {
            Row<I> sp = info.getRow();
            DefaultAutomatonWithCounterValuesState state = info.getState();

            for (int i = 0; i < alphabet.size(); i++) {
                I input = alphabet.getSymbol(i);
                Row<I> successor = sp.getSuccessor(i);
                if (successor != null) {
                    Row<I> canSuccessor = table.getCanonicalRow(successor);
                    if (canSuccessor != null) {
                        int successorId = canSuccessor.getRowContentId();
                        DefaultAutomatonWithCounterValuesState successorState = stateInfos.get(successorId).getState();
                        hypothesis.setTransition(state, input, successorState);
                    }
                }
            }
        }
    }

    private static class StateInfo<S, I> {
        private final Row<I> row;
        private final S state;

        StateInfo(Row<I> row, S state) {
            this.row = row;
            this.state = state;
        }

        public Row<I> getRow() {
            return row;
        }

        public S getState() {
            return state;
        }
    }
}
