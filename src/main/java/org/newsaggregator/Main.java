package org.newsaggregator;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class Main {
    private static <T> T loadFromFile(String path, Supplier<T> factory, BiConsumer<T, String> adder) {
        T collection = factory.get();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine();

            String line;
            while ((line = br.readLine()) != null) {
                String trimmedLine = line.trim();

                if (!trimmedLine.isEmpty()) {
                    adder.accept(collection, trimmedLine);
                }
            }
        } catch (IOException e) {
            System.err.println("Eroare la citirea: " + path);
            e.printStackTrace();
            return null;
        }

        return collection;
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Incorrect number of args.");
            return;
        }

        int numberOfThreads;

        try {
            numberOfThreads = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number of threads.");
            return;
        }

        String pathToArticles = args[1];
        String pathToAdditionalFile = args[2];

        ConcurrentLinkedQueue<String> fileQueue = loadFromFile(
                pathToArticles,
                ConcurrentLinkedQueue::new,
                ConcurrentLinkedQueue::add
        );

        if (fileQueue == null) return;

        List<String> configPaths = loadFromFile(
                pathToAdditionalFile,
                ArrayList::new,
                List::add
        );

        if (configPaths == null || configPaths.size() < 3) return;

        Map<String, Boolean> languagesMap = loadFromFile(
                configPaths.get(0),
                HashMap::new,
                (map, line) -> map.put(line, true)
        );

        Map<String, Boolean> categoriesMap = loadFromFile(
                configPaths.get(1),
                HashMap::new,
                (map, line) -> map.put(line, true)
        );

        Map<String, Boolean> englishWordsMap = loadFromFile(
                configPaths.get(2),
                HashMap::new,
                (map, line) -> map.put(line, true)
        );

        if (languagesMap == null || categoriesMap == null || englishWordsMap == null) {
            System.out.println("Unul sau mai multe fisiere de configurare nu au putut fi citite.");
            return;
        }

        Map<String, Boolean> permittedLanguages = Collections.unmodifiableMap(languagesMap);
        Map<String, Boolean> interestCategories = Collections.unmodifiableMap(categoriesMap);
        Map<String, Boolean> englishWords = Collections.unmodifiableMap(englishWordsMap);

        System.out.println("All read.");

        while (!fileQueue.isEmpty()) {
            System.out.println(fileQueue.peek());
            fileQueue.poll();
        }

        NewsAggregator news = new NewsAggregator(numberOfThreads, fileQueue, permittedLanguages, interestCategories, englishWords);
    }
}