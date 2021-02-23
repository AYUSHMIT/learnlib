package de.learnlib.algorithms.lstar.roca;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.OTLearner;
import de.learnlib.datastructure.observationtable.ObservationTableWithCounterValues;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.ObservationTableWithCounterValues.CounterValueAssignment;
import de.learnlib.util.MQUtil;
import de.learnlib.api.algorithm.feature.GlobalSuffixLearner;
import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.automata.oca.ROCA;
import net.automatalib.automata.oca.automatoncountervalues.AcceptingOrExit;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValues;
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
 * @author Gaëtan Staquet
 */
public final class LStarROCA<I> implements OTLearner.OTLearnerROCA<I>,
        GlobalSuffixLearner<ROCA<?, I>, I, AcceptingOrExit>, SupportsGrowingAlphabet<I> {

    private final Alphabet<I> alphabet;

    private final MembershipOracle.RestrictedAutomatonMembershipOracle<I> membershipOracle;
    private final EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> restrictedAutomatonEquivalenceOracle;

    private final ObservationTableWithCounterValues<I> table;
    private List<DefaultAutomatonWithCounterValues<I>> automataWithCounterValues;

    public LStarROCA(MembershipOracle.RestrictedAutomatonMembershipOracle<I> membershipOracle,
            EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> automatonWithCounterValuesEquivalenceOracle,
            Alphabet<I> alphabet) {
        this.membershipOracle = membershipOracle;
        this.restrictedAutomatonEquivalenceOracle = automatonWithCounterValuesEquivalenceOracle;
        this.alphabet = alphabet;
        this.table = new ObservationTableWithCounterValues<>(alphabet);
        automataWithCounterValues = new LinkedList<>();
        automataWithCounterValues.add(new DefaultAutomatonWithCounterValues<>(alphabet));
    }

    @Override
    public Iterator<ROCA<?, I>> getHypothesisModels() {
        return new Iterator<ROCA<?, I>>() {

            private final Iterator<DefaultAutomatonWithCounterValues<I>> automata = automataWithCounterValues
                    .iterator();
            private Iterator<ROCA<?, I>> rocas = null;

            @Override
            public boolean hasNext() {
                while ((rocas == null || !rocas.hasNext()) && automata.hasNext()) {
                    DefaultAutomatonWithCounterValues<I> automaton = automata.next();
                    // @formatter:off
                    List<ROCA<?, I>> goodRocas = automaton.toROCAs(table.getCounterLimit()).stream()
                            .filter(roca -> isConsistent(roca))
                            .collect(Collectors.toList());
                    // @formatter:on
                    rocas = goodRocas.iterator();
                }
                if (rocas == null) {
                    return false;
                }
                return rocas.hasNext();
            }

            @Override
            public ROCA<?, I> next() {
                return rocas.next();
            }

        };
    }

    private boolean isConsistent(ROCA<?, I> roca) {
        // We want the ROCA to be correct with regards to the information stored in the table.
        // That is, the ROCA and the table must agree on the acceptance of the words.
        for (Row<I> row : table.getAllRows()) {
            Word<I> prefix = row.getLabel();
            List<AcceptingOrExit> rowContent = table.rowContents(row);
            for (int i = 0 ; i < table.numberOfSuffixes() ; i++) {
                Word<I> word = prefix.concat(table.getSuffix(i));
                boolean shouldBeAccepted = rowContent.get(i) == AcceptingOrExit.ACCEPTING;
                if (roca.accepts(word) != shouldBeAccepted) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void startLearning() {
        List<Word<I>> prefixes = initialPrefixes();
        List<Word<I>> suffixes = initialSuffixes();
        List<List<Row<I>>> initialUnclosed = table.initialize(prefixes, suffixes, membershipOracle);

        completeConsistentTable(initialUnclosed, table.isInitialConsistencyCheckRequired());

        learnDFA();

        updateAutomataWithCounterValues();
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, AcceptingOrExit> ceQuery) {
        if (!MQUtil.isCounterexample(ceQuery, automataWithCounterValues.get(0))) {
            return false;
        }

        // 1. We add the prefixes of the counterexample as prefixes in the table.
        // 2. We increment the counter limit.
        // 3. We learn the DFA for the restricted automaton up to that counter limit.
        // 4. We construct the automata with counter values from the table.
        // We repeat 2 to 4 as long as the counterexample remains incorrect.
        // Indeed, it may happen that the teacher gives a counterexample such that the
        // counter value needed to correctly accept (or reject) the word requires
        // multiple increments.

        List<List<Row<I>>> unclosed = ObservationTableCEXHandlers.handleClassicLStar(ceQuery, table, membershipOracle);
        completeConsistentTable(unclosed, true);

        while (MQUtil.isCounterexample(ceQuery, automataWithCounterValues.get(0))) {
            membershipOracle.incrementCounterLimit();
            restrictedAutomatonEquivalenceOracle.incrementCounterLimit();
            unclosed = table.incrementCounterLimit(membershipOracle);

            completeConsistentTable(unclosed, true);
            table.computeCounterValues(false);

            learnDFA();

            updateAutomataWithCounterValues();
        }

        return true;
    }

    /**
     * Learns the DFA for the restricted language up to the counter limit.
     */
    private void learnDFA() {
        while (true) {
            final DFA<?, I> hypothesis = constructDFAHypothesis();
            DefaultQuery<I, Boolean> ce = restrictedAutomatonEquivalenceOracle.findCounterExample(hypothesis, alphabet);

            if (ce == null) {
                return;
            }

            int oldDistinctRows = table.numberOfDistinctRows();
            List<Word<I>> prefixes = ce.getInput().prefixes(false);
            List<List<Row<I>>> unclosed = table.addShortPrefixes(prefixes, membershipOracle);
            completeConsistentTable(unclosed, true);
            assert table.numberOfDistinctRows() > oldDistinctRows;
        }
    }

    private DFA<?, I> constructDFAHypothesis() {
        Set<Row<I>> representativeRows = table.getRepresentativeRows();

        CompactDFA<I> dfa = new CompactDFA<>(alphabet);

        // We create the states of the DFA
        Map<Integer, Integer> idToState = new HashMap<>();
        for (Row<I> row : representativeRows) {
            boolean initial = row.getLabel() == Word.epsilon();
            boolean accepting = table.isAcceptingRow(row);
            Integer state;
            if (initial) {
                state = dfa.addInitialState(accepting);
            } else {
                state = dfa.addState(accepting);
            }

            idToState.put(row.getRowId(), state);
        }

        // We create the transitions
        for (Row<I> row : representativeRows) {
            Integer start = idToState.get(row.getRowId());

            for (int i = 0; i < alphabet.size(); i++) {
                Row<I> targetRow = row.getSuccessor(i);
                // If the transition leads to the exit state, we completely ignore it (so, it
                // will lead to a bin state, as intended).
                Row<I> targetRepresentative = table.getRepresentativeForEquivalenceClass(targetRow);
                if (targetRepresentative != null) {
                    Integer target = idToState.get(targetRepresentative.getRowId());
                    dfa.setTransition(start, alphabet.getSymbol(i), target);
                }

            }
        }

        return dfa;
    }

    @Override
    public ObservationTableWithCounterValues<I> getObservationTable() {
        return table;
    }

    private List<Word<I>> initialPrefixes() {
        return Collections.singletonList(Word.epsilon());
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
            closingRows.add(rowList.get(0));
        }

        return closingRows;
    }

    private Word<I> analyzeInconsistency(Inconsistency<I> inconsistency) {
        int inputIdx = alphabet.getSymbolIndex(inconsistency.getSymbol());

        Row<I> successorRow1 = inconsistency.getFirstRow().getSuccessor(inputIdx);
        Row<I> successorRow2 = inconsistency.getSecondRow().getSuccessor(inputIdx);

        int numSuffixes = table.getSuffixes().size();

        for (int i = 0; i < numSuffixes; i++) {
            AcceptingOrExit val1 = table.cellContents(successorRow1, i), val2 = table.cellContents(successorRow2, i);
            if (!Objects.equals(val1, val2)) {
                I sym = alphabet.getSymbol(inputIdx);
                Word<I> suffix = table.getSuffixes().get(i);
                return suffix.prepend(sym);
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
        return automataWithCounterValues.get(0).asROCA();
    }

    private void updateAutomataWithCounterValues() {
        if (!table.isInitialized()) {
            throw new IllegalStateException("Cannot update internal hypothesis: not initialized");
        }

        final Set<Row<I>> representativeRows = table.getRepresentativeRows();
        automataWithCounterValues.clear();

        for (CounterValueAssignment<I> assignment : table.getAllPossibleCounterValuesAssignments()) {
            DefaultAutomatonWithCounterValues<I> automaton = new DefaultAutomatonWithCounterValues<>(alphabet);

            Map<Integer, DefaultAutomatonWithCounterValuesState> idToState = new HashMap<>();
            for (Row<I> representativeRow : representativeRows) {
                if (!table.isExitRow(representativeRow) && !table.isBinRow(representativeRow)) {
                    boolean initial = representativeRow.getLabel() == Word.epsilon();
                    int cv = assignment.getValue(representativeRow);
                    DefaultAutomatonWithCounterValuesState state = createState(automaton, initial, representativeRow,
                            cv);
                    idToState.put(representativeRow.getRowId(), state);
                }
            }

            for (Row<I> representativeRow : representativeRows) {
                if (!table.isExitRow(representativeRow) && !table.isBinRow(representativeRow)) {
                    DefaultAutomatonWithCounterValuesState start = idToState.get(representativeRow.getRowId());
                    for (int i = 0; i < alphabet.size(); i++) {
                        Row<I> targetRow = representativeRow.getSuccessor(i);
                        if (targetRow != null) {
                            Row<I> targetRepresentativeRow = table.getRepresentativeForEquivalenceClass(targetRow);
                            DefaultAutomatonWithCounterValuesState target = null;

                            if (!idToState.containsKey(-1)
                                    && (table.isExitRow(targetRow) || table.isExitRow(targetRepresentativeRow))) {
                                // We construct the exit state
                                DefaultAutomatonWithCounterValuesState exitState = automaton
                                        .addState(AcceptingOrExit.EXIT, table.getCounterLimit() + 1);
                                for (I input : alphabet) {
                                    automaton.setTransition(exitState, input, exitState);
                                }
                                idToState.put(-1, exitState);
                                target = exitState;
                            } else if (!table.isBinRow(targetRepresentativeRow)) {
                                target = idToState.get(targetRepresentativeRow.getRowId());
                            }

                            if (target != null) {
                                automaton.setTransition(start, alphabet.getSymbol(i), target);
                            }
                        }
                    }
                }
            }

            automataWithCounterValues.add(automaton);
        }
    }

    private AcceptingOrExit stateAcceptance(Row<I> stateRow) {
        return table.cellContents(stateRow, 0);
    }

    private DefaultAutomatonWithCounterValuesState createState(DefaultAutomatonWithCounterValues<I> automaton,
            boolean initial, Row<I> row, int counterValue) {
        AcceptingOrExit acceptance = stateAcceptance(row);
        if (initial) {
            return automaton.addInitialState(acceptance, counterValue);
        }
        return automaton.addState(acceptance, counterValue);
    }
}
