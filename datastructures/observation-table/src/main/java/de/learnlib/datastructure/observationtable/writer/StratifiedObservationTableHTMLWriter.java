package de.learnlib.datastructure.observationtable.writer;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.StratifiedObservationTable;
import net.automatalib.words.Word;

/**
 * ¨A writer outputting an HTML file from a stratified observation table.
 * 
 * @param <I> Input alphabet type
 * @param <D> Output type
 * @author Gaëtan Staquet
 */
public class StratifiedObservationTableHTMLWriter<I, D> extends AbstractStratifiedObservationTableWriter<I, D> {

    public StratifiedObservationTableHTMLWriter(Function<? super Word<? extends I>, ? extends String> wordToString,
            Function<? super D, ? extends String> outputToString) {
        super(wordToString, outputToString);
    }

    @Override
    public void write(StratifiedObservationTable<? extends I, ? extends D> table, Appendable out) throws IOException {
        writeInternal(table, wordToString, outputToString, out);
    }

    private <I, D> void writeInternal(StratifiedObservationTable<I, D> table,
            Function<? super Word<? extends I>, ? extends String> wordToString,
            Function<? super D, ? extends String> outputToString, Appendable out) throws IOException {
        for (int layer = 0; layer < table.numberOfLayers(); layer++) {
            out.append("<h2>Layer number ").append(String.valueOf(layer)).append("</h2>");

            List<Word<I>> suffixes = table.getSuffixes(layer);

            out.append("<table class=\"learnlib-observationtable\">").append(System.lineSeparator());
            out.append("\t<thead>").append(System.lineSeparator());
            out.append("\t\t<tr><th rowspan=\"2\" class=\"prefix\">Prefix</th><th colspan=\"")
                    .append(Integer.toString(suffixes.size())).append("\" class=\"suffixes-header\">Suffixes</th></tr>")
                    .append(System.lineSeparator());
            out.append("\t\t<tr>");
            for (Word<I> suffix : suffixes) {
                out.append("<td>").append(wordToString.apply(suffix)).append("</td>");
            }
            out.append("</tr>").append(System.lineSeparator());
            out.append("\t</thead>").append(System.lineSeparator());
            out.append("\t<tbody>").append(System.lineSeparator());

            for (Row<I> row : table.getShortPrefixRows(layer)) {
                out.append("\t\t<tr class=\"short-prefix\"><td class=\"prefix\">")
                        .append(wordToString.apply(row.getLabel())).append(" ").append(String.valueOf(row.getRowId()))
                        .append(" ").append(String.valueOf(row.getRowContentId())).append("</td>");
                for (D value : table.rowContents(row)) {
                    out.append("<td class=\"suffix-column\">").append(outputToString.apply(value)).append("</td>");
                }
                out.append("</tr>").append(System.lineSeparator());
            }

            out.append("\t\t<tr><td colspan=\"").append(Integer.toString(suffixes.size() + 1)).append("\"></td></tr>")
                    .append(System.lineSeparator());

            for (Row<I> row : table.getLongPrefixRows(layer)) {
                out.append("\t\t<tr class=\"long-prefix\"><td>").append(wordToString.apply(row.getLabel())).append(" ")
                        .append(String.valueOf(row.getRowId())).append(" ")
                        .append(String.valueOf(row.getRowContentId())).append("</td>");
                for (D value : table.rowContents(row)) {
                    out.append("<td class=\"suffix-column\">").append(outputToString.apply(value)).append("</td>");
                }
                out.append("</tr>").append(System.lineSeparator());
            }

            out.append("</table>").append(System.lineSeparator());
        }
    }

}
