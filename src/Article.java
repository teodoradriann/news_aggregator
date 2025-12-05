import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Article implements Comparable<Article> {

    private String uuid;
    private String title;
    private String author;
    private String url;
    private String text;
    private String published;
    private String language;
    private Set<String> categories;

    public Article(
        String uuid,
        String title,
        String author,
        String url,
        String text,
        String published,
        String language,
        Set<String> categories
    ) {
        this.uuid = uuid;
        this.title = title;
        this.author = author;
        this.url = url;
        this.text = text;
        this.published = published;
        this.language = language;
        this.categories = categories;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getPublished() {
        return published;
    }

    public void setPublished(String published) {
        this.published = published;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Set<String> getCategories() { return categories; }

    @Override
    public String toString() {
        return "Article {" +
                "\n  uuid='" + uuid + '\'' +
                ",\n  title='" + title + '\'' +
                ",\n  author='" + author + '\'' +
                ",\n  url='" + url + '\'' +
                ",\n  published='" + published + '\'' +
                ",\n  language='" + language + '\'' +
                ",\n  categories=" + categories +
                ",\n  text='" + (text != null && text.length() > 50 ? text.substring(0, 50) + "..." : text) + '\'' +
                "\n}";
    }

    @Override
    public int compareTo(Article other) {
        int dateComparison = other.published.compareTo(this.getPublished());

        if (dateComparison == 0) {
            return this.uuid.compareTo(other.getUuid());
        }

        return dateComparison;
    }
}
