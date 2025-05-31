// FileInfo.java
package main.model;

/**
 * Representerer en fil som er funnet på en nettside.
 */
public class FileInfo {
    private String url;
    private String extension;
    private String detectedType;
    private boolean selected = true;

    public FileInfo(String url, String extension) {
        this.url = url;
        this.extension = (extension != null) ? extension.toLowerCase() : "";
        // Bruker FileCategory direkte her, siden den nå er i samme pakke (eller importert hvis i annen).
        this.detectedType = FileCategory.categorizeExtension(this.extension);
    }

    public String getUrl() { return url; }
    public String getExtension() { return extension; }
    public String getDetectedType() { return detectedType; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    @Override
    public String toString() {
        return "FileInfo{url='" + url + "', ext='" + extension + "', type='" + detectedType + "', sel=" + selected + '}';
    }
}