package org.newsaggregator;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Main {
    private static HashMap<String, Boolean> readConstrainsAndPopulate(String path) {
        HashMap<String, Boolean> map = new HashMap<>();

        try(BufferedReader bufferedReader = new BufferedReader(new FileReader(path))) {
            // ignor numarul N
            bufferedReader.readLine();

            String line = bufferedReader.readLine();
            while (line != null) {
                map.put(line.trim(), true);
                line = bufferedReader.readLine();
            }
        } catch (FileNotFoundException e) {
            System.out.println("File " + path + " not found");
            return null;
        } catch (IOException e) {
            return null;
        }

        return map;
    }
    private static List<String> readLinesFromFile(String path) {
        List<String> lines = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine();

            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Eroare la citirea fisierului: " + path);
            e.printStackTrace();
            return null;
        }

        return lines;
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

        List<String> jsonPaths = readLinesFromFile(pathToArticles);
        if (jsonPaths == null) {
            return;
        }

        ConcurrentLinkedQueue<String> fileQueue = new ConcurrentLinkedQueue<>(jsonPaths);

        List<String> configPaths = readLinesFromFile(pathToAdditionalFile);

        if (configPaths == null) {
            return;
        }

        if (configPaths.size() < 3) {
            System.out.println("Fisierul de config nu contine toate cele 3 cai!");
            return;
        }

        Map<String, Boolean> languagesMap = readConstrainsAndPopulate(configPaths.get(0));
        Map<String, Boolean> categoriesMap = readConstrainsAndPopulate(configPaths.get(1));
        Map<String, Boolean> englishWordsMap = readConstrainsAndPopulate(configPaths.get(2));

        if (languagesMap == null || categoriesMap == null || englishWordsMap == null) {
            System.out.println("Unul sau mai multe fisiere de configurare nu au putut fi citite.");
            return;
        }

        Map<String, Boolean> permittedLanguages = Collections.unmodifiableMap(languagesMap);
        Map<String, Boolean> interestCategories = Collections.unmodifiableMap(categoriesMap);
        Map<String, Boolean> englishWords = Collections.unmodifiableMap(englishWordsMap);

        System.out.println("All read.");

        NewsAggregator news = new NewsAggregator(numberOfThreads, fileQueue, permittedLanguages, interestCategories, englishWords);
    }
}