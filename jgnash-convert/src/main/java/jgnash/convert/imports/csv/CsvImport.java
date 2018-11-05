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
package jgnash.convert.imports.csv;

import jgnash.convert.imports.DateFormat;
import jgnash.convert.imports.ImportUtils;
import jgnash.convert.imports.qif.*;
import jgnash.engine.*;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Wrapper Class to control the import of transactions form a CSV file.
 * @author Filipe Leme
 */
public class CsvImport {
    private CsvParser parser;
    /**
     * A holder for duplicate transactions
     */
    private final ArrayList<Transaction> duplicates = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();

    private static final Logger logger = Logger.getLogger(CsvImport.class.getName());

    public CsvParser getParser() {
        return parser;
    }

    public void setParser(CsvParser parser) {
        this.parser = parser;
    }


    public boolean doParse(final File file) {
        if (file != null) {
            parser = new CsvParser();
            boolean result = parser.parseFile(file);
            if(result) {
                addTransactions(parser.getTransactions());
            }
            return result;

        }
        return false;
    }

    public String dumpStats() {
        StringBuffer buffer = new StringBuffer();

        transactions.forEach(t->buffer.append(t.toString()));
        if(parser!=null) {
            buffer.append("\n Num Accounts :" + parser.accountMap.size());
            buffer.append("\n Num Total CSV Transactions :" + parser.getTransactions().size());
            buffer.append("\n Num AutoCategory Transactions : " + parser.getAutoCategorizedTransactions().size());
            parser.getAutoCategorizedTransactions().forEach((k,v)->buffer.append("\n \t Memo: '" + k +"' mapped to '" +v +"'"));
            buffer.append("\n No Category Transactions : " + parser.getNoCategoryTransactions().size());
            parser.getNoCategoryTransactions().forEach((k,v)->buffer.append("\n \t Memo: '" + k +"' mapped to '" +v +"'"));
        }
        buffer.append("\n Num Duplicated Transactions :" + duplicates.size());
        buffer.append("\n Num Final Transactions :" + transactions.size());
        return buffer.toString();
    }

    private void addTransactions(final List<Transaction> toImport) {
        if (toImport.isEmpty()) {
            return;
        }
        for (Transaction tran : toImport) {
            if (tran != null && isDuplicate(tran, tran.getCommonAccount())) { // strip and prevent NPE
                logger.fine("duplicate found");
                duplicates.add(tran);
                continue;
            }
            if (tran != null) {
                transactions.add(tran);
            } else {
                logger.warning("Null Transaction!");
            }
        }
    }

    private boolean isDuplicate(final Transaction t, final Account a) {
        for (final Transaction tran : a.getSortedTransactionList()) {
            if (tran.equalsIgnoreDate(t)) {
                return true;
            }
        }
        for(final Transaction tran : this.transactions){
            if(tran.equalsIgnoreDate(t)){
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the number of duplicate transactions that were stripped during the import
     *
     * @return number of duplicates found
     */
    public int getDuplicateCount() {
        return duplicates.size();
    }

    /**
     * Return an array of duplicate transactions that were found
     *
     * @return duplicate transactions if found
     */
    public Transaction[] getDuplicates() {
        return duplicates.toArray(new Transaction[0]);
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }
}
