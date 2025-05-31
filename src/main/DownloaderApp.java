// DownloaderApp.java
package main;


import main.model.FileCategory;
import main.model.FileInfo;
import main.ui.FilesTableModel;
import main.util.UrlUtil; // Importer UrlUtil

// Standard Swing og Java imports
import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Jsoup import
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Hovedapplikasjonsklassen for filnedlasteren.
 */
public class DownloaderApp extends JFrame {

    private JTextField urlField;
    private JComboBox<String> categoryComboBox;
    private JButton fetchButton, downloadButton, selectAllButton, selectNoneButton;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JFileChooser directoryChooser;
    private transient List<FileInfo> fetchedFilesList;
    private JTable filesTable;
    private FilesTableModel tableModel;
    private File saveDirectory;
    private JPanel extensionSelectionPanel;
    private transient List<JCheckBox> currentExtensionCheckBoxes = new ArrayList<>();

    public DownloaderApp() {
        setTitle("Avansert Filnedlaster Pro");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Kunne ikke sette system look and feel: " + e.getMessage());
        }

        fetchedFilesList = new ArrayList<>();
        categoryComboBox = new JComboBox<>(FileCategory.CATEGORIZED_EXTENSIONS.keySet().toArray(new String[0]));
        categoryComboBox.addItem(FileCategory.ALL_FILES_CATEGORY);
        categoryComboBox.setSelectedItem("Bilder");

        tableModel = new FilesTableModel(fetchedFilesList, categoryComboBox, this::updateDownloadButtonStateAndExtensionCheckboxes);

        initComponents();
    }

    private void initComponents() {
        JPanel topInputPanel = new JPanel(new BorderLayout(10, 5));
        topInputPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
        String placeholderText = "Skriv inn URL her...";
        urlField = new JTextField(placeholderText);
        urlField.setForeground(Color.GRAY);
        urlField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { if (urlField.getText().equals(placeholderText)) { urlField.setText(""); urlField.setForeground(Color.BLACK); } }
            @Override public void focusLost(FocusEvent e) { if (urlField.getText().isEmpty()) { urlField.setForeground(Color.GRAY); urlField.setText(placeholderText); } }
        });
        urlField.setToolTipText("Nettsideadressen du vil hente filer fra");
        topInputPanel.add(urlField, BorderLayout.CENTER);
        JPanel categoryAndFetchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5,0));
        categoryComboBox.setToolTipText("Velg filkategori for forhåndsfiltrering");
        categoryComboBox.addActionListener(e -> {
            tableModel.filterAndSelectBasedOnCategory();
            updateExtensionCheckboxes();
        });
        categoryAndFetchPanel.add(categoryComboBox);
        fetchButton = new JButton("Analyser URL");
        fetchButton.setToolTipText("Hent og vis fillenker fra URL");
        fetchButton.addActionListener(this::fetchFileLinksAction);
        categoryAndFetchPanel.add(fetchButton);
        topInputPanel.add(categoryAndFetchPanel, BorderLayout.EAST);
        add(topInputPanel, BorderLayout.NORTH);

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
        selectAllButton.addActionListener(e -> setExtensionCheckBoxesState(true));
        selectionButtonsPanel.add(selectAllButton);
        selectNoneButton = new JButton("Velg Ingen Viste Typer");
        selectNoneButton.addActionListener(e -> setExtensionCheckBoxesState(false));
        selectionButtonsPanel.add(selectNoneButton);
        bottomControlsForTablePanel.add(selectionButtonsPanel, BorderLayout.NORTH);
        extensionSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JScrollPane extensionScrollPane = new JScrollPane(extensionSelectionPanel);
        extensionScrollPane.setBorder(BorderFactory.createTitledBorder("Filtrer på Filtype"));
        extensionScrollPane.setPreferredSize(new Dimension(100, 100));
        bottomControlsForTablePanel.add(extensionScrollPane, BorderLayout.CENTER);
        centerPanel.add(bottomControlsForTablePanel, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomOuterPanel = new JPanel(new BorderLayout(10,5));
        bottomOuterPanel.setBorder(new EmptyBorder(5,10,10,10));
        logArea = new JTextArea(8, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        ((DefaultCaret)logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
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
        updateDownloadButtonStateAndExtensionCheckboxes();
    }

    private void updateDownloadButtonStateAndExtensionCheckboxes() {
        updateDownloadButtonState();
        updateExtensionCheckboxes();
    }

    private void setupTableColumns() {
        TableColumn selectCol = filesTable.getColumnModel().getColumn(0); selectCol.setPreferredWidth(50); selectCol.setMaxWidth(70); selectCol.setMinWidth(40);
        TableColumn urlCol = filesTable.getColumnModel().getColumn(1); urlCol.setPreferredWidth(500);
        TableColumn typeCol = filesTable.getColumnModel().getColumn(2); typeCol.setPreferredWidth(120);
        TableColumn extCol = filesTable.getColumnModel().getColumn(3); extCol.setPreferredWidth(100);
    }

    private void updateDownloadButtonState() {
        boolean anySelected = fetchedFilesList.stream().anyMatch(FileInfo::isSelected);
        downloadButton.setEnabled(anySelected && !fetchedFilesList.isEmpty());
        boolean hasExtCb = !currentExtensionCheckBoxes.isEmpty();
        selectAllButton.setEnabled(hasExtCb);
        selectNoneButton.setEnabled(hasExtCb);
    }

    private void log(String message) { SwingUtilities.invokeLater(() -> logArea.append(message + "\n")); }
    private void setExtensionCheckBoxesState(boolean selected) { currentExtensionCheckBoxes.forEach(cb -> cb.setSelected(selected)); }

    private void updateExtensionCheckboxes() {
        extensionSelectionPanel.removeAll(); currentExtensionCheckBoxes.clear();
        String selCat = (String) categoryComboBox.getSelectedItem();
        if (selCat == null || fetchedFilesList.isEmpty()) {
            extensionSelectionPanel.revalidate(); extensionSelectionPanel.repaint(); updateDownloadButtonState(); return;
        }
        Set<String> relExts = new HashSet<>();
        for (FileInfo fi : fetchedFilesList) {
            if (fi.getExtension() != null && !fi.getExtension().isEmpty()) {
                if (FileCategory.ALL_FILES_CATEGORY.equals(selCat) ||
                        (FileCategory.CATEGORIZED_EXTENSIONS.get(selCat) != null && FileCategory.CATEGORIZED_EXTENSIONS.get(selCat).contains(fi.getExtension()))) {
                    relExts.add(fi.getExtension());
                }
            }
        }
        List<String> sortedExts = new ArrayList<>(relExts); sortedExts.sort(String.CASE_INSENSITIVE_ORDER);
        if (sortedExts.isEmpty()) {
            extensionSelectionPanel.add(new JLabel("Ingen filtyper å filtrere på.") {{ setForeground(Color.GRAY); }});
        } else {
            for (String ext : sortedExts) {
                boolean isAnySelForExt = false;
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (ext.equals(tableModel.getValueAt(i, 3)) && (Boolean)tableModel.getValueAt(i, 0)) { isAnySelForExt = true; break; }
                }
                JCheckBox extCb = new JCheckBox(ext, isAnySelForExt);
                extCb.addActionListener(e -> {
                    boolean makeSel = extCb.isSelected();
                    for (int i = 0; i < tableModel.getRowCount(); i++) {
                        if (ext.equals(tableModel.getValueAt(i, 3)) && (Boolean)tableModel.getValueAt(i, 0) != makeSel) {
                            tableModel.setValueAt(makeSel, i, 0);
                        }
                    }
                });
                extensionSelectionPanel.add(extCb); currentExtensionCheckBoxes.add(extCb);
            }
        }
        updateDownloadButtonState(); extensionSelectionPanel.revalidate(); extensionSelectionPanel.repaint();
    }

    private void fetchFileLinksAction(ActionEvent e) {
        String urlText = urlField.getText().trim();
        if (urlText.isEmpty() || urlText.equals("Skriv inn URL her...")) { JOptionPane.showMessageDialog(this, "URL tom.", "Feil", JOptionPane.ERROR_MESSAGE); return; }
        try { new URL(urlText); } catch (MalformedURLException ex) { JOptionPane.showMessageDialog(this, "Ugyldig URL: " + ex.getMessage(), "Feil", JOptionPane.ERROR_MESSAGE); return; }
        log("Starter analyse: " + urlText);
        fetchButton.setEnabled(false); downloadButton.setEnabled(false); selectAllButton.setEnabled(false); selectNoneButton.setEnabled(false); progressBar.setValue(0);
        SwingWorker<List<FileInfo>, String> worker = new SwingWorker<>() {
            @Override protected List<FileInfo> doInBackground() throws Exception {
                List<FileInfo> found = new ArrayList<>(); Set<String> uniqueUrls = new HashSet<>();
                publish("Kobler til: " + urlText);
                Document doc = Jsoup.connect(urlText).userAgent("Mozilla/5.0 ... Chrome/98...").timeout(20000).ignoreHttpErrors(true).followRedirects(true).get();
                if (doc.connection().response().statusCode() >= 400) publish("Advarsel: Status " + doc.connection().response().statusCode());
                publish("HTML Analyse...");
                Elements links = doc.select("a[href], img[src], source[srcset], video[src], audio[src], track[src], embed[src], object[data], link[href]");
                for (Element link : links) {
                    String url = "";
                    if (link.hasAttr("href")) url = link.absUrl("href"); else if (link.hasAttr("src")) url = link.absUrl("src");
                    else if (link.hasAttr("srcset")) url = link.absUrl("srcset").split(",")[0].trim().split("\\s+")[0]; else if (link.hasAttr("data")) url = link.absUrl("data");
                    if (url.isEmpty() || url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("javascript:") || url.startsWith("#")) continue;
                    if (url.contains("#")) url = url.substring(0, url.indexOf('#'));
                    String ext = UrlUtil.getFileExtensionFromUrl(url); boolean isPotFile = (ext != null && !ext.isEmpty());
                    if ("link".equals(link.tagName().toLowerCase())) { String rel = link.attr("rel").toLowerCase();
                        if (!("stylesheet".equals(rel)||"icon".equals(rel)||"shortcut icon".equals(rel)||"apple-touch-icon".equals(rel)||"preload".equals(rel)||"manifest".equals(rel)||("alternate".equals(rel)&&(url.endsWith(".xml")||url.endsWith(".rss")||url.endsWith(".atom"))))) { if(!isPotFile) continue; }
                    }
                    if (isPotFile && uniqueUrls.add(url)) { found.add(new FileInfo(url, ext)); publish("Fant: " + url); }
                } return found;
            }
            @Override protected void process(List<String> chunks) { chunks.forEach(DownloaderApp.this::log); }
            @Override protected void done() {
                try { List<FileInfo> res = get(); tableModel.setData(res); log("Analyse ferdig. Fant " + res.size() + " filer."); if (res.isEmpty()) log("Ingen filer funnet.");
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); log("Analyse avbrutt.");
                } catch (ExecutionException ex) { Throwable c = ex.getCause()!=null?ex.getCause():ex; log("Feil analyse: "+c.getMessage()); JOptionPane.showMessageDialog(DownloaderApp.this, "Feil: "+c.getMessage(),"Feil", JOptionPane.ERROR_MESSAGE);
                } finally { fetchButton.setEnabled(true); updateDownloadButtonStateAndExtensionCheckboxes(); }
            }
        }; worker.execute();
    }

    private void downloadFilesAction(ActionEvent e) {
        List<FileInfo> toDl = new ArrayList<>(); fetchedFilesList.stream().filter(FileInfo::isSelected).forEach(toDl::add);
        if (toDl.isEmpty()) { JOptionPane.showMessageDialog(this, "Ingen filer valgt.", "Advarsel", JOptionPane.WARNING_MESSAGE); return; }
        if (directoryChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            saveDirectory = directoryChooser.getSelectedFile(); if (!saveDirectory.exists()&&!saveDirectory.mkdirs()) { JOptionPane.showMessageDialog(this, "Kan ikke lage mappe.", "Feil", JOptionPane.ERROR_MESSAGE); return; }
            log("Lagrer til: " + saveDirectory.getAbsolutePath() + ". Laster ned " + toDl.size() + " filer...");
            downloadButton.setEnabled(false); fetchButton.setEnabled(false); selectAllButton.setEnabled(false); selectNoneButton.setEnabled(false); progressBar.setValue(0); progressBar.setMaximum(toDl.size());
            SwingWorker<Integer,Object[]> dlWorker = new SwingWorker<>() {
                int okDl = 0;
                @Override protected Integer doInBackground() throws Exception {
                    int prog = 0;
                    for (FileInfo fi : toDl) { if(isCancelled())break; HttpURLConnection con=null;
                        try { publish(new Object[]{"Laster ned: "+fi.getUrl(),prog}); URL u=new URL(fi.getUrl()); con=(HttpURLConnection)u.openConnection();
                            con.setRequestProperty("User-Agent","Mozilla/5.0 ..."); con.setConnectTimeout(15000); con.setReadTimeout(60000); con.setInstanceFollowRedirects(true);
                            int rc=con.getResponseCode(); if(rc>=400){publish(new Object[]{"Feil ("+rc+") "+fi.getUrl(),prog+1}); prog++; continue;}
                            String fnHead=null; String cd=con.getHeaderField("Content-Disposition"); if(cd!=null){Matcher m=Pattern.compile("filename\\*?=['\"]?([^'\"]+)['\"]?",Pattern.CASE_INSENSITIVE).matcher(cd); if(m.find()){fnHead=m.group(1); if(fnHead.toLowerCase().startsWith("utf-8''"))fnHead=java.net.URLDecoder.decode(fnHead.substring(7),"UTF-8");}}
                            String fn=(fnHead!=null&&!fnHead.trim().isEmpty())?fnHead:Paths.get(u.getPath()).getFileName().toString();
                            if(fn.isEmpty()||(!fn.contains(".")&&fi.getExtension()!=null&&!fi.getExtension().isEmpty()))fn="f_"+System.currentTimeMillis()+(fi.getExtension()!=null?fi.getExtension():".dat");
                            fn=fn.replaceAll("[^a-zA-Z0-9._\\- ()]","_").replaceAll("_+","_").trim(); if(fn.isEmpty())fn="f_"+System.currentTimeMillis()+(fi.getExtension()!=null?fi.getExtension():".dat");
                            File oF=new File(saveDirectory,fn); int sfx=1; String bN=fn.contains(".")?fn.substring(0,fn.lastIndexOf('.')):fn; String eP=fn.contains(".")?fn.substring(fn.lastIndexOf('.')):""; while(oF.exists())oF=new File(saveDirectory,bN+"_"+sfx+++eP);
                            try(InputStream iS=con.getInputStream();FileOutputStream fOS=new FileOutputStream(oF)){byte[]buf=new byte[8192];int br; while((br=iS.read(buf))!=-1){if(isCancelled()){fOS.close();if(oF.exists())Files.delete(oF.toPath());throw new InterruptedException();}fOS.write(buf,0,br);}}
                            okDl++;publish(new Object[]{"OK: "+oF.getName(),prog+1});
                        }catch(IOException ex){publish(new Object[]{"Feil DL "+fi.getUrl()+": "+ex.getMessage(),prog+1});
                        }catch(InterruptedException ex){Thread.currentThread().interrupt();break;
                        }finally{if(con!=null)con.disconnect();} prog++;
                    } return okDl;
                }
                @Override protected void process(List<Object[]>cs){for(Object[]c:cs){log((String)c[0]);progressBar.setValue((Integer)c[1]);}}
                @Override protected void done(){
                    try{int n=get();JOptionPane.showMessageDialog(DownloaderApp.this,"DL ferdig. "+n+" filer.","Ferdig",JOptionPane.INFORMATION_MESSAGE);
                    }catch(InterruptedException ex){Thread.currentThread().interrupt();JOptionPane.showMessageDialog(DownloaderApp.this,"DL avbrutt.","Avbrutt",JOptionPane.WARNING_MESSAGE);
                    }catch(ExecutionException ex){JOptionPane.showMessageDialog(DownloaderApp.this,"Feil DL: "+ex.getCause().getMessage(),"Feil",JOptionPane.ERROR_MESSAGE);
                    }finally{fetchButton.setEnabled(true);progressBar.setValue(progressBar.getMaximum());updateDownloadButtonStateAndExtensionCheckboxes();}
                }
            }; dlWorker.execute();
        } else { log("DL kansellert."); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DownloaderApp().setVisible(true));
    }
}