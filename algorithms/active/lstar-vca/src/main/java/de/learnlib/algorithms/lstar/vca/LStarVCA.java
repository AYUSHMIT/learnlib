package de.learnlib.algorithms.lstar.vca;

import static de.learnlib.algorithms.lstar.vca.VCALearningUtils.computeMaximalCounterValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.api.algorithm.feature.GlobalSuffixLearner;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.AbstractObservationTableWithCounterValues.OutputAndCounterValue;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.OTLearner;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.util.MQUtil;
import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.oca.VCA;
import net.automatalib.automata.oca.automatoncountervalues.AutomatonWithCounterValues;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValuesState;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValuesVCA;
import net.automatalib.words.VPDAlphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * L* learner for VCAs.
 * 
 * The implementation is inspired by the unpublished paper "Learning Visibly
 * One-Counter Automata" by D. Neider and C. Löding (2010). Note that this is
 * not a strict implementation as the paper uses a stratified observation table
 * to separate the short prefixes by counter value.
 * 
 * @param <I> Input parameter type
 * @author Gaëtan Staquet
 */
public class LStarVCA<I>
        implements OTLearner.OTLearnerVCA<I>, GlobalSuffixLearner<VCA<?, I>, I, Boolean>, SupportsGrowingAlphabet<I> {

    private final VPDAlphabet<I> alphabet;

    private final MembershipOracle.RestrictedAutomatonMembershipOracle<I> membershipOracle;
    private final EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> restrictedAutomatonEquivalenceOracle;

    private final ObservationTableWithCounterValuesVCA<I> table;

    private AutomatonWithCounterValues<?, I, VCA<?, I>> hypothesis;
    private int counterLimit;

    public LStarVCA(MembershipOracle.RestrictedAutomatonMembershipOracle<I> membershipOracle,
            EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> restrictedAutomatonEquivalenceOracle,
            VPDAlphabet<I> alphabet) {
        this.membershipOracle = membershipOracle;
        this.restrictedAutomatonEquivalenceOracle = restrictedAutomatonEquivalenceOracle;
        this.alphabet = alphabet;
        counterLimit = 0;
        this.table = new ObservationTableWithCounterValuesVCA<>(alphabet);
    }

    @Override
    public int getCounterLimit() {
        return counterLimit;
    }

    @Override
    public VCA<?, I> getLearntDFAAsVCA() {
        return hypothesis.asAutomaton();
    }

    @Override
    public Collection<VCA<?, I>> getHypothesisModels() {
        // @formatter:off
        List<VCA<?, I>> goodRocas = hypothesis.toAutomata(counterLimit).stream()
            .filter(vca -> isConsistent(vca))
            .collect(Collectors.toList());
        // @formatter:on
        return goodRocas;
    }

    private boolean isConsistent(VCA<?, I> vca) {
        // We want the ROCA to be correct with regards to the information stored in the
        // table.
        // That is, the ROCA and the table must agree on the acceptance of the words.
        for (Row<I> row : table.getAllRows()) {
            Word<I> prefix = row.getLabel();
            List<OutputAndCounterValue<Boolean>> rowContent = table.fullRowContents(row);
            for (int i = 0; i < table.numberOfSuffixes(); i++) {
                Word<I> word = prefix.concat(table.getSuffix(i));
                if (vca.accepts(word) != rowContent.get(i).getOutput()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void startLearning() {
        final List<Word<I>> initialPrefixes = initialPrefixes();
        final List<Word<I>> initialSuffixes = initialSuffixes();

        List<List<Row<I>>> unclosed = table.initialize(initialPrefixes, initialSuffixes, membershipOracle);
        completeConsistentTable(unclosed, table.isInitialConsistencyCheckRequired());

        learnDFA();
        updateHypothesis();
    }

    private List<Word<I>> initialPrefixes() {
        return Collections.singletonList(Word.epsilon());
    }

    private List<Word<I>> initialSuffixes() {
        return Collections.singletonList(Word.epsilon());
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Boolean> ceQuery) {
        if (!MQUtil.isCounterexample(ceQuery, hypothesis)) {
            return false;
        }

        // 1. We compute the new counter limit
        counterLimit = computeMaximalCounterValue(ceQuery.getInput(), alphabet);

        // 2. We increase the counter limit in the oracles
        membershipOracle.setCounterLimit(counterLimit);
        restrictedAutomatonEquivalenceOracle.setCounterLimit(counterLimit);

        // 3. We refine the table
        List<List<Row<I>>> unclosed = table.increaseCounterLimit(counterLimit, membershipOracle);
        completeConsistentTable(unclosed, true);
        unclosed = ObservationTableCEXHandlers.handleClassicLStar(ceQuery, table, membershipOracle);
        completeConsistentTable(unclosed, true);

        // 4. We learn the DFA up to the counter value
        learnDFA();
        updateHypothesis();

        return true;
    }

    private void updateHypothesis() {
        if (!table.isInitialized()) {
            throw new IllegalStateException("Cannot update internal hypothesis: not initialized");
        }

        final DefaultAutomatonWithCounterValuesVCA<I> automaton = new DefaultAutomatonWithCounterValuesVCA<>(alphabet);
        final Set<Row<I>> representativeRows = table.getRepresentativeRows();

        Map<Integer, DefaultAutomatonWithCounterValuesState> idToState = new HashMap<>();
        for (Row<I> row : representativeRows) {
            boolean initial = row.getLabel() == Word.epsilon();
            OutputAndCounterValue<Boolean> cellContents = table.fullCellContents(row, 0);
            boolean accepting = cellContents.getOutput();
            int counterValue = cellContents.getCounterValue();

            DefaultAutomatonWithCounterValuesState state;
            if (initial) {
                state = automaton.addInitialState(accepting, counterValue);
            } else {
                state = automaton.addState(accepting, counterValue);
            }

            idToState.put(row.getRowId(), state);
        }

        for (Row<I> row : representativeRows) {
            DefaultAutomatonWithCounterValuesState start = idToState.get(row.getRowId());
            for (int i = 0; i < alphabet.size(); i++) {
                Row<I> targetRow = row.getSuccessor(i);
                Row<I> targetRepresentative = table.getRepresentativeForEquivalenceClass(targetRow);
                if (targetRepresentative != null) {
                    DefaultAutomatonWithCounterValuesState target = idToState.get(targetRepresentative.getRowId());
                    automaton.setTransition(start, alphabet.getSymbol(i), target);
                }
            }
        }

        hypothesis = automaton;
    }

    private void learnDFA() {
        while (true) {
            updateHypothesis();
            DefaultQuery<I, Boolean> ce = restrictedAutomatonEquivalenceOracle.findCounterExample(hypothesis, alphabet);

            if (ce == null) {
                return;
            }

            int oldDistinctRows = table.numberOfDistinctRows();

            List<List<Row<I>>> unclosed = ObservationTableCEXHandlers.handleClassicLStar(ce, table, membershipOracle);
            completeConsistentTable(unclosed, true);
            assert table.numberOfDistinctRows() > oldDistinctRows
                    : "Nothing was learnt during the last iteration for DFA";
        }
    }

    @Override
    public ObservationTableWithCounterValuesVCA<I> getObservationTable() {
        return table;
    }

    @Override
    public Collection<Word<I>> getGlobalSuffixes() {
        return Collections.unmodifiableList(table.getSuffixes());
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
            OutputAndCounterValue<Boolean> val1 = table.fullCellContents(successorRow1, i);
            OutputAndCounterValue<Boolean> val2 = table.fullCellContents(successorRow2, i);
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
}
