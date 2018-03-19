/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2017 Craig Cavanaugh
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
package jgnash.uifx.actions;

import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import jgnash.convert.imports.GenericImport;
import jgnash.convert.imports.ImportTransaction;
import jgnash.convert.imports.csv.CsvImport;
import jgnash.engine.Account;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.engine.Transaction;
import jgnash.uifx.StaticUIMethods;
import jgnash.uifx.control.wizard.WizardDialogController;
import jgnash.uifx.views.main.MainView;
import jgnash.uifx.wizard.imports.ImportWizard;
import jgnash.uifx.wizard.imports.csv.ImportCsvWizard;
import jgnash.util.ResourceUtils;

import java.io.File;
import java.util.List;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Utility class to import a Csv file containing transactions (exported from an online bank).
 *
 * @author Filipe Leme
 */
public class ImportCsvAction {
    private static final String LAST_DIR = "importDir";

    public static void showAndWait() {
        final ResourceBundle resources = ResourceUtils.getBundle();

        final ImportCsvWizard importCsvWizard = new ImportCsvWizard();

        final WizardDialogController<ImportCsvWizard.Settings> wizardDialogController
                = importCsvWizard.wizardControllerProperty().get();
        importCsvWizard.showAndWait();

        if (wizardDialogController.validProperty().get()) {
            @SuppressWarnings("unchecked")
            // import threads in the background
            ImportTransactionsTask importTransactionsTask = new ImportTransactionsTask((List<Transaction>) wizardDialogController.getSetting(ImportCsvWizard.Settings.TRANSACTIONS));

            new Thread(importTransactionsTask).start();

            StaticUIMethods.displayTaskProgress(importTransactionsTask);
        }
    }

    private static class ImportTransactionsTask extends Task<Void> {
        private final List<Transaction> transactions;

        ImportTransactionsTask(List<Transaction> transactions) {
            this.transactions = transactions;
        }

        @Override
        public Void call() {
            updateMessage(ResourceUtils.getString("Message.PleaseWait"));
            updateProgress(-1, Long.MAX_VALUE);
            Engine engine = EngineFactory.getEngine(EngineFactory.DEFAULT);
            for(final Transaction t : transactions){
                engine.addTransaction(t);
            }
            return null;
        }
    }
}
