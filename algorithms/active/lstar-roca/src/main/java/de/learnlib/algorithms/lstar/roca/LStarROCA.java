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

import static de.learnlib.algorithms.lstar.roca.ObservationTableWithCounterValuesROCA.UNKNOWN_COUNTER_VALUE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.learnlib.algorithms.lstar.roca.ObservationTreeNode.TableCell;
import de.learnlib.api.algorithm.feature.GlobalSuffixLearner;
import de.learnlib.api.logging.LearnLogger;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.OTLearner;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.filter.statistic.Counter;
import de.learnlib.util.MQUtil;
import de.learnlib.util.statistics.SimpleProfiler;
import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.oca.automatoncountervalues.AutomatonWithCounterValues;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValuesROCA;
import net.automatalib.automata.oca.automatoncountervalues.DefaultAutomatonWithCounterValuesState;
import net.automatalib.automata.oca.automatoncountervalues.ROCAFromDescription;
import net.automatalib.commons.util.Pair;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * A learner based on L* for ROCAs.
 * 
 * The learner first learns a DFA accepting a sub-language of the target
 * language up to a certain counter limit. This sub-language is called the
 * restricted language (and a DFA accepting that language is called a restricted
 * automaton, see {@link AutomatonWithCounterValues}). On top of that, the
 * learner associates possible counter values to each cell in the table. Once
 * the restricted automaton is correctly learned alongside the counter values,
 * the learner proceeds to construct every possible ROCA using that automaton
 * and the values.
 * 
 * Each time a counterexample is processed after asking equivalence queries over
 * the ROCAs, the counter limit is increased. This means that the restricted
 * language is modified to accept more words. In turn, this implies that the DFA
 * must be re-learned. However, we can keep the information learned before. That
 * is, we only need to identify the classes in the new part of the restricted
 * language.
 * 
 * Note that this means that the learner can yield multiple hypotheses by
 * learning round.
 * 
 * For more details on the data structure, see
 * {@link ObservationTableWithCounterValuesROCA}.
 * 
 * The implementation assumes the target ROCA accepts by final location and
 * counter value equal to zero.
 * 
 * @author Gaëtan Staquet
 */
public class LStarROCA<I>
        implements OTLearner.OTLearnerROCA<I>, GlobalSuffixLearner<ROCAFromDescription<?, I>, I, Boolean>, SupportsGrowingAlphabet<I> {

    public static final String CLOSED_TABLE_PROFILE_KEY = "Making the table closed, Sigma-consistent, and bottom-consistent";
    public static final String COUNTEREXAMPLE_DFA_PROFILE_KEY = "Searching for counterexample DFA";
    public static final String FINDING_PERIODIC_DESCRIPTIONS = "Computing periodic descriptions";

    private static final LearnLogger LOGGER = LearnLogger.getLogger(LStarROCA.class);

    protected final Alphabet<I> alphabet;

    protected final MembershipOracle.ROCAMembershipOracle<I> membershipOracle;
    protected final MembershipOracle.CounterValueOracle<I> counterValueOracle;
    protected final EquivalenceOracle.RestrictedAutomatonEquivalenceOracle<I> restrictedAutomatonEquivalenceOracle;

    protected final ObservationTableWithCounterValuesROCA<I> table;
    protected DefaultAutomatonWithCounterValuesROCA<I> hypothesis;
    protected int counterLimit;

    protected Counter numberOfUnclosedRows = new Counter("Resolved unclosed rows", "rows");
    protected Counter numberOfSigmaInconsistencies = new Counter("Sigma-inconsistencies", "inconsistencies");
    protected Counter numberOfBottomInconsistencies = new Counter("Bottom-inconsistencies", "inconsistencies");
    protected Counter numberOfResolvedMismatches = new Counter("Resolved mismatches", "mismatches");
    protected long lengthLongestCounterexample = 0;

    public LStarROCA(MembershipOracle.ROCAMembershipOracle<I> membershipOracle,
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

    public DefaultAutomatonWithCounterValuesROCA<I> getHypothesis() {
        return hypothesis;
    }

    @Override
    public int getCounterLimit() {
        return counterLimit;
    }

    @Override
    public List<ROCAFromDescription<?, I>> getHypothesisModels() {
        if (!table.isInitialized()) {
            return Collections.singletonList(hypothesis.asAutomaton());
        }

        SimpleProfiler.start(FINDING_PERIODIC_DESCRIPTIONS);
        List<ROCAFromDescription<?, I>> rocas = hypothesis.toAutomata(counterLimit);
        SimpleProfiler.stop(FINDING_PERIODIC_DESCRIPTIONS);
        return rocas;
    }

    @Override
    public void startLearning() {
        // During the first round, we only want to test if the language is empty
        // Since the hypothesis is initialized as an ROCA accepting nothing, we have
        // nothing to do
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Boolean> ceQuery) {
        lengthLongestCounterexample = Math.max(lengthLongestCounterexample, ceQuery.getInput().length());
        // 1. We compute the new counter limit
        List<DefaultQuery<I, Integer>> counterValueQueries = new ArrayList<>(ceQuery.getInput().length());
        for (Word<I> prefix : ceQuery.getInput().prefixes(false)) {
            DefaultQuery<I, Integer> query = new DefaultQuery<>(prefix);
            counterValueQueries.add(query);
        }
        counterValueOracle.processQueries(counterValueQueries);
        // In practical cases, it may happen that the maximal counter value of the
        // counterexample is lower than the current counter limit (if the equivalence
        // oracle relies on conformance testing, for instance). Therefore, we must make
        // sure we do not lower the counter limit.
        // @formatter:off
        counterLimit = Math.max(counterLimit, counterValueQueries.stream()
            .map(query -> query.getOutput())
            .max(Integer::compare)
            .get());
        // @formatter:on

        // 2. We increase the counter limit in the oracles
        restrictedAutomatonEquivalenceOracle.setCounterLimit(counterLimit);

        // 3. If it's the first time we refine the hypothesis, we initialize the table
        // using the counter-example.
        // If it is not the firs time, we refine the table
        List<List<Row<I>>> unclosed;
        if (!table.isInitialized()) {
            final List<Word<I>> initialPrefixes = ceQuery.getInput().prefixes(false);
            final List<Word<I>> initialSuffixes = initialSuffixes();

            table.setInitialCounterLimit(counterLimit);
            unclosed = table.initialize(initialPrefixes, initialSuffixes, membershipOracle);
            completeConsistentTable(unclosed, table.isInitialConsistencyCheckRequired());
        } else if (!MQUtil.isCounterexample(ceQuery, hypothesis)) {
            return false;
        } else {
            Word<I> counterexample = ceQuery.getInput();
            unclosed = table.increaseCounterLimit(counterLimit, counterexample.prefixes(false), Collections.emptyList(),
                    membershipOracle);
            LOGGER.logPhase("Counter limit increased to " + counterLimit);
            completeConsistentTable(unclosed, true);
        }

        // 4. We learn the DFA up to the counter value
        learnDFA();

        return true;
    }

    /**
     * Learns the DFA for the restricted language up to the counter limit.
     */
    protected void learnDFA() {
        while (true) {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                return;
            }

            updateHypothesis();
            SimpleProfiler.start(COUNTEREXAMPLE_DFA_PROFILE_KEY);
            DefaultQuery<I, Boolean> ce = restrictedAutomatonEquivalenceOracle.findCounterExample(hypothesis, alphabet);
            SimpleProfiler.stop(COUNTEREXAMPLE_DFA_PROFILE_KEY);

            if (ce == null) {
                return;
            }
            assert MQUtil.isCounterexample(ce, hypothesis);
            lengthLongestCounterexample = Math.max(lengthLongestCounterexample, ce.getInput().length());
            LOGGER.logCounterexample("Partial equivalence query counterexample " + ce.getInput().toString());

            int oldDistinctRows = table.numberOfDistinctRows();
            int oldSuffixes = table.numberOfSuffixes();

            List<List<Row<I>>> unclosed = table.addShortPrefixes(ce.getInput().prefixes(false), membershipOracle);
            completeConsistentTable(unclosed, true);

            assert table.numberOfDistinctRows() > oldDistinctRows || table.numberOfSuffixes() > oldSuffixes
                    : "Nothing was learned during the last iteration for DFA";
        }
    }

    @Override
    public ObservationTableWithCounterValuesROCA<I> getObservationTable() {
        return table;
    }

    @Override
    public boolean isCounterexample(Word<I> word, boolean output) {
        return hypothesis.accepts(word) != output;
    }

    public long numberOfRowsUsedToCloseTable() {
        return numberOfUnclosedRows.getCount();
    }

    public long numberOfSigmaInconsistencies() {
        return numberOfSigmaInconsistencies.getCount();
    }

    public long numberOfBottomInconsistencies() {
        return numberOfBottomInconsistencies.getCount();
    }

    public long numberOfBottomInconsistenciesResolvedByAddingOnlyForLanguageSuffixes() {
        return numberOfResolvedMismatches.getCount();
    }

    public long getLengthOfTheLongestCounterexample() {
        return lengthLongestCounterexample;
    }

    protected List<Word<I>> initialSuffixes() {
        return Collections.singletonList(Word.epsilon());
    }

    protected boolean completeConsistentTable(List<List<Row<I>>> unclosed, boolean checkConsistency) {
        SimpleProfiler.start(CLOSED_TABLE_PROFILE_KEY);
        boolean refined = false;
        List<List<Row<I>>> unclosedIter = unclosed;
        do {
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                return false;
            }
            while (!unclosedIter.isEmpty()) {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                List<Row<I>> closingRows = selectClosingRows(unclosedIter);
                numberOfUnclosedRows.increment(closingRows.size());
                unclosedIter = table.toShortPrefixes(closingRows, membershipOracle);
                refined = true;
            }

            if (checkConsistency) {
                // A sigma inconsistency is due to a row u·a (with ua in R\Sigma) such that
                // there exists v in Approx(u) such that ua is not in Approx(va).
                // That is, one of outgoing transitions of ua is ill-defined (as multiple
                // different states are possible).
                Inconsistency<I> sigmaInconsistency;
                do {
                    if (Thread.interrupted()) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    sigmaInconsistency = table.findSigmaInconsistency();
                    if (sigmaInconsistency != null) {
                        numberOfSigmaInconsistencies.increment();
                        Set<Word<I>> newSuffix = analyzeSigmaInconsistency(sigmaInconsistency);
                        unclosedIter = table.addSuffixes(newSuffix, membershipOracle);
                    }
                } while (unclosedIter.isEmpty() && sigmaInconsistency != null);

                if (unclosedIter.isEmpty()) {
                    // A bottom inconsistency is due to a pair of rows u, v such that u in Approx(v)
                    // and there is a suffix s such that C_l(us) == -1 iff C_l(vs) != -1.
                    // That is, we can either remove u from Approx(v) or replace C_l(vs) by an
                    // actual counter value, if we can find a word witnessing the fact that vs is in
                    // prefix.
                    BottomInconsistency<I> bottomInconsistency;
                    do {
                        if (Thread.interrupted()) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                        bottomInconsistency = table.findBottomInconsistency();
                        if (bottomInconsistency != null) {
                            numberOfBottomInconsistencies.increment();
                            Pair<Boolean, Word<I>> analysis = analyzeBottomInconsistency(bottomInconsistency);
                            if (analysis.getFirst()) {
                                numberOfResolvedMismatches.increment();
                                unclosedIter = table.addSuffixesOnlyForLanguage(analysis.getSecond().suffixes(false),
                                        membershipOracle);
                            } else {
                                unclosedIter = table.addSuffixes(analysis.getSecond().suffixes(false),
                                        membershipOracle);
                            }
                        }
                    } while (unclosedIter.isEmpty() && bottomInconsistency != null);
                }
            }
        } while (!unclosedIter.isEmpty());

        SimpleProfiler.stop(CLOSED_TABLE_PROFILE_KEY);
        return refined;
    }

    private List<Row<I>> selectClosingRows(List<List<Row<I>>> unclosed) {
        List<Row<I>> closingRows = new ArrayList<>(unclosed.size());

        for (List<Row<I>> rowList : unclosed) {
            closingRows.add(rowList.get(0));
        }

        return closingRows;
    }

    private Set<Word<I>> analyzeSigmaInconsistency(Inconsistency<I> inconsistency) {
        List<Word<I>> knownSuffixes = table.getSuffixes();
        Set<Word<I>> newSuffixes = new HashSet<>();

        I symbol = inconsistency.getSymbol();
        int symbolIndex = alphabet.getSymbolIndex(symbol);
        Row<I> firstRowSuccessor = inconsistency.getFirstRow().getSuccessor(symbolIndex);
        Row<I> secondRowSuccessor = inconsistency.getSecondRow().getSuccessor(symbolIndex);
        List<PairCounterValueOutput<Boolean>> firstRowContents = table.fullRowContents(firstRowSuccessor);
        List<PairCounterValueOutput<Boolean>> secondRowContents = table.fullRowContents(secondRowSuccessor);
        int suffixIndex = 0;
        for (int i = 0; i < knownSuffixes.size(); i++) {
            if (table.isSuffixOnlyForLanguage(i)) {
                continue;
            }
            PairCounterValueOutput<Boolean> firstRowCell = firstRowContents.get(suffixIndex);
            PairCounterValueOutput<Boolean> secondRowCell = secondRowContents.get(suffixIndex);
            if (firstRowCell.getOutput() != secondRowCell.getOutput()) {
                newSuffixes.add(table.getSuffix(i).prepend(symbol));
            } else if (firstRowCell.getCounterValue() != UNKNOWN_COUNTER_VALUE
                    && secondRowCell.getCounterValue() != UNKNOWN_COUNTER_VALUE
                    && firstRowCell.getCounterValue() != secondRowCell.getCounterValue()) {
                newSuffixes.add(table.getSuffix(i).prepend(symbol));
            }
            suffixIndex++;
        }

        if (newSuffixes.isEmpty()) {
            throw new IllegalArgumentException("Bogus sigma inconsistency " + inconsistency.getFirstRow().getLabel()
                    + " " + inconsistency.getSecondRow().getLabel() + " " + inconsistency.getSymbol());
        }
        return newSuffixes;
    }

    private Pair<Boolean, Word<I>> analyzeBottomInconsistency(BottomInconsistency<I> inconsistency) {
        // u·s
        Word<I> u = inconsistency.getFirstRow().getLabel();
        Word<I> s = table.getSuffix(inconsistency.getSuffixIndex());
        Word<I> us = u.concat(s);
        Word<I> v = inconsistency.getSecondRow().getLabel();

        ObservationTreeNode<I> witness = table.getWitnessInPrefix(inconsistency.getFirstRow(),
                inconsistency.getSuffixIndex());
        List<TableCell<I>> cells = witness.getCellsInTableUsingNode();
        for (TableCell<I> cell : cells) {
            Word<I> s_prime = table.getSuffix(cell.getSuffixIndex());
            Word<I> u_prime = cell.getRow().getLabel();
            // u'·s'
            Word<I> u_prime_s_prime = u_prime.concat(s_prime);
            if (u_prime.isPrefixOf(u)) {
                Word<I> s_second = u_prime_s_prime.subWord(u.length());
                return Pair.of(false, s_second);
            } else { // u' is not a prefix of u
                assert membershipOracle.answerQuery(u_prime_s_prime);
                // u·s is a prefix of u'·s'
                assert us.isPrefixOf(u_prime_s_prime);
                // s'' = x·s' (with x such that u·x = u')
                Word<I> x = u_prime.subWord(u.length());
                Word<I> s_second = x.concat(s_prime);
                assert membershipOracle.answerQuery(u.concat(s_second));

                // v·s'' is accepted
                Word<I> v_s_prime = v.concat(s_second);
                if (table.getObservationTreeRoot().isSuffixAccepted(v_s_prime, 0, membershipOracle, counterValueOracle,
                        counterLimit)) {
                    return Pair.of(true, s_second);
                } else {
                    return Pair.of(false, s_second);
                }
            }
        }

        throw new IllegalArgumentException("Bogus bottom inconsistency " + inconsistency.getFirstRow().getLabel() + " "
                + inconsistency.getSecondRow().getLabel() + " " + inconsistency.getSuffixIndex());
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
    public ROCAFromDescription<?, I> getLearnedDFAAsROCA() {
        return hypothesis.asAutomaton();
    }

    protected void updateHypothesis() {
        assert table.isInitialized();

        Map<Integer, StateInfo<DefaultAutomatonWithCounterValuesState, I>> stateInfos = new HashMap<>();
        hypothesis = new DefaultAutomatonWithCounterValuesROCA<>(alphabet);

        Map<Row<I>, Map<I, Row<I>>> canonicalRowsAndSuccessors = table.getCanonicalRows();

        for (Row<I> canonical : canonicalRowsAndSuccessors.keySet()) {
            int id = canonical.getRowId();
            StateInfo<DefaultAutomatonWithCounterValuesState, I> info = stateInfos.getOrDefault(id, null);
            PairCounterValueOutput<Boolean> cell = table.fullCellContents(canonical, 0);
            boolean acceptance = cell.getOutput();
            int counterValue = cell.getCounterValue();

            if (info != null) {
                if (info.getRow() == canonical) {
                    hypothesis.setStateAcceptance(info.getState(), acceptance);
                }
            } else {
                DefaultAutomatonWithCounterValuesState state;
                if (table.isInInitialClass(canonical)) {
                    state = hypothesis.addInitialState(acceptance, 0);
                } else {
                    state = hypothesis.addState(acceptance, counterValue);
                }

                stateInfos.put(id, new StateInfo<>(canonical, state));
            }
        }

        for (StateInfo<DefaultAutomatonWithCounterValuesState, I> info : stateInfos.values()) {
            Row<I> canonical = info.getRow();
            DefaultAutomatonWithCounterValuesState state = info.getState();

            for (int i = 0; i < alphabet.size(); i++) {
                I input = alphabet.getSymbol(i);
                Row<I> successor = canonicalRowsAndSuccessors.get(canonical).get(input);
                if (successor != null) {
                    int successorId = successor.getRowId();
                    assert stateInfos.containsKey(successorId) : stateInfos.keySet() + " " + successorId;
                    DefaultAutomatonWithCounterValuesState successorState = stateInfos.get(successorId).getState();
                    hypothesis.setTransition(state, input, successorState);
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
