package app.alan.challenge;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.language.Soundex;
import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * This algorithm is focused on finding most suitable name in case of any mistake in source text.
 * The main condition is that the matching words should start with upper case letter and should not be the 'I' word.
 * So, in this way the algorithm won't work in a proper way, if the name in text begins with lowercase letter.
 *
 * Words that sounds similar (in English) but have a lot of differences in letters are also acceptable just like the case
 * if they sounds similar for more than a half (soundex index of common letters should be more than 2 of 4 as max).
 *
 * If the given name consists of more then two words, Jaro-Winkler's distance is counted, based on the matching of previous
 * and/or next word. This is done for the explicit identification of almost identical words in different word combinations,
 * such as 'Jon' and 'John' in phrases "Jon Hamm" and "John Nolan".
 *
 *
 * Computational complexity of the Algorithm is O(n*p) , where
 *  - n, is number names
 *  - p, number of words in phrases
 * */

public class App{

    static List<String> inputNames = new ArrayList<>();
    static List<String> inputPhrases = new LinkedList<>();

    static List<String> namesLines = new ArrayList<>();
    static List<String> phrasesLines = new ArrayList<>();

    static Map<Integer, List<String>> namesByWordsMap = new HashMap<>();
    static Map<Integer, List<String>> phrasesByWordsMap = new HashMap<>();

    static Map<String, String> possibleMatches= new HashMap<>();

    public static void main(String[] args) {
        getNamesAndPhrases();
        possibleMatches = findMatches();
        printResults();
    }

    private static void getNamesAndPhrases() {
        try {
            Path namesPath = Paths.get(Objects.requireNonNull(App.class.getClassLoader().getResource("names.txt")).toURI());
            Path phrasesPath = Paths.get(Objects.requireNonNull(App.class.getClassLoader().getResource("phrases.txt")).toURI());

            Files.lines(namesPath).forEach(inputNames::add);
            for (String name : inputNames) {
                namesLines.add(name.replaceAll("[^a-zA-Z ]", "").trim());
            }

            Files.lines(phrasesPath).forEach(inputPhrases::add);
            for (String phrase : inputPhrases) {
                phrasesLines.add(phrase.replaceAll("[^a-zA-Z ]", "").trim());
            }

            splitPhraseInWords(namesLines, namesByWordsMap);
            splitPhraseInWords(phrasesLines, phrasesByWordsMap);

        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void splitPhraseInWords(List<String> sourceCollection, Map<Integer, List<String>> phraseSplitByWordsMap) {
        int numberOfString = 0;
        for (String str : sourceCollection) {
            phraseSplitByWordsMap.put(numberOfString++, App.splitPhrase(str));
        }
    }

    private static List<String> splitPhrase(String phrase) {
        return Arrays.asList(phrase.split(" "));
    }
    /**
     * @return the edit distance between two words, from 0.0 to 1.0.
     *
     * The more words are similar, the closer ratio to 1.0
     * The more edits are requred to make words similar, the closer ratio is to 0.0
     * */
    private static Double jaroWinklerDistanceRatio(String uniqueName, String word) {
        return new JaroWinklerDistance().apply(uniqueName, word);
    }

    /**
     * @return number of common letters from 0 to 4.
     *
     * The more words sound the same, the higher sound index.
     * */
    private static int soundexCommonLetters(String word, String name) {
        int soundDifference = 0;
        try {
            soundDifference = Soundex.US_ENGLISH.difference(word, name);
        } catch (EncoderException e) {
            e.printStackTrace();
        }
        return soundDifference;
    }

    /**
     * @return map of word with possible mistake and matching name from the given list
     * */
    private static Map<String, String> findMatches() {
        Map<String, String> mapOfMatchingNames = new HashMap<>();

        for (Integer phraseNumber : phrasesByWordsMap.keySet() ) {
            List<String> wordsInPhrase = phrasesByWordsMap.get(phraseNumber);

            for (String word : wordsInPhrase) {
                if (Character.isUpperCase(word.charAt(0)) && !word.equals("I")) {
                    Map<String, Double> nameWithMatchingCoefficientMap = new HashMap<>();

                    for (Integer nameNumber : namesByWordsMap.keySet()) {
                        List<String> wordsInName = namesByWordsMap.get(nameNumber);

                        for (String name : wordsInName) {
                            nameWithMatchingCoefficientMap.put(name, 0.0D);

                            int numberWordsInName = namesByWordsMap.get(nameNumber).size();
                            double coincidenceRatio;

                            if (numberWordsInName > 1) {
                                double wholeNameCoincidenceRatio = 0.0D;

                                for (int positionOfWordInName = 0; positionOfWordInName < numberWordsInName; positionOfWordInName++) {
                                    String namePart = wordsInName.get(positionOfWordInName);
                                    String wordPart;

                                    if (wordsInPhrase.indexOf(word) < numberWordsInName) {
                                        wordPart = wordsInPhrase.get(positionOfWordInName);
                                    } else {
                                        wordPart = wordsInPhrase.get(wordsInPhrase.indexOf(word) - numberWordsInName + positionOfWordInName);
                                    }
                                    wholeNameCoincidenceRatio += jaroWinklerDistanceRatio(namePart, wordPart);
                                }

                                coincidenceRatio = (wholeNameCoincidenceRatio / numberWordsInName);
                            } else {
                                coincidenceRatio = jaroWinklerDistanceRatio(name, word);
                            }

                            if (nameWithMatchingCoefficientMap.get(name) < coincidenceRatio) {
                                nameWithMatchingCoefficientMap.put(name, coincidenceRatio);
                            }
                        }
                    }

                    Double maxCoefficient = nameWithMatchingCoefficientMap.values().stream().max(Comparator.comparing(Double::valueOf)).orElse(0.0D);
                    for (String name : nameWithMatchingCoefficientMap.keySet()) {
                        if (Objects.equals(nameWithMatchingCoefficientMap.get(name), maxCoefficient)) {
                            if ( soundexCommonLetters(word,name) > 2 && jaroWinklerDistanceRatio(word, name) > 0.5 ) {
                                mapOfMatchingNames.put(word, name);
                            }
                        }
                    }
                }
            }
        }
        return mapOfMatchingNames;
    }

    /**
     * Print results on screen
     * */
    private static void printResults() {
        System.out.println("We got text on input:");
        for (String phrase : inputPhrases) {
            System.out.println(phrase);
        }
        System.out.println("\nAnd list of correct names:");
        for (String name : inputNames){
            System.out.println(name);
        }

        System.out.println("\nWe've found possible names with mistakes:");
        for (String key:possibleMatches.keySet()) {
            System.out.println(key + " - " + possibleMatches.get(key));
        }
    }
}