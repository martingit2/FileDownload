// FileCategory.java
package main.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Håndterer definisjoner av filkategorier og deres tilknyttede filtyper.
 */
public class FileCategory {

    public static final Map<String, List<String>> CATEGORIZED_EXTENSIONS = new LinkedHashMap<>();
    public static final String ALL_FILES_CATEGORY = "Alle filtyper";

    static {
        CATEGORIZED_EXTENSIONS.put("Bilder", Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".bmp", ".tiff", ".ico"));
        CATEGORIZED_EXTENSIONS.put("Dokumenter", Arrays.asList(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".rtf", ".odt", ".csv", ".pages", ".numbers", ".key"));
        CATEGORIZED_EXTENSIONS.put("Videoer", Arrays.asList(".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv", ".wmv", ".mpeg", ".mpg"));
        CATEGORIZED_EXTENSIONS.put("Lydfiler", Arrays.asList(".mp3", ".wav", ".ogg", ".aac", ".flac", ".m4a", ".wma"));
        CATEGORIZED_EXTENSIONS.put("Arkiver", Arrays.asList(".zip", ".rar", ".tar", ".gz", ".7z", ".bz2", ".xz"));
        CATEGORIZED_EXTENSIONS.put("Kildekode/Tekst", Arrays.asList(".java", ".js", ".html", ".css", ".xml", ".json", ".py", ".c", ".cpp", ".h", ".cs", ".php", ".rb", ".sql", ".md"));
    }

    private FileCategory() {}

    public static String categorizeExtension(String ext) {
        if (ext == null || ext.isEmpty()) return "Ukjent";
        String lowerExt = ext.toLowerCase();
        for (Map.Entry<String, List<String>> entry : CATEGORIZED_EXTENSIONS.entrySet()) {
            if (entry.getValue().contains(lowerExt)) {
                return entry.getKey();
            }
        }
        return "Annet";
    }
}