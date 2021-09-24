package de.learnlib.algorithms.lstar.vca;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.learnlib.api.algorithm.feature.GlobalSuffixLearner;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.OTLearner;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.oca.VCA;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValuesState;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValuesVCA;
import net.automatalib.util.automata.oca.OCAUtil;
import net.automatalib.words.VPDAlphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * L* learner for VCAs.
 * 
 * The implementation is based p, the unpublished paper "Learning Visibly
 * One-Counter Automata" by D. Neider and C. Löding (2010).
 * 
 * The learner works over a pushdown alphabet, which means that the counter
 * value of a word is solely dictated by the alphabet. In other words, the
 * learner can compute the counter value of a given word without having to ask a
 * query.
 * 
 * The learner uses a stratified table to store the observations. That is, there
 * is one table by counter value between zero and the counter limit. Each table
 * has its own sets of prefixes and suffixes such that the. See
 * {@link StratifiedObservationTableWithCounterValues} for more information.
 * 
 * @param <I> Input parameter type
 * @author Gaëtan Staquet
 */
public class LStarVCA<I>
        implements OTLearner.OTLearnerVCA<I>, GlobalSuffixLearner<VCA<?, I>, I, Boolean>, SupportsGrowingAlphabet<I> {

    private final VPDAlphabet<I> alphabet;

    private final MembershipOracle.ROCAMembershipOracle<I> membershipOracle;
    private final EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> restrictedAutomatonEquivalenceOracle;

    private final StratifiedObservationTableWithCounterValues<I, Boolean> table;

    private DefaultAutomatonWithCounterValuesVCA<I> hypothesis;
    private int counterLimit;

    public LStarVCA(MembershipOracle.ROCAMembershipOracle<I> membershipOracle,
            EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> restrictedAutomatonEquivalenceOracle,
            VPDAlphabet<I> alphabet) {
        this.membershipOracle = membershipOracle;
        this.restrictedAutomatonEquivalenceOracle = restrictedAutomatonEquivalenceOracle;
        this.alphabet = alphabet;
        counterLimit = 0;
        this.table = new StratifiedObservationTableWithCounterValues<>(alphabet);
        this.hypothesis = new DefaultAutomatonWithCounterValuesVCA<>(alphabet);
    }

    @Override
    public int getCounterLimit() {
        return counterLimit;
    }

    @Override
    public VCA<?, I> getlearnedDFAAsVCA() {
        return hypothesis.asAutomaton();
    }

    @Override
    public Collection<VCA<?, I>> getHypothesisModels() {
        return hypothesis.toAutomata(counterLimit);
    }

    @Override
    public void startLearning() {
        final List<List<Word<I>>> initialPrefixes = initialPrefixes();
        final List<List<Word<I>>> initialSuffixes = initialSuffixes();

        List<List<Row<I>>> unclosed = table.initialize(initialPrefixes, initialSuffixes, membershipOracle);
        completeConsistentTable(unclosed, table.isInitialConsistencyCheckRequired());

        learnDFA();
    }

    private List<List<Word<I>>> initialPrefixes() {
        return Collections.singletonList(Collections.singletonList(Word.epsilon()));
    }

    private List<List<Word<I>>> initialSuffixes() {
        return Collections.singletonList(Collections.singletonList(Word.epsilon()));
    }

    private void processCounterExample(Word<I> counterexample, List<List<? extends Word<I>>> prefixes,
            List<List<? extends Word<I>>> suffixes) {
        Map<Integer, List<Word<I>>> prefixesByCounterValue = new HashMap<>();
        Map<Integer, List<Word<I>>> suffixesByCounterValue = new HashMap<>();

        List<Word<I>> prefixesList = counterexample.prefixes(false);
        List<Word<I>> suffixesList = counterexample.suffixes(true);
        for (int i = 0; i < prefixesList.size(); i++) {
            Word<I> prefix = prefixesList.get(i);
            Word<I> suffix = suffixesList.get(i);

            int counterValue = OCAUtil.computeCounterValue(prefix, alphabet);
            if (!prefixesByCounterValue.containsKey(counterValue)) {
                prefixesByCounterValue.put(counterValue, new ArrayList<>());
                suffixesByCounterValue.put(counterValue, new ArrayList<>());
            }

            prefixesByCounterValue.get(counterValue).add(prefix);
            suffixesByCounterValue.get(counterValue).add(suffix);
        }

        prefixes.addAll(prefixesByCounterValue.values());
        suffixes.addAll(suffixesByCounterValue.values());
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Boolean> ceQuery) {

        // 1. We compute the new counter limit
        counterLimit = OCAUtil.computeMaximalCounterValue(ceQuery.getInput(), alphabet);

        // 2. We increase the counter limit in the oracles
        restrictedAutomatonEquivalenceOracle.setCounterLimit(counterLimit);

        // 3. We refine the table
        List<List<? extends Word<I>>> prefixes = new ArrayList<>();
        List<List<? extends Word<I>>> suffixes = new ArrayList<>();
        processCounterExample(ceQuery.getInput(), prefixes, suffixes);
        List<List<Row<I>>> unclosed = table.increaseCounterLimit(counterLimit, prefixes, suffixes, membershipOracle);
        completeConsistentTable(unclosed, true);

        // 4. We learn the DFA up to the counter value
        learnDFA();

        return true;
    }

    private void updateHypothesis() {
        if (!table.isInitialized()) {
            throw new IllegalStateException("Cannot update internal hypothesis: not initialized");
        }

        List<Map<Integer, StateInfo<DefaultAutomatonWithCounterValuesState, I>>> stateInfosByCounterValue = new ArrayList<>();
        hypothesis = new DefaultAutomatonWithCounterValuesVCA<>(alphabet);

        for (int i = 0; i <= counterLimit; i++) {
            stateInfosByCounterValue.add(new HashMap<>());
        }

        for (int counterValue = 0; counterValue <= counterLimit; counterValue++) {
            Map<Integer, StateInfo<DefaultAutomatonWithCounterValuesState, I>> stateInfos = stateInfosByCounterValue
                    .get(counterValue);

            for (Row<I> sp : table.getShortPrefixRows(counterValue)) {
                int id = sp.getRowContentId();
                StateInfo<DefaultAutomatonWithCounterValuesState, I> info = stateInfos.getOrDefault(id, null);
                boolean acceptance = false;
                if (counterValue == 0 && table.cellContents(sp, 0)) {
                    acceptance = true;
                }
                if (info != null) {
                    if (info.getRow() == sp) {
                        hypothesis.setStateAcceptance(info.getState(), acceptance);
                    }
                } else {
                    DefaultAutomatonWithCounterValuesState state;
                    if (sp.getLabel().equals(Word.epsilon())) {
                        state = hypothesis.addInitialState(acceptance, counterValue);
                    } else {
                        state = hypothesis.addState(acceptance, counterValue);
                    }

                    stateInfos.put(id, new StateInfo<>(sp, state));
                }
            }

        }

        for (int counterValue = 0; counterValue <= counterLimit; counterValue++) {
            Map<Integer, StateInfo<DefaultAutomatonWithCounterValuesState, I>> stateInfos = stateInfosByCounterValue
                    .get(counterValue);
            for (StateInfo<DefaultAutomatonWithCounterValuesState, I> info : stateInfos.values()) {
                Row<I> sp = info.getRow();
                assert counterValue == table.getCounterValueOfRow(sp);
                DefaultAutomatonWithCounterValuesState state = info.getState();
                assert state != null;

                for (int j = 0; j < alphabet.size(); j++) {
                    I input = alphabet.getSymbol(j);
                    Row<I> successor = sp.getSuccessor(j);
                    if (successor != null) {
                        int successorId = successor.getRowContentId();
                        int successorCv = table.getCounterValueOfRow(successor);
                        assert successorCv == (counterValue + OCAUtil.counterOperation(input, alphabet));
                        DefaultAutomatonWithCounterValuesState successorState = stateInfosByCounterValue
                                .get(successorCv).get(successorId).getState();
                        hypothesis.setTransition(state, input, successorState);
                    }
                }
            }
        }
    }

    private void learnDFA() {
        while (true) {
            updateHypothesis();
            DefaultQuery<I, Boolean> ce = restrictedAutomatonEquivalenceOracle.findCounterExample(hypothesis, alphabet);

            if (ce == null) {
                return;
            }

            int oldDistinctRows = table.numberOfDistinctRows();

            List<List<Row<I>>> unclosed = table.addShortPrefixes(ce.getInput().prefixes(false), membershipOracle);
            completeConsistentTable(unclosed, true);
            assert table.numberOfDistinctRows() > oldDistinctRows
                    : "Nothing was learned during the last iteration for DFA";
        }
    }

    @Override
    public StratifiedObservationTableWithCounterValues<I, Boolean> getObservationTable() {
        return table;
    }

    @Override
    public Collection<Word<I>> getGlobalSuffixes() {
        return Collections.unmodifiableList(table.getSuffixes());
    }

    /**
     * This function should not be called. See the other addGlobalSuffixes.
     */
    @Override
    public boolean addGlobalSuffixes(Collection<? extends Word<I>> globalSuffixes) {
        throw new UnsupportedOperationException(
                "To add global suffixes in L* for VCA, counter values must be provided. See the other addGlobalSuffixes method");
    }

    /**
     * Adds global suffixes in the learner.
     * 
     * The suffixes must be grouped by the counter value of the table in which they
     * must be added.
     * 
     * @param globalSuffixes Global suffixes
     * @return Whether new distinct rows were identified
     */
    public boolean addGlobalSuffixes(Map<Integer, List<? extends Word<I>>> globalSuffixes) {
        List<List<Row<I>>> unclosed = table.addSuffixes(globalSuffixes, membershipOracle);
        if (unclosed.isEmpty()) {
            return false;
        }
        return completeConsistentTable(unclosed, false);
    }

    @Override
    public void addAlphabetSymbol(I symbol) {
        if (!alphabet.containsSymbol(symbol)) {
            Alphabets.toGrowingAlphabetOrThrowException(alphabet).addSymbol(symbol);
        }

        final List<List<Row<I>>> unclosed = table.addAlphabetSymbol(symbol, membershipOracle);
        completeConsistentTable(unclosed, true);
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
                        int counterValue = OCAUtil.computeCounterValue(inconsistency.getFirstRow().getLabel(),
                                alphabet);
                        Word<I> newSuffix = analyzeInconsistency(inconsistency, counterValue);
                        unclosedIter = table.addSuffix(newSuffix, counterValue, membershipOracle);
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

    private Word<I> analyzeInconsistency(Inconsistency<I> inconsistency, int counterValueOfFirstRow) {
        int inputIdx = alphabet.getSymbolIndex(inconsistency.getSymbol());
        I sym = alphabet.getSymbol(inputIdx);
        int counterValue = counterValueOfFirstRow + OCAUtil.counterOperation(sym, alphabet);

        Row<I> successorRow1 = inconsistency.getFirstRow().getSuccessor(inputIdx);
        Row<I> successorRow2 = inconsistency.getSecondRow().getSuccessor(inputIdx);

        List<Word<I>> suffixes = table.getSuffixes(counterValue);

        for (int i = 0; i < suffixes.size(); i++) {
            boolean val1 = table.cellContents(successorRow1, i);
            boolean val2 = table.cellContents(successorRow2, i);
            if (val1 != val2) {
                Word<I> suffix = suffixes.get(i);
                if (!suffixes.contains(suffix.prepend(sym))) {
                    return suffix.prepend(sym);
                }
            }
        }

        throw new IllegalArgumentException("Bogus inconsistency");
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
