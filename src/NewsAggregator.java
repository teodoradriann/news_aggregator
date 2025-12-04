import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
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

    private final AtomicInteger readArticles = new AtomicInteger(0);

    private final ConcurrentHashMap<String, Integer> seenUUID = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> seenTitle = new ConcurrentHashMap<>();

    public final List<Article> uniqueArticles = Collections.synchronizedList(new ArrayList<>());

    // private final ConcurrentLinkedQueue<Article> uniqueArticles = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Article> articles = new ConcurrentLinkedQueue<>();

    private final ConcurrentHashMap<String, List<String>> organizedCategories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> organizedLanguages = new ConcurrentHashMap<>();


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

        private void readArticles(JsonNode root) {
            Article article;
            if (root.isArray()) {
                for (JsonNode articleNode : root) {
                    String uuid = articleNode.get("uuid").asText();
                    String title = articleNode.get("title").asText();

                    boolean okTitle = false;
                    boolean okUUID = false;

                    Set<String> listCategories = new HashSet<>();
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
        }

        private void waitAtBarrier() {
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
                return;
            }
        }

        // procesarea articolelor in paralel
        private void process(int id) {
            int size = uniqueArticles.size();
            int N = numberOfThreads;

            int start = size * id / N;
            int end = size * (id + 1) / N;
            end = Math.min(end, size);

            for (int i = start; i < end; i++) {
                Article article = uniqueArticles.get(i);
                String UUID = article.getUuid();

                if (UUID.equals("3d86792936ddca8764bcce85d6c618ad646c4c69")) {
                    System.out.println(article);
                }

                // procesez categoriile
                for (String category: article.getCategories()) {
                    if (interestCategories.get(category).equals(true)) {
                        List<String> uuidList = organizedCategories.computeIfAbsent(
                                category,
                                k -> Collections.synchronizedList(new ArrayList<>())
                        );
                        uuidList.add(UUID);
                    }
                }

                // procesez limbile
                String language = article.getLanguage();
                List<String> uuidList = organizedLanguages.computeIfAbsent(
                        language,
                        k -> Collections.synchronizedList(new ArrayList<>())
                );
                uuidList.add(UUID);
            }
        }

        String parseCategory(String category) {
            return category.replace(",", "").trim().replace(" ", "_");
        }

        private void generateOutputFromDictionary(ConcurrentHashMap<String, List<String>> map) {
            if (id == 0) {
                // generez pt categorii
                for (Map.Entry<String, List<String>> entry: map.entrySet()) {
                    String categoryName = parseCategory(entry.getKey());
                    categoryName += ".txt";
                    try {
                        File newFile = new File(categoryName);
                        if (newFile.createNewFile()) {
                            System.out.println("File created: " + newFile.getName());
                        } else {
                            System.out.println("File already exists.");
                        }
                        List<String> uuidList = entry.getValue();
                        Collections.sort(uuidList);

                        try (PrintWriter writer = new PrintWriter(newFile)) {
                            for (String uuid : uuidList) {
                                writer.println(uuid);
                            }
                        } } catch (IOException e) {
                            e.printStackTrace();
                    }
                }
            }
        }


        @Override
        public void run() {
            String path;
            Article article;
            while ((path = fileQueue.poll()) != null) {
                try {
                    JsonNode rootNode = objMapper.readTree(new File(path));
                    readArticles(rootNode);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

            // astept ca toate threadurile sa fi terminat de citit articolele pentru ca mai apoi
            // sa incep filtrarea lor
            waitAtBarrier();

            while ((article = articles.poll()) != null) {
                boolean uniqueness = (seenUUID.get(article.getUuid()) == 1 && seenTitle.get(article.getTitle()) == 1);

                if (uniqueness) {
                    uniqueArticles.add(article);
                }
            }

            waitAtBarrier();
            // trec prin articole si incep sa le sortez si sa le adaug in maps

            process(id);
            waitAtBarrier();
            generateOutputFromDictionary(organizedCategories);
            generateOutputFromDictionary(organizedLanguages);
        }
    }
}
