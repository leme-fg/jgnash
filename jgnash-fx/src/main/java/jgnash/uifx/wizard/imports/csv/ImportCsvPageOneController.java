/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2018 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.uifx.wizard.imports.csv;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import jgnash.convert.imports.csv.CsvImport;
import jgnash.engine.Transaction;
import jgnash.uifx.StaticUIMethods;
import jgnash.uifx.actions.ImportCsvAction;
import jgnash.uifx.control.wizard.AbstractWizardPaneController;
import jgnash.uifx.control.wizard.WizardDialogController;
import jgnash.uifx.views.main.MainView;
import jgnash.util.ResourceUtils;
import org.apache.commons.csv.CSVParser;
import org.apache.xmlbeans.impl.xb.xsdschema.ImportDocument;
import org.h2.tools.Csv;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Import Wizard Controller for CSV file chooser (page one)
 *
 * @author Filipe Leme
 */
public class ImportCsvPageOneController extends AbstractWizardPaneController<ImportCsvWizard.Settings> {
    private static final String LAST_DIR = "importCSVDir";
    private static final Preferences pref = Preferences.userNodeForPackage(ImportCsvAction.class);
    @FXML
    private Button fileButton;

    @FXML
    private TextField fileNameField;

    @FXML
    private ResourceBundle resources;

    @FXML
    private void initialize() {
        updateDescriptor();
        fileNameField.setText("/Users/fleme/Dropbox/2017Folders/jGnash/import_canada.csv");
    }

    @Override
    public void putSettings(final Map<ImportCsvWizard.Settings, Object> map) {
        map.put(ImportCsvWizard.Settings.CSV_FILE, fileNameField.getText());
    }

    @Override
    public void getSettings(final Map<ImportCsvWizard.Settings, Object> map) {
        String newFile = (String) map.get(ImportCsvWizard.Settings.CSV_FILE);
        if(newFile != null){
            fileNameField.setText(newFile);
        }
        updateDescriptor();
    }

    @Override
    public String toString() {
        return "1. " + ResourceUtils.getString("Title.ImportCsv");
    }

    @FXML
    private void handleFileButtonAction() {
        final FileChooser fileChooser = configureFileChooser();
        fileChooser.setTitle(resources.getString("Title.SelFile"));

        final File file = fileChooser.showOpenDialog(MainView.getPrimaryStage());
        if (file != null) {
            fileNameField.setText(file.getAbsolutePath());
            pref.put(LAST_DIR, file.getParent());
        }

        updateDescriptor();
    }

    private static FileChooser configureFileChooser() {
        final FileChooser fileChooser = new FileChooser();
        File fileDir = new File(pref.get(LAST_DIR, System.getProperty("user.home")));
        if(!fileDir.isDirectory()){
            fileDir = fileDir.getParentFile();
        }
        fileChooser.setInitialDirectory(fileDir);

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Csv Files (*.csv)", "*.csv")
        );

        return fileChooser;
    }

    @Override
    public boolean isPaneValid() {
        return !fileNameField.getText().isEmpty();
    }
}
