// ImageDownloaderApp.java
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ImageDownloaderApp extends JFrame {

    private JTextField urlField;
    private JComboBox<String> categoryComboBox;
    // private JPanel fileTypeSelectionPanel; // Erstattes av JTable
    private JButton fetchButton, downloadButton, selectAllButton, selectNoneButton;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JFileChooser directoryChooser;
    private transient List<FileInfo> fetchedFilesList; // Liste som holder alle funnede filer
    private JTable filesTable;
    private FilesTableModel tableModel;
    private File saveDirectory;

    private static final Map<String, List<String>> CATEGORIZED_EXTENSIONS = new LinkedHashMap<>();
    static {
        CATEGORIZED_EXTENSIONS.put("Bilder", Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".bmp", ".tiff", ".ico"));
        CATEGORIZED_EXTENSIONS.put("Dokumenter", Arrays.asList(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".rtf", ".odt"));
        CATEGORIZED_EXTENSIONS.put("Videoer", Arrays.asList(".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv", ".wmv"));
        CATEGORIZED_EXTENSIONS.put("Lydfiler", Arrays.asList(".mp3", ".wav", ".ogg", ".aac", ".flac"));
        CATEGORIZED_EXTENSIONS.put("Arkiver", Arrays.asList(".zip", ".rar", ".tar", ".gz", ".7z"));
    }
    private static final String ALL_FILES_CATEGORY = "Alle filtyper";

    // Klasse for filinformasjon, nå med 'selected' flagg for tabellen
    protected static class FileInfo { // Endret til protected for tilgang fra indre klasse TableModel
        String url;
        String extension;
        String detectedType;
        boolean selected = true; // Standard er valgt

        FileInfo(String url, String extension) {
            this.url = url;
            this.extension = extension != null ? extension.toLowerCase() : "";
            this.detectedType = categorizeExtension(this.extension);
        }

        private String categorizeExtension(String ext) {
            if (ext == null || ext.isEmpty()) return "Ukjent";
            for (Map.Entry<String, List<String>> entry : CATEGORIZED_EXTENSIONS.entrySet()) {
                if (entry.getValue().contains(ext)) {
                    return entry.getKey();
                }
            }
            return "Annet";
        }

        @Override
        public String toString() { // Brukes mindre nå, men greit å ha
            return url;
        }
    }

    // TableModel for JTable
    class FilesTableModel extends AbstractTableModel {
        private final List<FileInfo> files;
        private final String[] columnNames = {"Velg", "Fil-URL", "Type", "Filtype (.ext)"};

        public FilesTableModel(List<FileInfo> files) {
            this.files = files;
        }

        @Override
        public int getRowCount() {
            return files.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Boolean.class; // For sjekkboksen
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0; // Kun sjekkbokskolonnen er redigerbar
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            FileInfo fileInfo = files.get(rowIndex);
            switch (columnIndex) {
                case 0: return fileInfo.selected;
                case 1: return fileInfo.url;
                case 2: return fileInfo.detectedType;
                case 3: return fileInfo.extension;
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0 && aValue instanceof Boolean) {
                files.get(rowIndex).selected = (Boolean) aValue;
                fireTableCellUpdated(rowIndex, columnIndex);
                updateDownloadButtonState(); // Oppdater nedlastingsknappens tilstand
            }
        }

        public void selectAll(boolean select) {
            for (FileInfo file : files) {
                file.selected = select;
            }
            fireTableDataChanged();
            updateDownloadButtonState();
        }

        public void setData(List<FileInfo> newFiles) {
            this.files.clear();
            this.files.addAll(newFiles);
            filterAndSelectBasedOnCategory(); // Velg basert på kategori etter at data er satt
            fireTableDataChanged();
            updateDownloadButtonState();
        }

        // Filtrer og velg basert på kategori
        public void filterAndSelectBasedOnCategory() {
            String selectedCategory = (String) categoryComboBox.getSelectedItem();
            if (selectedCategory == null || files.isEmpty()) return;

            List<String> extensionsInCurrentCategory = null;
            if (!ALL_FILES_CATEGORY.equals(selectedCategory)) {
                extensionsInCurrentCategory = CATEGORIZED_EXTENSIONS.get(selectedCategory);
            }

            for (FileInfo file : files) {
                if (ALL_FILES_CATEGORY.equals(selectedCategory)) {
                    file.selected = true; // Velg alle hvis "Alle filtyper"
                } else if (extensionsInCurrentCategory != null) {
                    file.selected = extensionsInCurrentCategory.contains(file.extension);
                } else {
                    file.selected = false; // Hvis kategorien ikke finnes eller ingen filtyper for den
                }
            }
            fireTableDataChanged(); // Oppdater tabellvisningen
            updateDownloadButtonState();
        }
    }


    public ImageDownloaderApp() {
        setTitle("Avansert Filnedlaster Pro");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 750);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Kunne ikke sette system look and feel: " + e.getMessage());
        }

        fetchedFilesList = new ArrayList<>(); // Initialiser listen som table model vil bruke
        tableModel = new FilesTableModel(fetchedFilesList); // Gi referansen til listen
        initComponents();
    }

    private void initComponents() {
        // ----- Toppanel: URL-input og Kategori -----
        JPanel topInputPanel = new JPanel(new BorderLayout(10, 5));
        topInputPanel.setBorder(new EmptyBorder(10, 10, 5, 10));

        String placeholderText = "Skriv inn URL her...";
        urlField = new JTextField(placeholderText);
        urlField.setForeground(Color.GRAY);
        urlField.addFocusListener(new FocusAdapter() { /* ... (som før) ... */
            @Override
            public void focusGained(FocusEvent e) {
                if (urlField.getText().equals(placeholderText)) {
                    urlField.setText("");
                    urlField.setForeground(Color.BLACK);
                }
            }
            @Override
            public void focusLost(FocusEvent e) {
                if (urlField.getText().isEmpty()) {
                    urlField.setForeground(Color.GRAY);
                    urlField.setText(placeholderText);
                }
            }
        });
        urlField.setToolTipText("Nettsideadressen du vil hente filer fra");
        topInputPanel.add(urlField, BorderLayout.CENTER);

        JPanel categoryAndFetchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5,0));
        categoryComboBox = new JComboBox<>(CATEGORIZED_EXTENSIONS.keySet().toArray(new String[0]));
        categoryComboBox.addItem(ALL_FILES_CATEGORY);
        categoryComboBox.setSelectedItem("Bilder");
        categoryComboBox.setToolTipText("Velg filkategori for forhåndsfiltrering");
        categoryComboBox.addActionListener(e -> {
            tableModel.filterAndSelectBasedOnCategory(); // Oppdater valg i tabellen
            updateButtonTexts(); // Oppdater knappetekster
        });
        categoryAndFetchPanel.add(categoryComboBox);

        fetchButton = new JButton("Analyser URL");
        fetchButton.setToolTipText("Hent og vis fillenker fra URL");
        fetchButton.addActionListener(this::fetchFileLinksAction);
        categoryAndFetchPanel.add(fetchButton);

        topInputPanel.add(categoryAndFetchPanel, BorderLayout.EAST);
        add(topInputPanel, BorderLayout.NORTH);

        // ----- Senterpanel: Fil-tabell og velg-knapper -----
        JPanel centerPanel = new JPanel(new BorderLayout(10,10));
        centerPanel.setBorder(new EmptyBorder(0, 10, 0, 10));

        filesTable = new JTable(tableModel);
        filesTable.setAutoCreateRowSorter(true); // Aktiver sortering på kolonner
        setupTableColumns();

        JScrollPane tableScrollPane = new JScrollPane(filesTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Funnet Filer"));
        centerPanel.add(tableScrollPane, BorderLayout.CENTER);

        JPanel selectionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectAllButton = new JButton("Velg Alle");
        selectAllButton.addActionListener(e -> tableModel.selectAll(true));
        selectAllButton.setEnabled(false);
        selectionButtonsPanel.add(selectAllButton);

        selectNoneButton = new JButton("Velg Ingen");
        selectNoneButton.addActionListener(e -> tableModel.selectAll(false));
        selectNoneButton.setEnabled(false);
        selectionButtonsPanel.add(selectNoneButton);

        centerPanel.add(selectionButtonsPanel, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);


        // ----- Bunnpanel: Logg, Nedlastingsknapp og Progressbar -----
        JPanel bottomOuterPanel = new JPanel(new BorderLayout(10,5));
        bottomOuterPanel.setBorder(new EmptyBorder(5,10,10,10));

        // Loggpanel (mindre)
        logArea = new JTextArea(8, 0); // Høyde på 8 rader
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        DefaultCaret caret = (DefaultCaret)logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Logg"));
        bottomOuterPanel.add(logScrollPane, BorderLayout.CENTER);

        // Panel for nedlastingselementer
        JPanel downloadControlsPanel = new JPanel(new BorderLayout(10,5));

        downloadButton = new JButton("Last Ned Valgte Filer");
        downloadButton.setEnabled(false);
        downloadButton.setToolTipText("Start nedlasting av valgte filer");
        downloadButton.addActionListener(this::downloadFilesAction);
        downloadControlsPanel.add(downloadButton, BorderLayout.EAST);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        downloadControlsPanel.add(progressBar, BorderLayout.CENTER);

        bottomOuterPanel.add(downloadControlsPanel, BorderLayout.SOUTH);
        add(bottomOuterPanel, BorderLayout.SOUTH);


        directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        directoryChooser.setDialogTitle("Velg Mappe for Nedlasting");

        updateButtonTexts(); // Initialiser knappetekster
        updateDownloadButtonState(); // Initialiser nedlastingsknappens tilstand
    }

    private void setupTableColumns() {
        // Sett bredde for sjekkbokskolonnen
        TableColumn selectColumn = filesTable.getColumnModel().getColumn(0);
        selectColumn.setPreferredWidth(50);
        selectColumn.setMaxWidth(70);
        selectColumn.setMinWidth(40);

        // Gjør URL-kolonnen bredere
        TableColumn urlColumn = filesTable.getColumnModel().getColumn(1);
        urlColumn.setPreferredWidth(450);

        TableColumn typeColumn = filesTable.getColumnModel().getColumn(2);
        typeColumn.setPreferredWidth(100);

        TableColumn extColumn = filesTable.getColumnModel().getColumn(3);
        extColumn.setPreferredWidth(100);

        // Custom renderer for å sentrere sjekkboksen (valgfritt, men penere)
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        // filesTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer); // Overstyrer default for Boolean
    }

    private void updateDownloadButtonState() {
        boolean anySelected = false;
        for (FileInfo fi : fetchedFilesList) {
            if (fi.selected) {
                anySelected = true;
                break;
            }
        }
        downloadButton.setEnabled(anySelected && !fetchedFilesList.isEmpty());
        selectAllButton.setEnabled(!fetchedFilesList.isEmpty());
        selectNoneButton.setEnabled(!fetchedFilesList.isEmpty());
    }


    private void updateButtonTexts() {
        String selectedCategory = (String) categoryComboBox.getSelectedItem();
        if (selectedCategory == null) selectedCategory = "Filer";

        fetchButton.setText("Analyser URL"); // Endret fast tekst

        if (ALL_FILES_CATEGORY.equals(selectedCategory)) {
            downloadButton.setText("Last Ned Valgte");
        } else {
            String categoryDisplay = selectedCategory.substring(0, 1).toUpperCase() + selectedCategory.substring(1).toLowerCase();
            // Forenklet logikk for knappetekst, da tabellen nå styrer utvalget.
            downloadButton.setText("Last Ned Valgte");
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    private String getFileExtensionFromUrl(String urlString) {
        // ... (samme som før)
        try {
            URL url = new URL(urlString);
            String path = url.getPath();

            if (path != null && !path.isEmpty()) {
                String fileNameCandidate = path.substring(path.lastIndexOf('/') + 1);
                if (fileNameCandidate.contains(".")) {
                    String ext = fileNameCandidate.substring(fileNameCandidate.lastIndexOf('.')).toLowerCase();
                    if (ext.length() > 1 && ext.length() <= 6 && ext.matches("\\.[a-z0-9]+")) {
                        return ext;
                    }
                }
            }
            String query = url.getQuery();
            if (query != null) {
                Pattern pattern = Pattern.compile("[?&]format=([^&]+)");
                Matcher matcher = pattern.matcher(query);
                if (matcher.find()) {
                    String format = matcher.group(1).toLowerCase();
                    if (format.length() > 0 && format.length() <= 5 && format.matches("[a-z0-9]+")) {
                        return "." + format;
                    }
                }
            }
            String urlStrNoQuery = urlString.split("\\?")[0];
            if (urlStrNoQuery.contains(".")) {
                String potentialExt = urlStrNoQuery.substring(urlStrNoQuery.lastIndexOf('.')).toLowerCase();
                if (potentialExt.length() > 1 && potentialExt.length() <= 6 && potentialExt.matches("\\.[a-z0-9]+")) {
                    boolean isKnown = CATEGORIZED_EXTENSIONS.values().stream().anyMatch(list -> list.contains(potentialExt));
                    if(isKnown) return potentialExt;
                }
            }
        } catch (MalformedURLException e) {
            if (urlString.contains(".")) {
                String potentialExt = urlString.substring(urlString.lastIndexOf('.')).toLowerCase();
                if (potentialExt.length() > 1 && potentialExt.length() <= 6 && potentialExt.matches("\\.[a-z0-9]+")) {
                    boolean isKnown = CATEGORIZED_EXTENSIONS.values().stream().anyMatch(list -> list.contains(potentialExt));
                    if(isKnown) return potentialExt;
                }
            }
        }
        return null;
    }

    private void fetchFileLinksAction(ActionEvent e) {
        String urlText = urlField.getText().trim();
        if (urlText.isEmpty() || urlText.equals("Skriv inn URL her...")) {
            JOptionPane.showMessageDialog(this, "URL kan ikke være tom.", "Feil", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            new URL(urlText);
        } catch (MalformedURLException ex) {
            JOptionPane.showMessageDialog(this, "Ugyldig URL-format: " + ex.getMessage(), "Feil", JOptionPane.ERROR_MESSAGE);
            return;
        }

        log("Starter analyse av URL: " + urlText);
        fetchButton.setEnabled(false);
        downloadButton.setEnabled(false); // Deaktiveres mens vi henter nytt
        selectAllButton.setEnabled(false);
        selectNoneButton.setEnabled(false);
        progressBar.setValue(0);
        // fetchedFilesList blir oppdatert via tableModel.setData() i SwingWorker's done()

        SwingWorker<List<FileInfo>, String> worker = new SwingWorker<>() {
            @Override
            protected List<FileInfo> doInBackground() throws Exception {
                List<FileInfo> foundOnPage = new ArrayList<>(); // Midlertidig liste for denne hentingen
                Set<String> uniqueFileUrls = new HashSet<>();

                publish("Kobler til " + urlText + "...");
                Document doc = Jsoup.connect(urlText)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .timeout(15000)
                        .get();
                publish("Koblet til. Analyserer HTML...");

                Elements links = doc.select("img[src], source[srcset], a[href], video[src], audio[src], track[src], embed[src], object[data], link[href]");
                // La til link[href] for å fange f.eks. CSS eller fonter om ønskelig.

                for (Element link : links) {
                    String url = "";
                    String tagName = link.tagName().toLowerCase();

                    if (link.hasAttr("src")) url = link.absUrl("src");
                    else if (link.hasAttr("srcset")) url = link.absUrl("srcset").split(",")[0].trim().split("\\s+")[0];
                    else if (link.hasAttr("href")) url = link.absUrl("href");
                    else if (link.hasAttr("data")) url = link.absUrl("data");

                    if (url.isEmpty() || url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("javascript:")) continue;
                    if (url.contains("#")) {
                        url = url.substring(0, url.indexOf('#'));
                    }

                    String extension = getFileExtensionFromUrl(url);

                    // For <link rel="stylesheet" href="style.css">, er href="style.css"
                    // For <link rel="icon" href="favicon.ico">
                    // Vurder om alle `link[href]` skal med, eller kun de med kjente filtyper.
                    boolean потенциальноФайл = (extension != null && !extension.isEmpty());
                    if ("link".equals(tagName)) {
                        String rel = link.attr("rel");
                        // Bare ta med link-elementer som sannsynligvis er filer (f.eks. stylesheet, icon)
                        if (!("stylesheet".equalsIgnoreCase(rel) || "icon".equalsIgnoreCase(rel) || "shortcut icon".equalsIgnoreCase(rel) || "apple-touch-icon".equalsIgnoreCase(rel) || "preload".equalsIgnoreCase(rel) || "manifest".equalsIgnoreCase(rel))) {
                            if(!потенциальноФайл) continue; // Hopp over andre link-typer med mindre de har en klar filtype
                        }
                    }


                    if (потенциальноФайл && uniqueFileUrls.add(url)) {
                        FileInfo fileInfo = new FileInfo(url, extension);
                        foundOnPage.add(fileInfo);
                        publish("Fant: " + url);
                    }
                }
                return foundOnPage;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    log(message);
                }
            }

            @Override
            protected void done() {
                try {
                    List<FileInfo> result = get();
                    tableModel.setData(result); // Oppdater tabellen med de nye filene
                    log("Analyse fullført. Fant " + result.size() + " unike potensielle filer.");
                    if (result.isEmpty()) {
                        log("Ingen filer funnet.");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log("Analyse avbrutt: " + ex.getMessage());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log("Feil under analyse: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
                    cause.printStackTrace();
                    JOptionPane.showMessageDialog(ImageDownloaderApp.this, "Feil under analyse: " + cause.getMessage(), "Feil", JOptionPane.ERROR_MESSAGE);
                } finally {
                    fetchButton.setEnabled(true);
                    updateDownloadButtonState(); // Oppdateres basert på tabellens innhold nå
                }
            }
        };
        worker.execute();
    }

    private void downloadFilesAction(ActionEvent e) {
        List<FileInfo> filesToDownload = new ArrayList<>();
        for (FileInfo fi : fetchedFilesList) { // Gå gjennom hovedlisten som tabellen bruker
            if (fi.selected) {
                filesToDownload.add(fi);
            }
        }

        if (filesToDownload.isEmpty()) {
            log("Ingen filer er valgt for nedlasting.");
            JOptionPane.showMessageDialog(this, "Ingen filer er valgt for nedlasting.", "Advarsel", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (directoryChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            saveDirectory = directoryChooser.getSelectedFile();
            if (!saveDirectory.exists()) {
                if (!saveDirectory.mkdirs()) { /* ... (feilhåndtering som før) ... */
                    log("Kunne ikke opprette mappe: " + saveDirectory.getAbsolutePath());
                    JOptionPane.showMessageDialog(this, "Kunne ikke opprette nedlastingsmappe.", "Feil", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            log("Valgt mappe for nedlasting: " + saveDirectory.getAbsolutePath());
            log("Starter nedlasting av " + filesToDownload.size() + " filer...");

            downloadButton.setEnabled(false);
            fetchButton.setEnabled(false);
            selectAllButton.setEnabled(false);
            selectNoneButton.setEnabled(false);
            progressBar.setValue(0);
            progressBar.setMaximum(filesToDownload.size());

            SwingWorker<Integer, Object[]> downloadWorker = new SwingWorker<>() {
                // ... (doInBackground og process som før, bruk filesToDownload)
                private int successfulDownloads = 0;

                @Override
                protected Integer doInBackground() throws Exception {
                    int currentProgress = 0;
                    for (FileInfo fileInfo : filesToDownload) {
                        if (isCancelled()) break;

                        try {
                            publish(new Object[]{"Laster ned: " + fileInfo.url, currentProgress});
                            URL fileUrl = new URL(fileInfo.url);
                            URLConnection connection = fileUrl.openConnection();
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                            connection.setConnectTimeout(10000);
                            connection.setReadTimeout(30000);

                            String fileName = Paths.get(fileUrl.getPath()).getFileName().toString();
                            if (fileName.isEmpty() || !fileName.contains(".")) {
                                fileName = "downloaded_file_" + System.currentTimeMillis() + (fileInfo.extension != null ? fileInfo.extension : ".tmp");
                            }
                            fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("_+", "_");

                            File outputFile = new File(saveDirectory, fileName);
                            int fileSuffix = 1;
                            String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                            String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
                            while(outputFile.exists()){
                                outputFile = new File(saveDirectory, baseName + "_" + fileSuffix++ + extension);
                            }

                            try (InputStream in = connection.getInputStream();
                                 FileOutputStream out = new FileOutputStream(outputFile)) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    if (isCancelled()) {
                                        out.close();
                                        Files.deleteIfExists(outputFile.toPath());
                                        publish(new Object[]{"Nedlasting avbrutt for: " + outputFile.getName(), currentProgress});
                                        throw new InterruptedException("Download cancelled by user");
                                    }
                                    out.write(buffer, 0, bytesRead);
                                }
                            }
                            successfulDownloads++;
                            publish(new Object[]{"Lastet ned: " + outputFile.getName(), currentProgress + 1});
                        } catch (IOException ex) {
                            publish(new Object[]{"Feil ved nedlasting av " + fileInfo.url + ": " + ex.getMessage(), currentProgress + 1});
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        currentProgress++;
                    }
                    return successfulDownloads;
                }

                @Override
                protected void process(List<Object[]> chunks) {
                    for (Object[] chunk : chunks) {
                        log((String)chunk[0]);
                        progressBar.setValue((Integer)chunk[1]);
                    }
                }

                @Override
                protected void done() {
                    try {
                        int downloadedCount = get();
                        log("Nedlasting fullført. " + downloadedCount + " av " + filesToDownload.size() + " filer ble lastet ned.");
                        JOptionPane.showMessageDialog(ImageDownloaderApp.this,
                                "Nedlasting fullført.\n" + downloadedCount + " filer lastet ned til:\n" + saveDirectory.getAbsolutePath(),
                                "Fullført", JOptionPane.INFORMATION_MESSAGE);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        log("Nedlasting avbrutt av bruker.");
                    } catch (ExecutionException ex) {
                        log("Feil under nedlasting: " + ex.getCause().getMessage());
                    } finally {
                        fetchButton.setEnabled(true); // Re-enable fetch button
                        updateDownloadButtonState(); // Re-evaluerer basert på tabell
                    }
                }
            };
            downloadWorker.execute();
        } else {
            log("Nedlasting kansellert av bruker (valgte ikke mappe).");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ImageDownloaderApp app = new ImageDownloaderApp();
            app.setVisible(true);
        });
    }
}