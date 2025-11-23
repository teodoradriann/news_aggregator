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
    private Map<String, Boolean> permittedLanguages;
    private Map<String, Boolean> interestCategories;
    private Map<String, Boolean> englishWords;

    public NewsAggregator(int numberOfThreads,
                          ConcurrentLinkedQueue<String> fileQueue,
                          Map<String, Boolean> permittedLanguages,
                          Map<String, Boolean> interestCategories,
                          Map<String, Boolean> englishWords) {

        this.numberOfThreads = numberOfThreads;
        this.threads = new ArrayList<>();
        this.fileQueue = fileQueue;
        this.permittedLanguages = permittedLanguages;
        this.interestCategories = interestCategories;
        this.englishWords = englishWords;

        for (int i = 0; i < this.numberOfThreads; i++) {
            Thread thrd = new Task(i);
            this.threads.add(thrd);
        }
    }

    public AtomicInteger getReadArticles() {
        return readArticles;
    }

    private AtomicInteger readArticles = new AtomicInteger(0);
    private AtomicInteger duplicateArticles = new AtomicInteger(0);
    private Set<Article> uniqueArticles = Collections.newSetFromMap(new ConcurrentHashMap<>());

    void startThreads() {
        for (Thread thread: this.threads) {
            thread.start();
        }

        for (Thread thread : this.threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private class Task extends Thread {
        int id;

        public Task(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            String path;
            while ((path = fileQueue.poll()) != null) {
                System.out.println("Thread-ul " + this.getName() + " a luat " + path);
                readArticles.incrementAndGet();
            }
        }
    }
}
