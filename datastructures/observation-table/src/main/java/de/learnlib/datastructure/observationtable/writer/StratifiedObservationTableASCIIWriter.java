package de.learnlib.datastructure.observationtable.writer;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.StratifiedObservationTable;
import net.automatalib.words.Word;

/**
 * A writer outputting ASCII from a stratified observation table.
 * 
 * @param <I> Input alphabet type
 * @param <D> Output type
 * @author Gaëtan Staquet
 */
public class StratifiedObservationTableASCIIWriter<I, D> extends AbstractStratifiedObservationTableWriter<I, D> {
    private boolean rowSeparators;
    private boolean layerSeparators;

    public StratifiedObservationTableASCIIWriter(Function<? super Word<? extends I>, ? extends String> wordToString,
            Function<? super D, ? extends String> outputToString, boolean rowSeparators, boolean layerSeparators) {
        super(wordToString, outputToString);
        this.rowSeparators = rowSeparators;
        this.layerSeparators = layerSeparators;
    }

    public StratifiedObservationTableASCIIWriter() {
        this(true, true);
    }

    public StratifiedObservationTableASCIIWriter(boolean rowSeparators, boolean layerSeparators) {
        this.rowSeparators = rowSeparators;
        this.layerSeparators = layerSeparators;
    }

    public void setRowSeparators(boolean rowSeparators) {
        this.rowSeparators = rowSeparators;
    }

    public void setLayerSeparators(boolean layerSeparators) {
        this.layerSeparators = layerSeparators;
    }

    @Override
    public void write(StratifiedObservationTable<? extends I, ? extends D> table, Appendable out) throws IOException {
        writeInternal(table, super.wordToString, super.outputToString, out);
    }

    /**
     * Utility method to bind wildcard generics.
     *
     * @see #write(ObservationTable, Appendable)
     */
    private <I, D> void writeInternal(StratifiedObservationTable<I, D> table,
            Function<? super Word<? extends I>, ? extends String> wordToString,
            Function<? super D, ? extends String> outputToString, Appendable out) throws IOException {
        for (int counterValue = 0; counterValue < table.numberOfLayers(); counterValue++) {
            if (layerSeparators) {
                appendSeparatorLayer(out, counterValue);
            }
            List<Word<I>> suffixes = table.getSuffixes(counterValue);
            int numSuffixes = suffixes.size();

            int[] colWidth = new int[numSuffixes + 1];

            int i = 1;
            for (Word<I> suffix : suffixes) {
                colWidth[i++] = wordToString.apply(suffix).length();
            }

            for (Row<I> row : table.getAllRows()) {
                int thisWidth = wordToString.apply(row.getLabel()).length();
                if (thisWidth > colWidth[0]) {
                    colWidth[0] = thisWidth;
                }

                i = 1;
                for (D value : table.rowContents(row)) {
                    thisWidth = outputToString.apply(value).length();
                    if (thisWidth > colWidth[i]) {
                        colWidth[i] = thisWidth;
                    }
                    i++;
                }
            }

            appendSeparatorRow(out, '=', colWidth);
            String[] content = new String[numSuffixes + 1];

            // Header
            content[0] = "";
            i = 1;
            for (Word<I> suffix : suffixes) {
                content[i++] = wordToString.apply(suffix);
            }
            appendContentRow(out, content, colWidth);
            appendSeparatorRow(out, '=', colWidth);

            boolean first = true;
            for (Row<I> spRow : table.getShortPrefixRows()) {
                if (first) {
                    first = false;
                } else if (rowSeparators) {
                    appendSeparatorRow(out, '-', colWidth);
                }
                content[0] = wordToString.apply(spRow.getLabel());
                i = 1;
                for (D value : table.rowContents(spRow)) {
                    content[i++] = outputToString.apply(value);
                }
                appendContentRow(out, content, colWidth);
            }

            appendSeparatorRow(out, '=', colWidth);

            first = true;
            for (Row<I> lpRow : table.getLongPrefixRows()) {
                if (first) {
                    first = false;
                } else if (rowSeparators) {
                    appendSeparatorRow(out, '-', colWidth);
                }
                content[0] = wordToString.apply(lpRow.getLabel());
                i = 1;
                for (D value : table.rowContents(lpRow)) {
                    content[i++] = outputToString.apply(value);
                }
                appendContentRow(out, content, colWidth);
            }

            appendSeparatorRow(out, '=', colWidth);
        }
    }

    private static void appendSeparatorRow(Appendable a, char sepChar, int[] colWidth) throws IOException {
        a.append('+').append(sepChar);
        appendRepeated(a, sepChar, colWidth[0]);
        for (int i = 1; i < colWidth.length; i++) {
            a.append(sepChar).append('+').append(sepChar);
            appendRepeated(a, sepChar, colWidth[i]);
        }
        a.append(sepChar).append("+").append(System.lineSeparator());
    }

    private static void appendSeparatorLayer(Appendable a, int numLayer) throws IOException {
        a.append("Layer number ").append(String.valueOf(numLayer));
        a.append(System.lineSeparator());
    }

    private static void appendContentRow(Appendable a, String[] content, int[] colWidth) throws IOException {
        a.append("| ");
        appendRightPadded(a, content[0], colWidth[0]);
        for (int i = 1; i < content.length; i++) {
            a.append(" | ");
            appendRightPadded(a, content[i], colWidth[i]);
        }
        a.append(" |").append(System.lineSeparator());
    }

    private static void appendRepeated(Appendable a, char c, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            a.append(c);
        }
    }

    private static void appendRightPadded(Appendable a, String string, int width) throws IOException {
        a.append(string);
        appendRepeated(a, ' ', width - string.length());
    }
}
