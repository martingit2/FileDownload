// ImageDownloaderApp.java
import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
    private JPanel fileTypeSelectionPanel; // Panel for JCheckBoxes
    private JButton fetchButton, downloadButton;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JFileChooser directoryChooser;
    private transient List<FileInfo> fetchedFiles;
    private File saveDirectory;

    // Definerer kategorier og deres tilknyttede filtyper
    // Bruker LinkedHashMap for å bevare rekkefølgen i dropdown
    private static final Map<String, List<String>> CATEGORIZED_EXTENSIONS = new LinkedHashMap<>();
    static {
        CATEGORIZED_EXTENSIONS.put("Bilder", Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".bmp", ".tiff", ".ico"));
        CATEGORIZED_EXTENSIONS.put("Dokumenter", Arrays.asList(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".rtf", ".odt"));
        CATEGORIZED_EXTENSIONS.put("Videoer", Arrays.asList(".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv", ".wmv"));
        CATEGORIZED_EXTENSIONS.put("Lydfiler", Arrays.asList(".mp3", ".wav", ".ogg", ".aac", ".flac"));
        CATEGORIZED_EXTENSIONS.put("Arkiver", Arrays.asList(".zip", ".rar", ".tar", ".gz", ".7z"));
        // "Alle Filer" vil bli håndtert spesielt, kanskje ved å lete etter alle lenker med kjente filtyper
        // eller ved å ha en egen avhukingsboks for "Prøv å hente alle lenkede filer uansett type"
    }
    private static final String ALL_FILES_CATEGORY = "Alle filtyper"; // En spesiell kategori

    // Enkel klasse for å holde på filinformasjon
    private static class FileInfo {
        String url;
        String extension;
        String detectedType; // f.eks. "Bilder", "Dokumenter"
        boolean selected = true;

        FileInfo(String url, String extension) {
            this.url = url;
            this.extension = extension != null ? extension.toLowerCase() : "";
            // Enkel logikk for å bestemme type basert på extension
            this.detectedType = categorizeExtension(this.extension);
        }

        private String categorizeExtension(String ext) {
            if (ext == null || ext.isEmpty()) return "Ukjent";
            for (Map.Entry<String, List<String>> entry : CATEGORIZED_EXTENSIONS.entrySet()) {
                if (entry.getValue().contains(ext)) {
                    return entry.getKey();
                }
            }
            return "Annet"; // For filer som ikke matcher de forhåndsdefinerte kategoriene
        }

        @Override
        public String toString() {
            return url;
        }
    }

    public ImageDownloaderApp() {
        setTitle("Avansert Filnedlaster");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 700); // Økt størrelse litt
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Kunne ikke sette system look and feel: " + e.getMessage());
        }

        fetchedFiles = new ArrayList<>();
        initComponents();
        updateFileTypeCheckBoxes(); // Initial oppdatering av sjekkbokser
    }

    private void initComponents() {
        // ----- Toppanel: URL-input og Kategori -----
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

        categoryComboBox = new JComboBox<>(CATEGORIZED_EXTENSIONS.keySet().toArray(new String[0]));
        categoryComboBox.addItem(ALL_FILES_CATEGORY); // Legg til "Alle filtyper"
        categoryComboBox.setSelectedItem("Bilder"); // Standardvalg
        categoryComboBox.setToolTipText("Velg filkategori du ønsker å fokusere på");
        categoryComboBox.addActionListener(e -> {
            updateFileTypeCheckBoxes();
            updateButtonTexts();
        });
        topInputPanel.add(categoryComboBox, BorderLayout.EAST);

        add(topInputPanel, BorderLayout.NORTH);


        // ----- Senterpanel: Filtypevalg og Hent-knapp plassert over loggen -----
        JPanel centerContainerPanel = new JPanel(new BorderLayout(10,10));
        centerContainerPanel.setBorder(new EmptyBorder(0, 10, 0, 10));


        // ----- Filtypevalgpanel (dynamisk) -----
        fileTypeSelectionPanel = new JPanel(); // Bruker FlowLayout som standard, kan endres
        fileTypeSelectionPanel.setLayout(new BoxLayout(fileTypeSelectionPanel, BoxLayout.Y_AXIS));
        JScrollPane fileTypeScrollPane = new JScrollPane(fileTypeSelectionPanel);
        fileTypeScrollPane.setBorder(BorderFactory.createTitledBorder("Velg Filformater"));
        fileTypeScrollPane.setPreferredSize(new Dimension(200, 150)); // Gi den en foretrukket størrelse


        // Panel for filtypevalg og hent-knapp
        JPanel controlsPanel = new JPanel(new BorderLayout(5,10));
        controlsPanel.add(fileTypeScrollPane, BorderLayout.CENTER);

        fetchButton = new JButton("Hent Bilder"); // Starttekst, oppdateres dynamisk
        fetchButton.setToolTipText("Hent fillenker fra URL basert på valgte typer");
        fetchButton.addActionListener(this::fetchFileLinksAction);
        controlsPanel.add(fetchButton, BorderLayout.SOUTH);

        centerContainerPanel.add(controlsPanel, BorderLayout.WEST);


        // ----- Loggpanel -----
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        DefaultCaret caret = (DefaultCaret)logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Logg"));
        centerContainerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerContainerPanel, BorderLayout.CENTER);


        // ----- Bunnpanel: Nedlastingsknapp og Progressbar -----
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        downloadButton = new JButton("Last Ned Valgte Filer"); // Starttekst
        downloadButton.setEnabled(false);
        downloadButton.setToolTipText("Start nedlasting av valgte filer til valgt mappe");
        downloadButton.addActionListener(this::downloadFilesAction);
        bottomPanel.add(downloadButton, BorderLayout.EAST);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        bottomPanel.add(progressBar, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        directoryChooser.setDialogTitle("Velg Mappe for Nedlasting");

        updateButtonTexts(); // Initialiser knappetekster
    }

    private void updateFileTypeCheckBoxes() {
        fileTypeSelectionPanel.removeAll();
        String selectedCategory = (String) categoryComboBox.getSelectedItem();

        if (selectedCategory == null) return;

        List<String> extensions;
        if (ALL_FILES_CATEGORY.equals(selectedCategory)) {
            // For "Alle filtyper", kan vi enten vise alle kjente extensions,
            // eller ha en "spesiell" boks. La oss vise alle for nå, men deaktivert.
            // Eller enda bedre, vi kan ha en enkelt boks "Hent alle detekterte filer".
            JCheckBox allKnownCheckBox = new JCheckBox("Alle kjente filtyper", true);
            allKnownCheckBox.setToolTipText("Prøver å hente alle filer med kjente filendelser fra kategoriene");
            // Man kan også velge å ikke ha sjekkbokser i det hele tatt for "Alle filtyper"
            // og heller stole på en mer generell søkelogikk.
            // For nå:
            // fileTypeSelectionPanel.add(new JLabel("Henter alle gjenkjennelige filtyper."));
            // Eller, vi kan vise alle extensions fra alle kategorier, og la brukeren velge:
            Set<String> allExts = new HashSet<>();
            CATEGORIZED_EXTENSIONS.values().forEach(allExts::addAll);
            extensions = new ArrayList<>(allExts);
            extensions.sort(String.CASE_INSENSITIVE_ORDER);

        } else {
            extensions = CATEGORIZED_EXTENSIONS.get(selectedCategory);
        }

        if (extensions != null) {
            for (String ext : extensions) {
                JCheckBox checkBox = new JCheckBox(ext, true); // Standard er valgt
                fileTypeSelectionPanel.add(checkBox);
            }
        }

        fileTypeSelectionPanel.revalidate();
        fileTypeSelectionPanel.repaint();
    }

    private void updateButtonTexts() {
        String selectedCategory = (String) categoryComboBox.getSelectedItem();
        if (selectedCategory == null) selectedCategory = "Filer"; // Fallback

        if (ALL_FILES_CATEGORY.equals(selectedCategory)) {
            fetchButton.setText("Hent Alle Filer");
            downloadButton.setText("Last Ned Alle Funnet Filer");
        } else {
            // Gjør første bokstav stor, resten små, f.eks. "Bilder" -> "Bilder"
            String categoryDisplay = selectedCategory.substring(0, 1).toUpperCase() + selectedCategory.substring(1).toLowerCase();
            if (!categoryDisplay.endsWith("er") && !categoryDisplay.endsWith("a") && !categoryDisplay.endsWith("n")) { // Enkel pluralisering
                if (categoryDisplay.endsWith("r")) { // f.eks. Bilder
                    // behold som det er
                } else if (categoryDisplay.endsWith("kument")) { // Dokument -> Dokumenter
                    categoryDisplay += "er";
                }
                else {
                    categoryDisplay += "er"; // Bilder, Videoer, Lydfiler
                }
            }
            fetchButton.setText("Hent " + categoryDisplay);
            downloadButton.setText("Last Ned Valgte " + categoryDisplay);
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    private String getFileExtensionFromUrl(String urlString) {
        try {
            // Først, prøv å parse URL for å håndtere query parametere etc.
            URL url = new URL(urlString);
            String path = url.getPath();

            if (path != null && !path.isEmpty()) {
                // Hent den siste delen av stien
                String fileNameCandidate = path.substring(path.lastIndexOf('/') + 1);
                if (fileNameCandidate.contains(".")) {
                    String ext = fileNameCandidate.substring(fileNameCandidate.lastIndexOf('.')).toLowerCase();
                    // Enkel validering av at filtypen er "fornuftig" (ikke for lang, ingen rare tegn)
                    if (ext.length() > 1 && ext.length() <= 6 && ext.matches("\\.[a-z0-9]+")) {
                        return ext;
                    }
                }
            }

            // Hvis ingen filtype i path, sjekk query for 'format=' (vanlig på CDN)
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

            // Fallback: Hvis URL-strengen (ikke bare path) inneholder en "." før en eventuell query-string
            // Dette er mindre pålitelig, men kan fange noen tilfeller.
            String urlStrNoQuery = urlString.split("\\?")[0];
            if (urlStrNoQuery.contains(".")) {
                String potentialExt = urlStrNoQuery.substring(urlStrNoQuery.lastIndexOf('.')).toLowerCase();
                if (potentialExt.length() > 1 && potentialExt.length() <= 6 && potentialExt.matches("\\.[a-z0-9]+")) {
                    // Sjekk om dette er en kjent filtype for å unngå f.eks. ".com" fra domenet
                    boolean isKnown = CATEGORIZED_EXTENSIONS.values().stream().anyMatch(list -> list.contains(potentialExt));
                    if(isKnown) return potentialExt;
                }
            }

        } catch (MalformedURLException e) {
            // Kan skje hvis input ikke er en gyldig URL, men Jsoup bør fange det meste før.
            // Eller hvis en relativ URL blir forsøkt parset uten base.
            // For enkelhetens skyld, prøv en råere string-split hvis URL-parsing feiler:
            if (urlString.contains(".")) {
                String potentialExt = urlString.substring(urlString.lastIndexOf('.')).toLowerCase();
                if (potentialExt.length() > 1 && potentialExt.length() <= 6 && potentialExt.matches("\\.[a-z0-9]+")) {
                    boolean isKnown = CATEGORIZED_EXTENSIONS.values().stream().anyMatch(list -> list.contains(potentialExt));
                    if(isKnown) return potentialExt;
                }
            }
        }
        return null; // Ingen filtype funnet
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

        String selectedCategory = (String) categoryComboBox.getSelectedItem();
        log("Starter henting av " + (ALL_FILES_CATEGORY.equals(selectedCategory) ? "alle filer" : selectedCategory.toLowerCase()) + " fra: " + urlText);
        fetchButton.setEnabled(false);
        downloadButton.setEnabled(false);
        progressBar.setValue(0);
        fetchedFiles.clear();

        SwingWorker<List<FileInfo>, String> worker = new SwingWorker<>() {
            @Override
            protected List<FileInfo> doInBackground() throws Exception {
                List<FileInfo> foundFiles = new ArrayList<>();
                Set<String> uniqueFileUrls = new HashSet<>();

                publish("Kobler til " + urlText + "...");
                Document doc = Jsoup.connect(urlText)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .timeout(15000) // Økt timeout litt
                        .get();
                publish("Koblet til. Analyserer HTML...");

                // Hent fra <img>, <source>, <a> tags, og potensielt <video>, <audio> etc.
                // Vi må generalisere dette for å finne lenker som *kan* være filer.
                Elements links = doc.select("img[src], source[srcset], a[href], video[src], audio[src], track[src], embed[src], object[data]");
                // Man kan også vurdere å se etter linker i <link href="..."> (f.eks. for CSS-bakgrunnsbilder, fonter)

                for (Element link : links) {
                    String url = "";
                    if (link.hasAttr("src")) url = link.absUrl("src");
                    else if (link.hasAttr("srcset")) url = link.absUrl("srcset").split(",")[0].trim().split("\\s+")[0]; // Ta første fra srcset
                    else if (link.hasAttr("href")) url = link.absUrl("href");
                    else if (link.hasAttr("data")) url = link.absUrl("data");

                    if (url.isEmpty() || url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("javascript:")) continue;
                    if (url.contains("#")) { // Fjern anker/fragment
                        url = url.substring(0, url.indexOf('#'));
                    }


                    String extension = getFileExtensionFromUrl(url);

                    // Hvis "Alle filtyper" er valgt, og vi har en extension, legg til
                    // Ellers, sjekk mot valgte extensions i den valgte kategorien.
                    // (Denne filtreringen kan også skje senere, før nedlasting)

                    if (extension != null && !extension.isEmpty() && uniqueFileUrls.add(url)) {
                        FileInfo fileInfo = new FileInfo(url, extension);
                        foundFiles.add(fileInfo);
                        publish("Fant potensiell fil: " + url + " (type: " + fileInfo.detectedType + ", ext: " + fileInfo.extension +")");
                    } else if (extension == null && ALL_FILES_CATEGORY.equals(selectedCategory)) {
                        // Hvis "Alle filtyper" og ingen klar extension, men vi vil være "grådige"
                        // Kan legge til logikk her for å prøve å laste ned og sjekke Content-Type senere.
                        // For nå, ignorer de uten extension med mindre vi har en veldig god grunn.
                    }
                }
                return foundFiles;
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
                    fetchedFiles = get();
                    log("Fant " + fetchedFiles.size() + " unike fillenker med gjenkjennelige filtyper.");
                    if (!fetchedFiles.isEmpty()) {
                        downloadButton.setEnabled(true);
                        // Her kan du oppdatere sjekkboksene for å vise antall funnet per type,
                        // eller dynamisk legge til sjekkbokser for uventede filtyper hvis "Alle filtyper" var valgt.
                        updateDynamicCheckboxesWithCounts();
                    } else {
                        log("Ingen filer funnet som matcher kriteriene.");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log("Henting avbrutt: " + ex.getMessage());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log("Feil under henting: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
                    if (cause.getCause() != null) log("  Underliggende årsak: " + cause.getCause().getMessage());
                    cause.printStackTrace(); // For mer detaljert feilsøking i konsollen
                    JOptionPane.showMessageDialog(ImageDownloaderApp.this, "Feil under henting: " + cause.getMessage(), "Feil", JOptionPane.ERROR_MESSAGE);
                } finally {
                    fetchButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void updateDynamicCheckboxesWithCounts() {
        // Denne metoden bør oppdatere sjekkboksene i fileTypeSelectionPanel
        // for å vise antall funnet filer av hver type.
        // Hvis "Alle filtyper" var valgt, kan den også legge til nye sjekkbokser for filtyper
        // som ble funnet, men ikke var i de forhåndsdefinerte kategoriene.

        String selectedCategoryFilter = (String) categoryComboBox.getSelectedItem();
        fileTypeSelectionPanel.removeAll(); // Start på nytt

        Map<String, Long> counts = new HashMap<>();
        Map<String, String> extensionToCategoryMap = new HashMap<>(); // For å vite hvilken kategori en extension tilhører

        // Bygg map for raskt oppslag av kategori per extension
        for (Map.Entry<String, List<String>> catEntry : CATEGORIZED_EXTENSIONS.entrySet()) {
            for (String ext : catEntry.getValue()) {
                extensionToCategoryMap.put(ext, catEntry.getKey());
            }
        }

        // Tell forekomster
        for (FileInfo fi : fetchedFiles) {
            counts.put(fi.extension, counts.getOrDefault(fi.extension, 0L) + 1);
        }

        // Filtrer og vis sjekkbokser
        List<String> extensionsToShow = new ArrayList<>();
        if (ALL_FILES_CATEGORY.equals(selectedCategoryFilter)) {
            extensionsToShow.addAll(counts.keySet()); // Vis alle funnet extensions
        } else {
            List<String> categoryExtensions = CATEGORIZED_EXTENSIONS.get(selectedCategoryFilter);
            if (categoryExtensions != null) {
                for (String ext : categoryExtensions) {
                    if (counts.containsKey(ext)) { // Bare vis hvis vi faktisk fant noen
                        extensionsToShow.add(ext);
                    }
                }
                // Legg også til de som er funnet, men ikke var i listen, hvis de tilhører kategorien.
                // Dette er litt overlappende med "Alle filtyper", men kan være nyttig.
                // For nå, hold det enkelt: kun de definerte for kategorien.
            }
        }
        extensionsToShow.sort(String.CASE_INSENSITIVE_ORDER);

        if (extensionsToShow.isEmpty() && !fetchedFiles.isEmpty()){
            fileTypeSelectionPanel.add(new JLabel("Ingen filer av valgt(e) type(r) funnet."));
            fileTypeSelectionPanel.add(new JLabel("Men " + fetchedFiles.size() + " andre filer ble detektert."));
            fileTypeSelectionPanel.add(new JLabel("Prøv kategorien '" + ALL_FILES_CATEGORY + "'."));
        } else {
            for (String ext : extensionsToShow) {
                JCheckBox checkBox = new JCheckBox(ext + " (" + counts.get(ext) + ")", true);
                fileTypeSelectionPanel.add(checkBox);
            }
        }

        fileTypeSelectionPanel.revalidate();
        fileTypeSelectionPanel.repaint();
    }


    private void downloadFilesAction(ActionEvent e) {
        if (fetchedFiles.isEmpty()) {
            log("Ingen filer er hentet og klar for nedlasting.");
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

            Set<String> selectedExtensions = new HashSet<>();
            for (Component comp : fileTypeSelectionPanel.getComponents()) {
                if (comp instanceof JCheckBox) {
                    JCheckBox checkBox = (JCheckBox) comp;
                    if (checkBox.isSelected()) {
                        // Teksten er "ext (count)", så vi må trekke ut "ext"
                        String text = checkBox.getText();
                        String ext = text.substring(0, text.indexOf(" (")).trim();
                        selectedExtensions.add(ext);
                    }
                }
            }

            String currentCategory = (String) categoryComboBox.getSelectedItem();
            boolean downloadAllDetected = false;
            if (ALL_FILES_CATEGORY.equals(currentCategory)) {
                // Hvis "Alle filtyper" er valgt, og det er sjekkbokser (som er default nå), bruk dem.
                // Hvis vi hadde en spesiell "last ned alt" uten sjekkbokser for "Alle filtyper",
                // ville vi satt downloadAllDetected = true her.
                // Med nåværende logikk, vil selectedExtensions inneholde de avhukede.
                if (selectedExtensions.isEmpty() && !fetchedFiles.isEmpty()) {
                    // Hvis ingen sjekkbokser er valgt under "Alle filtyper", men vi har funnet filer,
                    // kan vi anta at brukeren vil ha alle som ble listet.
                    // Dette avhenger av designvalg. For nå, hvis ingenting er sjekket, lastes ingenting ned.
                }
            }


            if (selectedExtensions.isEmpty() && !fetchedFiles.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Ingen filformater er valgt for nedlasting.", "Advarsel", JOptionPane.WARNING_MESSAGE);
                log("Ingen filformater valgt. Avbryter nedlasting.");
                return;
            }

            List<FileInfo> filesToDownload = new ArrayList<>();
            for (FileInfo fi : fetchedFiles) {
                if (selectedExtensions.contains(fi.extension)) {
                    filesToDownload.add(fi);
                }
            }

            if (filesToDownload.isEmpty()){
                log("Ingen filer samsvarte med valgte formater for nedlasting.");
                if(!fetchedFiles.isEmpty()) {
                    log("Det ble funnet " + fetchedFiles.size() + " filer totalt, men ingen av de valgte typene.");
                }
                return;
            }


            log("Starter nedlasting av " + filesToDownload.size() + " filer...");
            downloadButton.setEnabled(false);
            fetchButton.setEnabled(false);
            progressBar.setValue(0);
            progressBar.setMaximum(filesToDownload.size());


            SwingWorker<Integer, Object[]> downloadWorker = new SwingWorker<>() { // Object[] for å sende både melding og progress
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
                            connection.setConnectTimeout(10000); // 10s
                            connection.setReadTimeout(30000); // 30s

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

                            long totalBytes = connection.getContentLengthLong(); // For mer nøyaktig progress
                            long downloadedBytes = 0;

                            try (InputStream in = connection.getInputStream();
                                 FileOutputStream out = new FileOutputStream(outputFile)) {
                                byte[] buffer = new byte[8192]; // Større buffer
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    if (isCancelled()) {
                                        out.close();
                                        Files.deleteIfExists(outputFile.toPath()); // Slett ufullstendig fil
                                        publish(new Object[]{"Nedlasting avbrutt for: " + outputFile.getName(), currentProgress});
                                        throw new InterruptedException("Download cancelled by user");
                                    }
                                    out.write(buffer, 0, bytesRead);
                                    downloadedBytes += bytesRead;
                                    // Kan legge til sub-progress her hvis ønskelig (f.eks. for store filer)
                                }
                            }
                            successfulDownloads++;
                            publish(new Object[]{"Lastet ned: " + outputFile.getName(), currentProgress + 1});
                        } catch (IOException ex) {
                            publish(new Object[]{"Feil ved nedlasting av " + fileInfo.url + ": " + ex.getMessage(), currentProgress + 1});
                        } catch (InterruptedException ex) {
                            // Håndtert i done()
                            Thread.currentThread().interrupt();
                            break;
                        }
                        currentProgress++;
                        // setProgress(currentProgress); // SwingWorker's egen progress for JProgressBar
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
                        downloadButton.setEnabled(true);
                        fetchButton.setEnabled(true);
                        progressBar.setValue(progressBar.getMaximum());
                    }
                }
            };

            // Koble SwingWorker's progress til JProgressBar (hvis du bruker setProgress internt)
            // downloadWorker.addPropertyChangeListener(evt -> {
            //     if ("progress".equals(evt.getPropertyName())) {
            //         progressBar.setValue((Integer) evt.getNewValue());
            //     }
            // });

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