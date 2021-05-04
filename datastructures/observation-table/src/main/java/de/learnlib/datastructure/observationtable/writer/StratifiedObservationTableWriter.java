package de.learnlib.datastructure.observationtable.writer;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;

import de.learnlib.datastructure.observationtable.StratifiedObservationTable;
import net.automatalib.commons.util.IOUtil;

/**
 * A writer transforms a stratified observation table into a string of text
 * representing that table.
 * 
 * @param <I> Input alphabet type
 * @param <D> Output type
 * @author Gaëtan Staquet
 */
public interface StratifiedObservationTableWriter<I, D> {
    void write(StratifiedObservationTable<? extends I, ? extends D> table, Appendable out) throws IOException;

    default void write(StratifiedObservationTable<? extends I, ? extends D> table, PrintStream out) {
        try {
            write(table, (Appendable) out);
        } catch (IOException ex) {
            throw new AssertionError("Writing to PrintStream must not throw", ex);
        }
    }

    default void write(StratifiedObservationTable<? extends I, ? extends D> table, StringBuilder out) {
        try {
            write(table, (Appendable) out);
        } catch (IOException ex) {
            throw new AssertionError("Writing to StringBuilder must not throw", ex);
        }
    }

    default void write(StratifiedObservationTable<? extends I, ? extends D> table, File file) throws IOException {
        try (Writer w = IOUtil.asBufferedUTF8Writer(file)) {
            write(table, w);
        }
    }
}
