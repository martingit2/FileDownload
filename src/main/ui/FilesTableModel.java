// FilesTableModel.java
package main.ui;


import main.model.FileCategory;
import main.model.FileInfo;

import javax.swing.table.AbstractTableModel;
import javax.swing.JComboBox;
import java.util.List;

/**
 * Egendefinert TableModel for JTable.
 */
public class FilesTableModel extends AbstractTableModel {
    private final List<FileInfo> files;
    private final String[] columnNames = {"Velg", "Fil-URL", "Type", "Filtype (.ext)"};
    private JComboBox<String> categoryComboBoxRef;
    private Runnable onSelectionChangeCallback;

    public FilesTableModel(List<FileInfo> files, JComboBox<String> categoryComboBoxRef, Runnable onSelectionChangeCallback) {
        this.files = files;
        this.categoryComboBoxRef = categoryComboBoxRef;
        this.onSelectionChangeCallback = onSelectionChangeCallback;
    }

    @Override public int getRowCount() { return files.size(); }
    @Override public int getColumnCount() { return columnNames.length; }
    @Override public String getColumnName(int column) { return columnNames[column]; }
    @Override public Class<?> getColumnClass(int columnIndex) { return (columnIndex == 0) ? Boolean.class : String.class; }
    @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return columnIndex == 0; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FileInfo fileInfo = files.get(rowIndex);
        switch (columnIndex) {
            case 0: return fileInfo.isSelected();
            case 1: return fileInfo.getUrl();
            case 2: return fileInfo.getDetectedType();
            case 3: return fileInfo.getExtension();
            default: return null;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 0 && aValue instanceof Boolean) {
            files.get(rowIndex).setSelected((Boolean) aValue);
            fireTableCellUpdated(rowIndex, columnIndex);
            if (onSelectionChangeCallback != null) onSelectionChangeCallback.run();
        }
    }

    public void setData(List<FileInfo> newFiles) {
        this.files.clear();
        if (newFiles != null) this.files.addAll(newFiles);
        filterAndSelectBasedOnCategory();
    }

    public void filterAndSelectBasedOnCategory() {
        String selectedCategory = (String) categoryComboBoxRef.getSelectedItem();
        if (selectedCategory == null) return;
        List<String> extensionsInCurrentCategory = FileCategory.ALL_FILES_CATEGORY.equals(selectedCategory) ? null : FileCategory.CATEGORIZED_EXTENSIONS.get(selectedCategory);
        boolean hasChanges = false;
        for (FileInfo file : files) {
            boolean shouldBeSelected = FileCategory.ALL_FILES_CATEGORY.equals(selectedCategory) || (extensionsInCurrentCategory != null && extensionsInCurrentCategory.contains(file.getExtension()));
            if (file.isSelected() != shouldBeSelected) {
                file.setSelected(shouldBeSelected);
                hasChanges = true;
            }
        }
        if (hasChanges || !files.isEmpty()) fireTableDataChanged();
        if (onSelectionChangeCallback != null) onSelectionChangeCallback.run();
    }
}