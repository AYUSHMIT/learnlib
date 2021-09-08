package de.learnlib.algorithms.lstar.roca;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.automatalib.words.Word;

/**
 * Utility class to reduce the memory consumption of
 * {@link ObservationTreeNode}.
 * 
 * Instead of explicitly storing every prefix in the nodes, we instead store a
 * minimal collection of words. The nodes then hold a {@link WordReference} to
 * their corresponding prefix.
 * 
 * @param <I> Input alphabet type
 * @author Gaëtan Staquet
 */
class WordStorage<I> {
    static class WordReference {
        private final int wordId;
        private final int length;

        WordReference(int wordId, int length) {
            this.wordId = wordId;
            this.length = length;
        }

        public int getWordId() {
            return wordId;
        }

        public int getLength() {
            return length;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj.getClass() != this.getClass()) {
                return false;
            }
            WordReference s = (WordReference) obj;
            return s.wordId == this.wordId && s.length == this.length;
        }

        @Override
        public int hashCode() {
            return Objects.hash(wordId, length);
        }
    }

    private final List<Word<I>> listOfWords = new ArrayList<>();

    public Word<I> getWord(int wordId, int length) {
        return listOfWords.get(wordId).subWord(0, length);
    }

    public int addWord(Word<I> word) {
        listOfWords.add(word);
        return listOfWords.size() - 1;
    }

    public void replaceWord(int wordId, Word<I> newWord) {
        listOfWords.set(wordId, newWord);
    }
}
