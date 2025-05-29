// ImageDownloaderApp.java
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ImageDownloaderApp extends JFrame {

    private JTextField urlField;
    private JCheckBox pngCheckBox, jpgCheckBox, svgCheckBox, webpCheckBox, allImagesCheckBox;
    private JButton fetchButton, downloadButton;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JFileChooser directoryChooser;
    private transient List<ImageInfo> fetchedImages; // transient for å unngå serialiseringsproblemer med SwingWorker hvis det skulle oppstå
    private File saveDirectory;

    private static final List<String> DEFAULT_EXTENSIONS = Arrays.asList(".png", ".jpg", ".jpeg", ".svg", ".webp", ".gif", ".bmp", ".tiff");

    // Enkel klasse for å holde på bildeinformasjon
    private static class ImageInfo {
        String url;
        String extension;
        boolean selected = true; // Standard er valgt

        ImageInfo(String url, String extension) {
            this.url = url;
            this.extension = extension.toLowerCase();
        }

        @Override
        public String toString() {
            return url; // For visning i lister, etc.
        }
    }

    public ImageDownloaderApp() {
        setTitle("Avansert Filnedlaster");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null); // Senterer vinduet
        setLayout(new BorderLayout(10, 10)); // Hovedlayout med mellomrom

        // Stil (enkel "look and feel" for bedre utseende)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Kunne ikke sette system look and feel: " + e.getMessage());
        }

        fetchedImages = new ArrayList<>();
        initComponents();
    }

    private void initComponents() {
        // ----- Toppanel: URL-input og Hent-knapp -----
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        urlField = new JTextField("https://www.worldometers.info/geography/flags-of-the-world/");
        urlField.setToolTipText("Skriv inn URL her");
        topPanel.add(urlField, BorderLayout.CENTER);

        fetchButton = new JButton("Hent Bilder");
        fetchButton.setToolTipText("Hent bildelinker fra URL");
        fetchButton.addActionListener(this::fetchImageLinksAction);
        topPanel.add(fetchButton, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // ----- Senterpanel: Valg og Logg -----
        JPanel centerPanel = new JPanel(new BorderLayout(10,10));
        centerPanel.setBorder(new EmptyBorder(0, 10, 0, 10));

        // ----- Valgpanel (for bildeformater) -----
        JPanel selectionPanel = new JPanel();
        selectionPanel.setLayout(new BoxLayout(selectionPanel, BoxLayout.Y_AXIS)); // Vertikal layout
        selectionPanel.setBorder(BorderFactory.createTitledBorder("Velg Bildeformater"));

        pngCheckBox = new JCheckBox(".png", true);
        jpgCheckBox = new JCheckBox(".jpg/.jpeg", true);
        svgCheckBox = new JCheckBox(".svg", true);
        webpCheckBox = new JCheckBox(".webp", true);
        allImagesCheckBox = new JCheckBox("Alle bildetyper (inkl. .gif, .bmp etc.)", false);

        allImagesCheckBox.addActionListener(e -> {
            boolean selected = allImagesCheckBox.isSelected();
            pngCheckBox.setSelected(selected);
            jpgCheckBox.setSelected(selected);
            svgCheckBox.setSelected(selected);
            webpCheckBox.setSelected(selected);
            // Kan også deaktivere de andre boksene hvis "Alle" er valgt
            pngCheckBox.setEnabled(!selected);
            jpgCheckBox.setEnabled(!selected);
            svgCheckBox.setEnabled(!selected);
            webpCheckBox.setEnabled(!selected);
        });

        // For å resette "Alle" hvis en individuell boks endres
        Action listener = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!pngCheckBox.isSelected() || !jpgCheckBox.isSelected() ||
                        !svgCheckBox.isSelected() || !webpCheckBox.isSelected()) {
                    allImagesCheckBox.setSelected(false);
                }
                if (pngCheckBox.isSelected() && jpgCheckBox.isSelected() &&
                        svgCheckBox.isSelected() && webpCheckBox.isSelected()) {
                    // Denne logikken blir litt feil om man bare har de fire og "alle" skal gjelde flere.
                    // Man kan vurdere å la "Alle" overstyre og at de andre bare er for finere kontroll.
                }
            }
        };
        pngCheckBox.addActionListener(listener);
        jpgCheckBox.addActionListener(listener);
        svgCheckBox.addActionListener(listener);
        webpCheckBox.addActionListener(listener);


        selectionPanel.add(pngCheckBox);
        selectionPanel.add(jpgCheckBox);
        selectionPanel.add(svgCheckBox);
        selectionPanel.add(webpCheckBox);
        selectionPanel.add(Box.createVerticalStrut(10)); // Litt mellomrom
        selectionPanel.add(allImagesCheckBox);

        centerPanel.add(selectionPanel, BorderLayout.WEST);

        // ----- Loggpanel -----
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        DefaultCaret caret = (DefaultCaret)logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // Auto-scroll
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Logg"));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // ----- Bunnpanel: Nedlastingsknapp og Progressbar -----
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        downloadButton = new JButton("Last Ned Valgte Bilder");
        downloadButton.setEnabled(false); // Aktiveres etter henting
        downloadButton.setToolTipText("Start nedlasting av valgte bilder til valgt mappe");
        downloadButton.addActionListener(this::downloadImagesAction);
        bottomPanel.add(downloadButton, BorderLayout.EAST);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        bottomPanel.add(progressBar, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        directoryChooser.setDialogTitle("Velg Mappe for Nedlasting");
    }

    private void log(String message) {
        logArea.append(message + "\n");
    }

    private void fetchImageLinksAction(ActionEvent e) {
        String urlText = urlField.getText().trim();
        if (urlText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "URL kan ikke være tom.", "Feil", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            new URL(urlText); // Valider URL-format
        } catch (MalformedURLException ex) {
            JOptionPane.showMessageDialog(this, "Ugyldig URL-format: " + ex.getMessage(), "Feil", JOptionPane.ERROR_MESSAGE);
            return;
        }

        log("Starter henting av bilder fra: " + urlText);
        fetchButton.setEnabled(false);
        downloadButton.setEnabled(false);
        progressBar.setValue(0);
        fetchedImages.clear();

        // Bruk SwingWorker for å ikke fryse GUI
        SwingWorker<List<ImageInfo>, String> worker = new SwingWorker<>() {
            @Override
            protected List<ImageInfo> doInBackground() throws Exception {
                List<ImageInfo> foundImages = new ArrayList<>();
                Set<String> uniqueImageUrls = new HashSet<>(); // For å unngå duplikater

                publish("Kobler til " + urlText + "...");
                Document doc = Jsoup.connect(urlText)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .timeout(10000) // 10 sekunder timeout
                        .get();
                publish("Koblet til. Analyserer HTML...");

                // Hent bilder fra <img> tags
                Elements images = doc.select("img[src]");
                for (Element img : images) {
                    String src = img.absUrl("src"); // Få absolutt URL
                    if (src.isEmpty()) continue;

                    String extension = getFileExtension(src);
                    if (extension != null && uniqueImageUrls.add(src)) {
                        foundImages.add(new ImageInfo(src, extension));
                        publish("Fant bilde: " + src);
                    }
                }

                // Hent bilder fra <source> tags (ofte inni <picture>)
                Elements sources = doc.select("source[srcset]");
                for (Element source : sources) {
                    String srcset = source.absUrl("srcset");
                    if (srcset.isEmpty()) continue;
                    // srcset kan inneholde flere URLer med deskriptorer, vi tar den første for enkelhets skyld
                    String[] urls = srcset.split(",")[0].trim().split("\\s+");
                    if (urls.length > 0) {
                        String src = urls[0];
                        String extension = getFileExtension(src);
                        if (extension != null && uniqueImageUrls.add(src)) {
                            foundImages.add(new ImageInfo(src, extension));
                            publish("Fant bilde (source): " + src);
                        }
                    }
                }

                // Man kan også vurdere å lete etter bilder i style-attributter (background-image)
                // Dette er mer komplekst og krever regex på style-innhold.

                return foundImages;
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
                    fetchedImages = get();
                    log("Fant " + fetchedImages.size() + " unike bildelinker.");
                    if (!fetchedImages.isEmpty()) {
                        downloadButton.setEnabled(true);
                        updateCheckboxesBasedOnFetchedImages();
                    } else {
                        log("Ingen bilder funnet med gjenkjennelige filtyper.");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); // Gjenopprett avbruddsstatus
                    log("Henting avbrutt: " + ex.getMessage());
                    JOptionPane.showMessageDialog(ImageDownloaderApp.this, "Henting avbrutt.", "Avbrutt", JOptionPane.WARNING_MESSAGE);
                } catch (ExecutionException ex) {
                    log("Feil under henting: " + ex.getCause().getMessage());
                    JOptionPane.showMessageDialog(ImageDownloaderApp.this, "Feil under henting: " + ex.getCause().getMessage(), "Feil", JOptionPane.ERROR_MESSAGE);
                } finally {
                    fetchButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void updateCheckboxesBasedOnFetchedImages() {
        // Denne metoden kan utvides til å dynamisk lage sjekkbokser basert på funn,
        // eller bare aktivere/deaktivere eksisterende basert på om typene finnes.
        // For nå lar vi de eksisterende være, men du kan legge til logikk her.
        // F.eks. telle hvor mange av hver type som ble funnet og vise det.
        long pngCount = fetchedImages.stream().filter(img -> img.extension.equals(".png")).count();
        long jpgCount = fetchedImages.stream().filter(img -> img.extension.equals(".jpg") || img.extension.equals(".jpeg")).count();
        long svgCount = fetchedImages.stream().filter(img -> img.extension.equals(".svg")).count();
        long webpCount = fetchedImages.stream().filter(img -> img.extension.equals(".webp")).count();

        pngCheckBox.setText(".png (" + pngCount + ")");
        jpgCheckBox.setText(".jpg/.jpeg (" + jpgCount + ")");
        svgCheckBox.setText(".svg (" + svgCount + ")");
        webpCheckBox.setText(".webp (" + webpCount + ")");

        // Aktiver/deaktiver basert på om noen ble funnet
        pngCheckBox.setEnabled(pngCount > 0 || allImagesCheckBox.isSelected());
        jpgCheckBox.setEnabled(jpgCount > 0 || allImagesCheckBox.isSelected());
        svgCheckBox.setEnabled(svgCount > 0 || allImagesCheckBox.isSelected());
        webpCheckBox.setEnabled(webpCount > 0 || allImagesCheckBox.isSelected());

        if (allImagesCheckBox.isSelected()){
            pngCheckBox.setEnabled(false);
            jpgCheckBox.setEnabled(false);
            svgCheckBox.setEnabled(false);
            webpCheckBox.setEnabled(false);
        }
    }


    private String getFileExtension(String urlString) {
        try {
            URL url = new URL(urlString);
            String path = url.getPath();
            if (path == null || path.isEmpty() || !path.contains(".")) {
                // Prøv å se etter 'format=' i query-parametere for noen CDN-er
                String query = url.getQuery();
                if (query != null) {
                    Pattern pattern = Pattern.compile("format=([^&]+)");
                    Matcher matcher = pattern.matcher(query);
                    if (matcher.find()) {
                        return "." + matcher.group(1).toLowerCase();
                    }
                }
                return null; // Ingen åpenbar filtype
            }
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (fileName.contains(".")) {
                return fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
            }
        } catch (MalformedURLException e) {
            // Ignorer, kan skje med databaserte URLer eller lignende
        }
        return null; // Ingen filtype funnet
    }

    private void downloadImagesAction(ActionEvent e) {
        if (fetchedImages.isEmpty()) {
            log("Ingen bilder å laste ned.");
            return;
        }

        if (directoryChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            saveDirectory = directoryChooser.getSelectedFile();
            if (!saveDirectory.exists()) {
                saveDirectory.mkdirs();
            }
            log("Valgt mappe for nedlasting: " + saveDirectory.getAbsolutePath());

            List<ImageInfo> imagesToDownload = new ArrayList<>();
            Set<String> selectedExtensions = new HashSet<>();

            if (allImagesCheckBox.isSelected()) {
                selectedExtensions.addAll(DEFAULT_EXTENSIONS); // Legg til alle kjente
                // For "alle" kan vi også vurdere å laste ned alle med *ukjent* men gyldig bilde-URL
                // Men for nå holder vi oss til de vi har definert.
            } else {
                if (pngCheckBox.isSelected()) selectedExtensions.add(".png");
                if (jpgCheckBox.isSelected()) { selectedExtensions.add(".jpg"); selectedExtensions.add(".jpeg");}
                if (svgCheckBox.isSelected()) selectedExtensions.add(".svg");
                if (webpCheckBox.isSelected()) selectedExtensions.add(".webp");
            }

            if (selectedExtensions.isEmpty() && !allImagesCheckBox.isSelected()){
                JOptionPane.showMessageDialog(this, "Ingen bildeformater er valgt.", "Advarsel", JOptionPane.WARNING_MESSAGE);
                return;
            }


            for (ImageInfo img : fetchedImages) {
                if (allImagesCheckBox.isSelected() || selectedExtensions.contains(img.extension)) {
                    imagesToDownload.add(img);
                }
            }

            if (imagesToDownload.isEmpty()){
                log("Ingen bilder samsvarte med valgte formater.");
                return;
            }


            log("Starter nedlasting av " + imagesToDownload.size() + " bilder...");
            downloadButton.setEnabled(false);
            fetchButton.setEnabled(false);
            progressBar.setValue(0);
            progressBar.setMaximum(imagesToDownload.size());


            // Bruk SwingWorker for nedlasting
            SwingWorker<Integer, String> downloadWorker = new SwingWorker<>() {
                private int successfulDownloads = 0;

                @Override
                protected Integer doInBackground() throws Exception {
                    int count = 0;
                    for (ImageInfo imgInfo : imagesToDownload) {
                        if (isCancelled()) break;

                        try {
                            publish("Laster ned: " + imgInfo.url);
                            URL imageUrl = new URL(imgInfo.url);
                            URLConnection connection = imageUrl.openConnection();
                            // Sett user-agent for å unngå 403-feil fra noen servere
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

                            // Prøv å få et fornuftig filnavn
                            String fileName = Paths.get(imageUrl.getPath()).getFileName().toString();
                            if (fileName.isEmpty() || !fileName.contains(".")) { // Hvis URL ikke slutter på filnavn (f.eks. /image/id123)
                                fileName = "image_" + System.currentTimeMillis() + (imgInfo.extension != null ? imgInfo.extension : ".tmp");
                            }
                            // Fjern ugyldige tegn fra filnavn (enkelt eksempel)
                            fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");


                            File outputFile = new File(saveDirectory, fileName);
                            // Håndter duplikat filnavn
                            int fileSuffix = 1;
                            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                            String extension = fileName.substring(fileName.lastIndexOf('.'));
                            while(outputFile.exists()){
                                outputFile = new File(saveDirectory, baseName + "_" + fileSuffix++ + extension);
                            }


                            try (InputStream in = connection.getInputStream();
                                 FileOutputStream out = new FileOutputStream(outputFile)) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, bytesRead);
                                }
                            }
                            successfulDownloads++;
                            publish("Lastet ned: " + outputFile.getName());
                        } catch (IOException ex) {
                            publish("Feil ved nedlasting av " + imgInfo.url + ": " + ex.getMessage());
                        }
                        count++;
                        setProgress(count); // Oppdaterer intern progress for SwingWorker
                    }
                    return successfulDownloads;
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String message : chunks) {
                        log(message);
                    }
                    // Oppdater progressbar basert på SwingWorkers interne progress
                    progressBar.setValue(getProgress());
                }

                @Override
                protected void done() {
                    try {
                        int downloadedCount = get();
                        log("Nedlasting fullført. " + downloadedCount + " av " + imagesToDownload.size() + " bilder ble lastet ned.");
                        JOptionPane.showMessageDialog(ImageDownloaderApp.this,
                                "Nedlasting fullført.\n" + downloadedCount + " bilder lastet ned til:\n" + saveDirectory.getAbsolutePath(),
                                "Fullført", JOptionPane.INFORMATION_MESSAGE);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        log("Nedlasting avbrutt.");
                        JOptionPane.showMessageDialog(ImageDownloaderApp.this, "Nedlasting avbrutt.", "Avbrutt", JOptionPane.WARNING_MESSAGE);
                    } catch (ExecutionException ex) {
                        log("Feil under nedlasting: " + ex.getCause().getMessage());
                        JOptionPane.showMessageDialog(ImageDownloaderApp.this, "Feil under nedlasting: " + ex.getCause().getMessage(), "Feil", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        downloadButton.setEnabled(true);
                        fetchButton.setEnabled(true);
                        progressBar.setValue(progressBar.getMaximum()); // Eller 0 hvis du foretrekker det etter fullføring
                    }
                }
            };

            // For å koble SwingWorker's progress til JProgressBar
            downloadWorker.addPropertyChangeListener(evt -> {
                if ("progress".equals(evt.getPropertyName())) {
                    progressBar.setValue((Integer) evt.getNewValue());
                }
            });

            downloadWorker.execute();

        } else {
            log("Nedlasting kansellert av bruker.");
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ImageDownloaderApp app = new ImageDownloaderApp();
            app.setVisible(true);
        });
    }
}