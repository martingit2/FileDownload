// ImageDownloaderApp.java
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
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
import java.util.HashMap; // Ikke lenger brukt direkte, men beholdes for potensiell fremtidig bruk
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
// import java.util.stream.Collectors; // Ikke lenger brukt direkte

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ImageDownloaderApp extends JFrame {

    private JTextField urlField;
    private JComboBox<String> categoryComboBox;
    private JButton fetchButton, downloadButton, selectAllButton, selectNoneButton;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JFileChooser directoryChooser;
    private transient List<FileInfo> fetchedFilesList; // Hoveddatakilde for hentede filer
    private JTable filesTable;
    private FilesTableModel tableModel;
    private File saveDirectory;
    private JPanel extensionSelectionPanel; // Panel for sjekkbokser for filendelser
    private transient List<JCheckBox> currentExtensionCheckBoxes = new ArrayList<>(); // Holder styr på sjekkboksene

    // Definerer filkategorier og deres tilknyttede filtyper.
    // LinkedHashMap bevarer innsettingsrekkefølgen for JComboBox.
    private static final Map<String, List<String>> CATEGORIZED_EXTENSIONS = new LinkedHashMap<>();
    static {
        CATEGORIZED_EXTENSIONS.put("Bilder", Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".bmp", ".tiff", ".ico"));
        CATEGORIZED_EXTENSIONS.put("Dokumenter", Arrays.asList(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".rtf", ".odt", ".csv", ".pages", ".numbers", ".key"));
        CATEGORIZED_EXTENSIONS.put("Videoer", Arrays.asList(".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv", ".wmv", ".mpeg", ".mpg"));
        CATEGORIZED_EXTENSIONS.put("Lydfiler", Arrays.asList(".mp3", ".wav", ".ogg", ".aac", ".flac", ".m4a", ".wma"));
        CATEGORIZED_EXTENSIONS.put("Arkiver", Arrays.asList(".zip", ".rar", ".tar", ".gz", ".7z", ".bz2", ".xz"));
        CATEGORIZED_EXTENSIONS.put("Kildekode/Tekst", Arrays.asList(".java", ".js", ".html", ".css", ".xml", ".json", ".py", ".c", ".cpp", ".h", ".cs", ".php", ".rb", ".sql", ".md"));
        // Man kan legge til flere kategorier og filtyper etter behov.
    }
    private static final String ALL_FILES_CATEGORY = "Alle filtyper";

    // Representerer en fil funnet på nettsiden.
    protected static class FileInfo {
        String url;
        String extension;
        String detectedType; // Kategori som "Bilder", "Dokumenter"
        boolean selected = true; // Standard er valgt

        FileInfo(String url, String extension) {
            this.url = url;
            this.extension = extension != null ? extension.toLowerCase() : ""; // Alltid små bokstaver for sammenligning
            this.detectedType = categorizeExtension(this.extension);
        }

        private String categorizeExtension(String ext) {
            if (ext == null || ext.isEmpty()) return "Ukjent";
            for (Map.Entry<String, List<String>> entry : CATEGORIZED_EXTENSIONS.entrySet()) {
                if (entry.getValue().contains(ext)) {
                    return entry.getKey();
                }
            }
            return "Annet"; // For filer som ikke matcher forhåndsdefinerte kategorier
        }
    }

    // Egendefinert TableModel for å vise FileInfo-objekter i JTable.
    class FilesTableModel extends AbstractTableModel {
        private final List<FileInfo> files; // Referanse til hoveddatalisten
        private final String[] columnNames = {"Velg", "Fil-URL", "Type", "Filtype (.ext)"};

        public FilesTableModel(List<FileInfo> files) {
            this.files = files;
        }

        @Override
        public int getRowCount() { return files.size(); }
        @Override
        public int getColumnCount() { return columnNames.length; }
        @Override
        public String getColumnName(int column) { return columnNames[column]; }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return (columnIndex == 0) ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
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
                updateDownloadButtonState();
            }
        }

        public void setData(List<FileInfo> newFiles) {
            this.files.clear();
            if (newFiles != null) {
                this.files.addAll(newFiles);
            }
            filterAndSelectBasedOnCategory();
        }

        public void filterAndSelectBasedOnCategory() {
            String selectedCategory = (String) categoryComboBox.getSelectedItem();
            if (selectedCategory == null) return;

            List<String> extensionsInCurrentCategory = null;
            if (!ALL_FILES_CATEGORY.equals(selectedCategory)) {
                extensionsInCurrentCategory = CATEGORIZED_EXTENSIONS.get(selectedCategory);
            }

            boolean hasChanges = false;
            for (FileInfo file : files) {
                boolean shouldBeSelected;
                if (ALL_FILES_CATEGORY.equals(selectedCategory)) {
                    shouldBeSelected = true;
                } else if (extensionsInCurrentCategory != null) {
                    shouldBeSelected = extensionsInCurrentCategory.contains(file.extension);
                } else {
                    shouldBeSelected = false;
                }
                if (file.selected != shouldBeSelected) {
                    file.selected = shouldBeSelected;
                    hasChanges = true;
                }
            }
            if (hasChanges || !files.isEmpty()) {
                fireTableDataChanged();
            }
            updateDownloadButtonState();
        }
    }

    // Konstruktør for hovedapplikasjonsvinduet.
    public ImageDownloaderApp() {
        setTitle("Avansert Filnedlaster Pro");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 800); // Økt størrelsen litt for å få plass til alt
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Kunne ikke sette system look and feel: " + e.getMessage());
        }

        fetchedFilesList = new ArrayList<>();
        tableModel = new FilesTableModel(fetchedFilesList);
        initComponents();
    }

    // Setter opp alle GUI-komponentene.
    private void initComponents() {
        // Toppanel for URL-input og kategorivalg.
        JPanel topInputPanel = new JPanel(new BorderLayout(10, 5));
        topInputPanel.setBorder(new EmptyBorder(10, 10, 5, 10));

        String placeholderText = "Skriv inn URL her...";
        urlField = new JTextField(placeholderText);
        urlField.setForeground(Color.GRAY);
        urlField.addFocusListener(new FocusAdapter() {
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
            tableModel.filterAndSelectBasedOnCategory();
            updateButtonTexts(); // Denne gjør ikke så mye lenger, men kan beholdes for fremtiden
            updateExtensionCheckboxes();
        });
        categoryAndFetchPanel.add(categoryComboBox);

        fetchButton = new JButton("Analyser URL");
        fetchButton.setToolTipText("Hent og vis fillenker fra URL");
        fetchButton.addActionListener(this::fetchFileLinksAction);
        categoryAndFetchPanel.add(fetchButton);

        topInputPanel.add(categoryAndFetchPanel, BorderLayout.EAST);
        add(topInputPanel, BorderLayout.NORTH);

        // Senterpanel for filtabellen og dens kontroller.
        JPanel centerPanel = new JPanel(new BorderLayout(10,5));
        centerPanel.setBorder(new EmptyBorder(0, 10, 0, 10));

        filesTable = new JTable(tableModel);
        filesTable.setAutoCreateRowSorter(true);
        setupTableColumns();

        JScrollPane tableScrollPane = new JScrollPane(filesTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Funnet Filer"));
        centerPanel.add(tableScrollPane, BorderLayout.CENTER);

        JPanel bottomControlsForTablePanel = new JPanel(new BorderLayout(5,5));
        JPanel selectionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectAllButton = new JButton("Velg Alle Viste Typer");
        selectAllButton.setToolTipText("Velg alle filer av typene som vises i filteret under");
        selectAllButton.addActionListener(e -> setExtensionCheckBoxesState(true));
        selectionButtonsPanel.add(selectAllButton);

        selectNoneButton = new JButton("Velg Ingen Viste Typer");
        selectNoneButton.setToolTipText("Fjern valg for alle filer av typene som vises i filteret under");
        selectNoneButton.addActionListener(e -> setExtensionCheckBoxesState(false));
        selectionButtonsPanel.add(selectNoneButton);
        bottomControlsForTablePanel.add(selectionButtonsPanel, BorderLayout.NORTH);

        extensionSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JScrollPane extensionScrollPane = new JScrollPane(extensionSelectionPanel);
        extensionScrollPane.setBorder(BorderFactory.createTitledBorder("Filtrer på Filtype"));
        // Juster høyden dynamisk eller sett en maks/min høyde. For nå en fast foretrukket høyde.
        extensionScrollPane.setPreferredSize(new Dimension(100, 100));
        bottomControlsForTablePanel.add(extensionScrollPane, BorderLayout.CENTER);

        centerPanel.add(bottomControlsForTablePanel, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        // Bunnpanel for logg, nedlastingsknapp og progressbar.
        JPanel bottomOuterPanel = new JPanel(new BorderLayout(10,5));
        bottomOuterPanel.setBorder(new EmptyBorder(5,10,10,10));

        logArea = new JTextArea(8, 0); // Økt høyde litt for loggen
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        DefaultCaret caret = (DefaultCaret)logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane logScrollPane = new JScrollPane(logArea);

        JPanel logPanelWithTitle = new JPanel(new BorderLayout());
        logPanelWithTitle.add(new JLabel("Logg:"), BorderLayout.NORTH);
        logPanelWithTitle.add(logScrollPane, BorderLayout.CENTER);
        bottomOuterPanel.add(logPanelWithTitle, BorderLayout.CENTER);

        JPanel downloadControlsPanel = new JPanel(new BorderLayout(10,5));
        downloadButton = new JButton("Last Ned Valgte Filer");
        downloadButton.addActionListener(this::downloadFilesAction);
        downloadControlsPanel.add(downloadButton, BorderLayout.EAST);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        downloadControlsPanel.add(progressBar, BorderLayout.CENTER);

        bottomOuterPanel.add(downloadControlsPanel, BorderLayout.SOUTH);
        add(bottomOuterPanel, BorderLayout.SOUTH);

        directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        directoryChooser.setDialogTitle("Velg Mappe for Nedlasting");

        updateButtonTexts();
        updateDownloadButtonState();
        updateExtensionCheckboxes();
    }

    // Konfigurerer kolonnene i filtabellen.
    private void setupTableColumns() {
        TableColumn selectColumn = filesTable.getColumnModel().getColumn(0); // "Velg"
        selectColumn.setPreferredWidth(50);
        selectColumn.setMaxWidth(70);
        selectColumn.setMinWidth(40);

        TableColumn urlColumn = filesTable.getColumnModel().getColumn(1); // "Fil-URL"
        urlColumn.setPreferredWidth(500); // Gi mer plass til URL

        TableColumn typeColumn = filesTable.getColumnModel().getColumn(2); // "Type" (kategori)
        typeColumn.setPreferredWidth(120);

        TableColumn extColumn = filesTable.getColumnModel().getColumn(3); // "Filtype (.ext)"
        extColumn.setPreferredWidth(100);
    }

    // Oppdaterer status for knapper.
    private void updateDownloadButtonState() {
        boolean anySelectedInTable = false;
        for (FileInfo fi : fetchedFilesList) {
            if (fi.selected) {
                anySelectedInTable = true;
                break;
            }
        }
        downloadButton.setEnabled(anySelectedInTable && !fetchedFilesList.isEmpty());

        boolean hasExtensionCheckboxes = !currentExtensionCheckBoxes.isEmpty();
        selectAllButton.setEnabled(hasExtensionCheckboxes);
        selectNoneButton.setEnabled(hasExtensionCheckboxes);
    }

    // Oppdaterer teksten på knapper (for tiden ikke mye dynamisk endring her).
    private void updateButtonTexts() {
        // Denne metoden kan utvides hvis knappetekster skal endres mer dynamisk.
    }

    // Logger en melding til loggområdet.
    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    // Henter filendelsen fra en URL-streng. Forbedret for å håndtere flere tilfeller.
    private String getFileExtensionFromUrl(String urlString) {
        if (urlString == null || urlString.isEmpty()) return null;

        try {
            // Fjern query-parametere og anker før vi ser etter filendelse i stien.
            String pathPart = urlString.split("\\?")[0].split("#")[0];

            // Prøv å parse som URL for å få stien, hvis mulig.
            // Dette er mer robust enn ren strengmanipulasjon for komplekse URLer.
            try {
                URL tempUrl = new URL(urlString); // Bruk original URL for parsing av path
                pathPart = tempUrl.getPath();
                if (pathPart == null || pathPart.isEmpty() || pathPart.endsWith("/")) {
                    // Hvis stien er tom eller slutter på "/", kan filnavnet være i siste segment av host.
                    // Dette er uvanlig, men la oss sjekke den "rå" pathPart fra strengen.
                    pathPart = urlString.split("\\?")[0].split("#")[0];
                }
            } catch (MalformedURLException ignored) {
                // Hvis URL-parsing feiler, bruk den allerede rensede pathPart.
            }

            if (pathPart != null && !pathPart.isEmpty() && !pathPart.endsWith("/")) {
                String fileNameCandidate = pathPart.substring(pathPart.lastIndexOf('/') + 1);
                if (fileNameCandidate.contains(".")) {
                    String ext = fileNameCandidate.substring(fileNameCandidate.lastIndexOf('.')).toLowerCase();
                    // Enkel validering: start med '.', etterfulgt av 1-5 alfanumeriske tegn.
                    if (ext.length() > 1 && ext.length() <= 6 && ext.matches("\\.[a-z0-9]+")) {
                        return ext;
                    }
                }
            }

            // Hvis ingen filtype i path, sjekk query for vanlige formatparametere.
            if (urlString.contains("?")) {
                String query = urlString.substring(urlString.indexOf('?') + 1);
                Pattern pattern = Pattern.compile("(?:type|format|ext)=([^&]+)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(query);
                if (matcher.find()) {
                    String format = matcher.group(1).toLowerCase();
                    // Valider formatet. Fjern eventuelle ekstra tegn etter filtypen.
                    format = format.replaceAll("[^a-z0-9].*", "");
                    if (format.length() > 0 && format.length() <= 5 && format.matches("[a-z0-9]+")) {
                        return "." + format;
                    }
                }
            }
        } catch (Exception e) {
            // Generell feilhåndtering for uventede problemer med strengmanipulasjon.
            log("Feil ved parsing av filtype fra URL: " + urlString + " - " + e.getMessage());
        }
        return null; // Ingen gyldig filtype funnet.
    }

    // Setter valgt-status for alle sjekkbokser for filendelser.
    private void setExtensionCheckBoxesState(boolean selected) {
        for (JCheckBox checkBox : currentExtensionCheckBoxes) {
            checkBox.setSelected(selected);
        }
    }

    // Oppdaterer panelet med sjekkbokser for filendelser.
    private void updateExtensionCheckboxes() {
        extensionSelectionPanel.removeAll();
        currentExtensionCheckBoxes.clear();

        String selectedCategory = (String) categoryComboBox.getSelectedItem();
        if (selectedCategory == null || fetchedFilesList.isEmpty()) {
            extensionSelectionPanel.revalidate();
            extensionSelectionPanel.repaint();
            updateDownloadButtonState();
            return;
        }

        Set<String> relevantExtensionsInTable = new HashSet<>();
        for (FileInfo fi : fetchedFilesList) {
            if (fi.extension != null && !fi.extension.isEmpty()) {
                if (ALL_FILES_CATEGORY.equals(selectedCategory)) {
                    relevantExtensionsInTable.add(fi.extension);
                } else {
                    List<String> extsForCategory = CATEGORIZED_EXTENSIONS.get(selectedCategory);
                    if (extsForCategory != null && extsForCategory.contains(fi.extension)) {
                        relevantExtensionsInTable.add(fi.extension);
                    }
                }
            }
        }

        List<String> sortedExtensions = new ArrayList<>(relevantExtensionsInTable);
        sortedExtensions.sort(String.CASE_INSENSITIVE_ORDER);

        if (sortedExtensions.isEmpty()){
            JLabel noExtLabel = new JLabel("Ingen filtyper å filtrere på for valgt kategori.");
            noExtLabel.setForeground(Color.GRAY);
            extensionSelectionPanel.add(noExtLabel);
        } else {
            for (String ext : sortedExtensions) {
                boolean isAnySelectedForThisExt = false;
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (ext.equals(tableModel.getValueAt(i, 3)) && (Boolean)tableModel.getValueAt(i, 0)) {
                        isAnySelectedForThisExt = true;
                        break;
                    }
                }

                JCheckBox extCheckBox = new JCheckBox(ext, isAnySelectedForThisExt);
                extCheckBox.setToolTipText("Velg/fjern alle filer med filtypen " + ext);
                extCheckBox.addActionListener(e -> {
                    boolean makeSelected = extCheckBox.isSelected();
                    for (int i = 0; i < tableModel.getRowCount(); i++) {
                        if (ext.equals(tableModel.getValueAt(i, 3))) {
                            if ((Boolean)tableModel.getValueAt(i, 0) != makeSelected) {
                                tableModel.setValueAt(makeSelected, i, 0);
                            }
                        }
                    }
                    updateDownloadButtonState();
                });
                extensionSelectionPanel.add(extCheckBox);
                currentExtensionCheckBoxes.add(extCheckBox);
            }
        }
        updateDownloadButtonState();
        extensionSelectionPanel.revalidate();
        extensionSelectionPanel.repaint();
    }

    // Håndterer handlingen for å hente fillenker fra en URL.
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
        downloadButton.setEnabled(false);
        selectAllButton.setEnabled(false);
        selectNoneButton.setEnabled(false);
        progressBar.setValue(0);

        SwingWorker<List<FileInfo>, String> worker = new SwingWorker<>() {
            @Override
            protected List<FileInfo> doInBackground() throws Exception {
                List<FileInfo> foundOnPage = new ArrayList<>();
                Set<String> uniqueFileUrls = new HashSet<>();

                publish("Kobler til " + urlText + "...");
                // Legg til .ignoreContentType(true) for å håndtere sider som feilaktig serverer f.eks. XML som text/html
                // .followRedirects(true) er standard, men kan være eksplisitt.
                Document doc = Jsoup.connect(urlText)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36") // Oppdatert User-Agent
                        .timeout(20000) // Økt timeout til 20 sekunder
                        .ignoreHttpErrors(true) // Prøv å parse selv om det er HTTP-feil (f.eks. 404 på en ressurs, men siden lastes)
                        .followRedirects(true)
                        .get();

                if (doc.connection().response().statusCode() >= 400) {
                    publish("Advarsel: Server returnerte statuskode " + doc.connection().response().statusCode() + " for " + urlText);
                }
                publish("Koblet til. Analyserer HTML...");

                // Utvidet selector for å fange flere potensielle fil-lenker
                Elements links = doc.select("a[href], img[src], source[srcset], video[src], audio[src], track[src], embed[src], object[data], link[href]");

                for (Element link : links) {
                    String url = "";
                    String tagName = link.tagName().toLowerCase();

                    if (link.hasAttr("href")) url = link.absUrl("href"); // Prioriter href for a-tags
                    else if (link.hasAttr("src")) url = link.absUrl("src");
                    else if (link.hasAttr("srcset")) url = link.absUrl("srcset").split(",")[0].trim().split("\\s+")[0];
                    else if (link.hasAttr("data")) url = link.absUrl("data");

                    if (url.isEmpty() || url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("javascript:") || url.startsWith("#")) continue;
                    // Fjern anker/fragment hvis det ikke allerede er gjort av absUrl eller forrige sjekk.
                    if (url.contains("#")) {
                        url = url.substring(0, url.indexOf('#'));
                    }

                    String extension = getFileExtensionFromUrl(url);
                    boolean isPotentialFile = (extension != null && !extension.isEmpty());

                    if ("link".equals(tagName)) {
                        String rel = link.attr("rel").toLowerCase();
                        // Vær mer selektiv med <link> tags, da mange ikke er nedlastbare filer.
                        if (!("stylesheet".equals(rel) || "icon".equals(rel) || "shortcut icon".equals(rel) ||
                                "apple-touch-icon".equals(rel) || "preload".equals(rel) || "manifest".equals(rel) ||
                                // Noen ganger brukes "alternate" for RSS/Atom feeds, som kan være XML
                                ("alternate".equals(rel) && (url.endsWith(".xml") || url.endsWith(".rss") || url.endsWith(".atom")))
                        )) {
                            if(!isPotentialFile) continue;
                        }
                    }

                    // For a-tags, sjekk om 'download' attributtet er satt, noe som sterkt indikerer en fil.
                    if ("a".equals(tagName) && link.hasAttr("download") && !isPotentialFile) {
                        // Hvis 'download' er satt, men vi ikke fant en extension, kan vi prøve en default.
                        // Dette er mindre vanlig, men verdt å vurdere. For nå, stol på extension.
                    }


                    if (isPotentialFile && uniqueFileUrls.add(url)) {
                        FileInfo fileInfo = new FileInfo(url, extension);
                        foundOnPage.add(fileInfo);
                        publish("Fant: " + url);
                    } else if (extension == null && "a".equals(tagName) && !url.endsWith("/")) {
                        // Hvis det er en <a> tag uten kjent filtype, men ikke ser ut som en mappe-URL,
                        // kan vi vurdere å legge den til for manuell sjekk (kanskje med "Ukjent" type).
                        // Foreløpig, ignorer for å unngå for mye støy.
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
                    tableModel.setData(result);
                    log("Analyse fullført. Fant " + result.size() + " unike potensielle filer.");
                    if (result.isEmpty()) {
                        log("Ingen filer funnet som matcher kriteriene.");
                    }
                    updateExtensionCheckboxes();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log("Analyse avbrutt: " + ex.getMessage());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log("Feil under analyse: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
                    if(cause.getCause() != null) log("  Underliggende årsak: " + cause.getCause().getMessage());
                    JOptionPane.showMessageDialog(ImageDownloaderApp.this, "Feil under analyse: " + cause.getMessage(), "Feil", JOptionPane.ERROR_MESSAGE);
                } finally {
                    fetchButton.setEnabled(true);
                    updateDownloadButtonState();
                }
            }
        };
        worker.execute();
    }

    // Håndterer handlingen for å laste ned valgte filer.
    private void downloadFilesAction(ActionEvent e) {
        List<FileInfo> filesToDownload = new ArrayList<>();
        for (FileInfo fi : fetchedFilesList) {
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
                if (!saveDirectory.mkdirs()) {
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
                private int successfulDownloads = 0;

                @Override
                protected Integer doInBackground() throws Exception {
                    int currentProgress = 0;
                    for (FileInfo fileInfo : filesToDownload) {
                        if (isCancelled()) break;

                        HttpURLConnection connection = null; // Definer utenfor try for finally-blokk
                        try {
                            publish(new Object[]{"Laster ned: " + fileInfo.url, currentProgress});
                            URL fileUrl = new URL(fileInfo.url);
                            connection = (HttpURLConnection) fileUrl.openConnection(); // Cast til HttpURLConnection for flere options
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36");
                            connection.setConnectTimeout(15000);
                            connection.setReadTimeout(60000); // Økt lesetimeout for store filer
                            connection.setInstanceFollowRedirects(true); // Følg omdirigeringer

                            // Håndter respons-koder
                            int responseCode = connection.getResponseCode();
                            if (responseCode >= 400) {
                                publish(new Object[]{"Feil ("+ responseCode +") ved henting av " + fileInfo.url, currentProgress + 1});
                                currentProgress++;
                                continue; // Gå til neste fil
                            }

                            // Prøv å få et bedre filnavn fra Content-Disposition header hvis tilgjengelig
                            String contentDisposition = connection.getHeaderField("Content-Disposition");
                            String fileNameFromHeader = null;
                            if (contentDisposition != null) {
                                Pattern fileNamePattern = Pattern.compile("filename\\*?=['\"]?([^'\"]+)['\"]?", Pattern.CASE_INSENSITIVE);
                                Matcher matcher = fileNamePattern.matcher(contentDisposition);
                                if (matcher.find()) {
                                    fileNameFromHeader = matcher.group(1);
                                    // Håndter URL-koding (f.eks. UTF-8''no%20filnavn.pdf)
                                    if (fileNameFromHeader.toLowerCase().startsWith("utf-8''")) {
                                        fileNameFromHeader = java.net.URLDecoder.decode(fileNameFromHeader.substring(7), "UTF-8");
                                    }
                                }
                            }

                            String fileName;
                            if (fileNameFromHeader != null && !fileNameFromHeader.trim().isEmpty()) {
                                fileName = fileNameFromHeader;
                            } else {
                                fileName = Paths.get(fileUrl.getPath()).getFileName().toString();
                                if (fileName.isEmpty() || (!fileName.contains(".") && fileInfo.extension != null && !fileInfo.extension.isEmpty()) ) {
                                    // Hvis filnavn fra path er tomt eller mangler extension, bruk en fallback
                                    fileName = "downloaded_file_" + System.currentTimeMillis() + (fileInfo.extension != null ? fileInfo.extension : ".dat");
                                }
                            }
                            fileName = fileName.replaceAll("[^a-zA-Z0-9._\\- ()]", "_").replaceAll("_+", "_").trim(); // Mer tillatende rensing
                            if (fileName.isEmpty()) { // Hvis rensing fjerner alt
                                fileName = "file_" + System.currentTimeMillis() + (fileInfo.extension != null ? fileInfo.extension : ".dat");
                            }


                            File outputFile = new File(saveDirectory, fileName);
                            int fileSuffix = 1;
                            String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                            String extensionPart = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
                            while(outputFile.exists()){
                                outputFile = new File(saveDirectory, baseName + "_" + fileSuffix++ + extensionPart);
                            }

                            try (InputStream in = connection.getInputStream();
                                 FileOutputStream out = new FileOutputStream(outputFile)) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    if (isCancelled()) {
                                        out.close(); // Lukk strømmen før sletting
                                        if (outputFile.exists()) Files.delete(outputFile.toPath());
                                        publish(new Object[]{"Nedlasting avbrutt for: " + outputFile.getName(), currentProgress});
                                        throw new InterruptedException("Nedlasting avbrutt av bruker");
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
                        } finally {
                            if (connection != null) {
                                connection.disconnect(); // Lukk alltid tilkoblingen
                            }
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
                        JOptionPane.showMessageDialog(ImageDownloaderApp.this, "Nedlasting avbrutt.", "Avbrutt", JOptionPane.WARNING_MESSAGE);
                    } catch (ExecutionException ex) {
                        log("Feil under nedlasting: " + ex.getCause().getMessage());
                        JOptionPane.showMessageDialog(ImageDownloaderApp.this, "En feil oppstod under nedlasting: " + ex.getCause().getMessage(), "Feil", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        fetchButton.setEnabled(true);
                        updateDownloadButtonState();
                        progressBar.setValue(progressBar.getMaximum());
                    }
                }
            };
            downloadWorker.execute();
        } else {
            log("Nedlasting kansellert av bruker (valgte ikke mappe).");
        }
    }

    // Hovedmetode for å starte applikasjonen.
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ImageDownloaderApp app = new ImageDownloaderApp();
            app.setVisible(true);
        });
    }
}