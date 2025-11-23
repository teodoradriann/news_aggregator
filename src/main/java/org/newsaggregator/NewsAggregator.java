package org.newsaggregator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class NewsAggregator {
    int numberOfThreads;
    int numberOfJSONFiles;
    private final List<Thread> threads;
    private final ConcurrentLinkedQueue<String> fileQueue;

    Map<String, Boolean> permittedLanguages;
    Map<String, Boolean> interestCategories;
    Map<String, Boolean> englishWords;

    public NewsAggregator(int numberOfThreads,
                          ConcurrentLinkedQueue<String> fileQueue,
                          Map<String, Boolean> permittedLanguages,
                          Map<String, Boolean> interestCategories,
                          Map<String, Boolean> englishWords) {

        this.numberOfThreads = numberOfThreads;
        this.threads = new ArrayList<>(numberOfThreads);
        this.fileQueue = fileQueue;
        this.permittedLanguages = permittedLanguages;
        this.interestCategories = interestCategories;
        this.englishWords = englishWords;
    }

    AtomicInteger readArticles = new AtomicInteger(0);
    AtomicInteger duplicateArticles = new AtomicInteger(0);
    Set<Article> uniqueArticles = Collections.newSetFromMap(new ConcurrentHashMap<>());
}
