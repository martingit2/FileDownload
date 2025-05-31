// UrlUtil.java
package main.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hjelpeklasse for URL-relaterte operasjoner.
 */
public class UrlUtil {

    private UrlUtil() {} // Privat konstruktør for hjelpeklasse

    public static String getFileExtensionFromUrl(String urlString) {
        if (urlString == null || urlString.isEmpty()) return null;
        try {
            String pathPart = urlString.split("\\?")[0].split("#")[0];
            try {
                URL tempUrl = new URL(urlString);
                pathPart = tempUrl.getPath();
                if (pathPart == null || pathPart.isEmpty() || pathPart.endsWith("/")) {
                    pathPart = urlString.split("\\?")[0].split("#")[0];
                }
            } catch (MalformedURLException ignored) { /* Ignorer */ }

            if (pathPart != null && !pathPart.isEmpty() && !pathPart.endsWith("/")) {
                String fileNameCandidate = pathPart.substring(pathPart.lastIndexOf('/') + 1);
                if (fileNameCandidate.contains(".")) {
                    String ext = fileNameCandidate.substring(fileNameCandidate.lastIndexOf('.')).toLowerCase();
                    if (ext.length() > 1 && ext.length() <= 6 && ext.matches("\\.[a-z0-9]+")) {
                        return ext;
                    }
                }
            }
            if (urlString.contains("?")) {
                String query = urlString.substring(urlString.indexOf('?') + 1);
                Pattern pattern = Pattern.compile("(?:type|format|ext)=([^&]+)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(query);
                if (matcher.find()) {
                    String format = matcher.group(1).toLowerCase().replaceAll("[^a-z0-9].*", "");
                    if (format.length() > 0 && format.length() <= 5 && format.matches("[a-z0-9]+")) {
                        return "." + format;
                    }
                }
            }
        } catch (Exception e) { /* Logg feil her om ønskelig */ }
        return null;
    }
}