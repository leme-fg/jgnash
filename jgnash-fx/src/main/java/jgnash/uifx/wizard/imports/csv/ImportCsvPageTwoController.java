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
import jgnash.uifx.views.main.MainView;
import jgnash.util.ResourceUtils;

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
public class ImportCsvPageTwoController extends AbstractWizardPaneController<ImportCsvWizard.Settings> {
    private List<Transaction> transactions = new ArrayList<>();
    @FXML
    private ResourceBundle resources;

    @FXML
    private TextArea textArea;

    @FXML
    private void initialize() {
        updateDescriptor();
    }

    @Override
    public void putSettings(final Map<ImportCsvWizard.Settings, Object> map) {
        map.put(ImportCsvWizard.Settings.TRANSACTIONS, transactions);
    }

    @Override
    public void getSettings(final Map<ImportCsvWizard.Settings, Object> map) {
        List<Transaction> newTrans = (List<Transaction>) map.get(ImportCsvWizard.Settings.TRANSACTIONS);
        if(newTrans != null) {
            transactions = newTrans;
        }
        String file = (String) map.get(ImportCsvWizard.Settings.CSV_FILE);
        if(file!=null && !file.isEmpty()) {
            CsvImport csvImport = new CsvImport();
            if (!csvImport.doParse(new File(file))) {
                StaticUIMethods.displayError(ResourceUtils.getString("Message.Error.ParseTransactions"));
            }
            transactions = csvImport.getTransactions();
            if (transactions.isEmpty()) {
                StaticUIMethods.displayError(ResourceUtils.getString("Message.Error.ParseTransactions"));
            }
            textArea.setText(csvImport.dumpStats());
            putSettings(map);
        }
        updateDescriptor();
    }

    @Override
    public String toString() {
        return "2. " + ResourceUtils.getString("Title.ImportTransactions");
    }

    @Override
    public boolean isPaneValid() {
        return true;
    }
}
