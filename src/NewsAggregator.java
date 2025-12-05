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
    Article mostRecentArticle;

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

    private final ConcurrentHashMap<String, AtomicInteger> englishWordsCount = new ConcurrentHashMap<>();


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
        private void process() {
            int size = uniqueArticles.size();
            int N = numberOfThreads;

            int start = size * id / N;
            int end = size * (id + 1) / N;
            end = Math.min(end, size);

            for (int i = start; i < end; i++) {
                Article article = uniqueArticles.get(i);
                String UUID = article.getUuid();

                // procesez categoriile
                for (String category: article.getCategories()) {
                    if (interestCategories.containsKey(category)) {
                        List<String> uuidList = organizedCategories.computeIfAbsent(
                                category,
                                k -> Collections.synchronizedList(new ArrayList<>())
                        );
                        uuidList.add(UUID);
                    }
                }

                // procesez limbile
                String language = article.getLanguage();
                if (permittedLanguages.containsKey(language)) {
                    List<String> uuidList = organizedLanguages.computeIfAbsent(
                            language,
                            k -> Collections.synchronizedList(new ArrayList<>())
                    );
                    uuidList.add(UUID);
                }

                if (language.equals("english")) {
                    String text = article.getText();
                    text = text.toLowerCase();
                    String[] words = text.split("\\s+");
                    Set<String> wordsFoundInThisArticle = new HashSet<>();

                    for (String word: words) {
                        String cleanedWord = word.replaceAll("[^a-z]", "");
                        if (!cleanedWord.isEmpty() && !englishWords.containsKey(cleanedWord)) {
                            if (wordsFoundInThisArticle.add(cleanedWord)) {
                                englishWordsCount.computeIfAbsent(
                                        cleanedWord,
                                        k -> new AtomicInteger(0)
                                ).incrementAndGet();
                            }
                        }
                    }
                }
            }
        }

        String parseCategory(String category) {
            return category.replace(",", "").trim().replace(" ", "_");
        }

        private void generateOutputFromDictionary(ConcurrentHashMap<String, List<String>> map) {
            List<String> keys = new ArrayList<>(map.keySet());
            int size = keys.size();
            int N = numberOfThreads;

            int start = size * id / N;
            int end = Math.min(size * (id + 1) / N, size);

            for (int i = start; i < end; i++) {
                String key = keys.get(i);
                List<String> uuidList = map.get(key);
                String fileName = parseCategory(key) + ".txt";
                Collections.sort(uuidList);
                try {
                    File newFile = new File(fileName);
                    try (PrintWriter writer = new PrintWriter(newFile)) {
                        for (String uuid: uuidList) {
                            writer.println(uuid);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void generateAllArticleOutput() {
            List<Article> allArticles = new ArrayList<>(uniqueArticles);
            Collections.sort(allArticles);
            // obtin cel mai recent articol
            int last_article_index = allArticles.size() - 1;
            while (last_article_index >= 1 && allArticles.get(last_article_index).getPublished().equals(
                    allArticles.get(last_article_index - 1).getPublished())) {
                last_article_index--;
            }

            if (last_article_index >= 0) {
                mostRecentArticle = allArticles.get(last_article_index);
            }

            try {
                File file = new File("all_articles.txt");
                try (PrintWriter writer = new PrintWriter(file)) {
                    for (Article article : allArticles) {
                        writer.println(article.getUuid() + " " + article.getPublished());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void generateWordCounterReport() {
            String fileName = "keywords_count.txt";

            List<Map.Entry<String, AtomicInteger>> sortedList = new ArrayList<>(englishWordsCount.entrySet());
            Collections.sort(sortedList, (entryA, entryB) -> {
                int count1 = entryA.getValue().get();
                int count2 = entryB.getValue().get();

                if (count1 != count2) {
                    return Integer.compare(count2, count1);
                }

                return entryA.getKey().compareTo(entryB.getKey());
            });

            try {
                File file = new File(fileName);
                try (PrintWriter writer = new PrintWriter(file)) {
                    for (Map.Entry<String, AtomicInteger> entry : sortedList) {
                        writer.println(entry.getKey() + " " + entry.getValue().get());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
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

            process();
            waitAtBarrier();
            generateOutputFromDictionary(organizedCategories);
            generateOutputFromDictionary(organizedLanguages);

            // pentru ca trebuie sa sortez o singura lista voi face generarea si sortarea pe 1 singur thread
            if (id == 0) {
                generateAllArticleOutput();
            }

            if (id == numberOfThreads - 1) {
                generateWordCounterReport();
            }


        }
    }
}
