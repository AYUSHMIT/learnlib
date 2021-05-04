package de.learnlib.datastructure.observationtable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.automatalib.words.Word;

/**
 * A stratified observation table is an observation table split into multiple
 * layers.
 * 
 * Typically, each layer has its own sets of short and long prefixes, and
 * suffixes.
 * 
 * @param <I> Input symbol type
 * @param <D> Output type
 * @author Gaëtan Staquet
 */
public interface StratifiedObservationTable<I, D> extends ObservationTable<I, D> {
    /**
     * Gets the number of layers in the stratified table
     * 
     * @return
     */
    public int numberOfLayers();

    /**
     * Gets the short prefix rows of the layer.
     * 
     * @param layer The layer
     * @return The short prefix rows in the layer
     */
    public Collection<Row<I>> getShortPrefixRows(int layer);

    /**
     * Gets the long prefix rows of the layer.
     * 
     * @param layer The layer
     * @return The long prefix rows in the layer
     */
    public Collection<Row<I>> getLongPrefixRows(int layer);

    /**
     * Gets the suffixes of the layer.
     * 
     * @param layer The layer
     * @return The suffixes in the layer
     */
    public List<Word<I>> getSuffixes(int layer);

    /**
     * Gets the number of distinct rows in the layer
     * 
     * @param layer The layer
     * @return The number of distinct rows in the layer
     */
    public int numberOfDistinctRows(int layer);

    public default Collection<Row<I>> getAllRows(int layer) {
        Collection<Row<I>> spRows = getShortPrefixRows(layer);
        Collection<Row<I>> lpRows = getLongPrefixRows(layer);

        List<Row<I>> result = new ArrayList<>(spRows.size() + lpRows.size());
        result.addAll(spRows);
        result.addAll(lpRows);

        return result;
    }
}
