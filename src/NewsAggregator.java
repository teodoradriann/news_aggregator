import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class NewsAggregator {

    int numberOfThreads;
    int numberOfJSONFiles;
    private final List<Thread> threads;
    private final ConcurrentLinkedQueue<String> fileQueue;
    private Map<String, Boolean> permittedLanguages;
    private Map<String, Boolean> interestCategories;
    private Map<String, Boolean> englishWords;
    private CyclicBarrier barrier;

    ObjectMapper objMapper = new ObjectMapper();

    public NewsAggregator(
        int numberOfThreads,
        ConcurrentLinkedQueue<String> fileQueue,
        Map<String, Boolean> permittedLanguages,
        Map<String, Boolean> interestCategories,
        Map<String, Boolean> englishWords
    ) {
        this.numberOfThreads = numberOfThreads;
        this.threads = new ArrayList<>();
        this.fileQueue = fileQueue;
        this.permittedLanguages = permittedLanguages;
        this.interestCategories = interestCategories;
        this.englishWords = englishWords;
        this.barrier = new CyclicBarrier(this.numberOfThreads);

        for (int i = 0; i < this.numberOfThreads; i++) {
            Thread thrd = new Task(i);
            this.threads.add(thrd);
        }
    }

    public AtomicInteger getReadArticles() {
        return readArticles;
    }

    public Integer getNumberOfUniqueArticles() {
        return uniqueArticles.size();
    }

    private AtomicInteger readArticles = new AtomicInteger(0);

    ConcurrentHashMap<String, Integer> seenUUID = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Integer> seenTitle = new ConcurrentHashMap<>();

    public final List<Article> uniqueArticles = Collections.synchronizedList(new ArrayList<>());

    public ConcurrentLinkedQueue<Article> articles = new ConcurrentLinkedQueue<>();


    void startThreads() {
        for (Thread thread : this.threads) {
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
            Article article;
            while ((path = fileQueue.poll()) != null) {
                try {
                    JsonNode rootNode = objMapper.readTree(new File(path));
                    if (rootNode.isArray()) {
                        for (JsonNode articleNode : rootNode) {
                            String uuid = articleNode.get("uuid").asText();
                            String title = articleNode.get("title").asText();

                            boolean okTitle = false;
                            boolean okUUID = false;

                            List<String> listCategories = new ArrayList<>();
                            JsonNode catNode = articleNode.get("categories");

                            if (catNode != null && catNode.isArray()) {
                                for (JsonNode objNode : catNode) {
                                    listCategories.add(objNode.asText());
                                }
                            }

                            article = new Article(
                                    uuid,
                                    title,
                                    articleNode.get("author").asText(),
                                    articleNode.get("url").asText(),
                                    articleNode.get("text").asText(),
                                    articleNode.get("published").asText(),
                                    articleNode.get("language").asText(),
                                    listCategories
                            );

                            // daca deja le-am vazut nu are rost sa le mai adaug in articles.
                            // dar o sa le incrementez valoarea la 2
                            if (seenTitle.putIfAbsent(title, 1) != null) {
                                seenTitle.replace(title, 2);
                            } else {
                                okTitle = true;
                            }

                            if (seenUUID.putIfAbsent(uuid, 1) != null) {
                                seenUUID.replace(uuid, 2);
                            } else {
                                okUUID = true;
                            }


                            if (okTitle && okUUID) {
                                articles.add(article);
                            }

                            readArticles.incrementAndGet();
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
                return;
            }

            while ((article = articles.poll()) != null) {
                boolean uniqueness = (seenUUID.get(article.getUuid()) == 1 && seenTitle.get(article.getTitle()) == 1);

                if (uniqueness) {
                    uniqueArticles.add(article);
                }
            }
        }
    }
}
