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
import jgnash.engine.*;
import jgnash.engine.search.AccountMatcher;
import jgnash.util.NotNull;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.csv.*;
import org.apache.commons.io.input.*;

/**
 * Controlling Class to manage the import of transactions form a CSV file.
 * @author Filipe Leme
 */
public final class CsvParser {
    private static final String PAYEE = "Payee";
    private static final String DATE = "Date";
    private static final String ACCOUNT = "Account";
    private static final String AMOUNT = "Amount";
    private static final String CODE = "Code";
    private static final String MEMO = "Memo";
    private static final String IGNORE = "Ignore";
    private static final String SPLIT_PAYEE = "-";
    private static final String MAIN_PAYEE = "Filipe";
    private static final String SECONDARY_PAYEE = "Brianne";
    private static final String DEFAULT_PAYEE = "Filipe-50";
    private static final String DEFAULT_BANK_PREFIX = "Bank Accounts:";
    private static final String UNCATEGORIZED_MAIN= "Expenses:"+MAIN_PAYEE+":NoCategory";
    private static final String UNCATEGORIZED_SECONDARY = "Expenses:"+SECONDARY_PAYEE+":NoCategory";
    private static final String IGNORE_ACC_KEYWORD = "_Brazil";
    private final Engine engine;
    private static int transCount;


    public final HashMap<String, Account> accountMap = new HashMap<>();

    private static final Logger logger = Logger.getLogger(CsvParser.class.getName());
    private List<Transaction> transactions = new ArrayList<>();
    private Map<String, String> autoCategorized = new HashMap<>();
    private Map<String, String> noCategory = new HashMap<>();

    private List<DateTimeFormatter> formatterList = new ArrayList<>();

    CsvParser() {
        AccountMatcher matcher = new AccountMatcher();
        engine = EngineFactory.getEngine(EngineFactory.DEFAULT);
        formatterList.add(DateTimeFormatter.ofPattern("d-MMM-yy").withLocale(Locale.CANADA));
        formatterList.add(DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale.CANADA));
        formatterList.add(DateTimeFormatter.ofPattern("dd MMM yyyy").withLocale(Locale.CANADA));
        formatterList.add(DateTimeFormatter.ofPattern("MMM. d, yyyy").withLocale(Locale.CANADA));
        formatterList.add(DateTimeFormatter.ofPattern("MMM d, yyyy").withLocale(Locale.CANADA));
        transCount = engine.getTransactions().size() + 1;

    }

    boolean parseFile(final File file) {
        try {
            loadAccountMap();
            final Reader reader = new InputStreamReader(new BOMInputStream(new FileInputStream(file)));
            final CSVParser parser = new CSVParser(reader, CSVFormat.EXCEL.withFirstRecordAsHeader().withIgnoreEmptyLines(true).withTrim(true));
            for (CSVRecord csvRecord : parser.getRecords()) {
                boolean isEmpty = true;
                logger.log(Level.FINE,"Reading : " + csvRecord.toString());
                if(getOrDefault(csvRecord, IGNORE, "").equals("yes")){
                    continue;
                }
                for(String value : csvRecord.toMap().values()){
                    if(value!=null && !value.equals("")){
                        isEmpty = false;
                        break;
                    }
                }
                if(!isEmpty) {
                    addCsvRecord(csvRecord);
                }
            }
        } catch (final FileNotFoundException e) {
            logger.log(Level.WARNING, "Could not find file: {0}", file);
            return false;
        } catch (final Exception e) {
            logger.log(Level.SEVERE, null, e);
            return false;
        }
        return true;
    }

    private void addCsvRecord(CSVRecord csvRecord) throws Exception{
        if(accountMap.isEmpty()){
            loadAccountMap();
        }
        Account leadAccount = findMatchingAccount(csvRecord.get(ACCOUNT));
        Transaction t = new Transaction();
        t.setDate(getDateFromCSV(csvRecord.get(DATE)));
        t.setMemo(getMemoFromCsv(csvRecord));
        t.setNumber("["+LocalDate.now().getYear() + "-"+LocalDate.now().getMonthValue()+"] "+ transCount++);
        t.setPayee(getOrDefault(csvRecord, PAYEE, DEFAULT_PAYEE));
        addTransactionEntries(t, leadAccount, csvRecord);
        transactions.add(t);

    }
    private void addTransactionEntries(Transaction t, Account baseAccount, CSVRecord csvRecord){
//        String[] splitTags = t.getPayee().split(SPLIT_PAYEE);
        BigDecimal totalAmount = getAmountFromCsv(csvRecord.get(AMOUNT));
        String keyword = getOrDefault(csvRecord, "Keyword", null);
        Account entry1Acc = findMatchingAccount(t, "Filipe", keyword, baseAccount);
        TransactionEntry entry = createEntry(totalAmount, baseAccount, entry1Acc, t.getMemo());
        t.addTransactionEntry(entry);
        // stop splitting between two.
//        BigDecimal remainder = totalAmount.subtract(entry1Amount);
//        if(remainder.compareTo(new BigDecimal(0)) != 0){
//            Account entry2Acc = findMatchingAccount(t, getOtherPayee(splitTags[0]), keyword, entry.getCreditAccount(), entry.getDebitAccount());
//            t.addTransactionEntry(createEntry(remainder, baseAccount, entry2Acc, t.getMemo()));
//        }
    }
    private TransactionEntry createEntry(BigDecimal amount, Account baseAccount, Account destination, String memo){
        TransactionEntry entry;
        // if amount is positive, base is credit
        boolean isBaseCredit = amount.compareTo(new BigDecimal(0)) > 0;
        if(isBaseCredit){
            entry = new TransactionEntry(baseAccount, destination, amount);
            entry.setMemo(memo);
        }else{
            entry = new TransactionEntry(destination, baseAccount, amount);
            entry.setMemo(memo);
        }
        return entry;
    }
    // Assume it is supposed to be a bank account
    private Account findMatchingAccount(String accountName){
        return accountMap.getOrDefault(accountName, accountMap.get(DEFAULT_BANK_PREFIX+accountName));
    }
    private Account findMatchingAccount(Transaction t, String payee, String keyword, Account... blockedAccounts){
        Account acc = AccountMatcher.getBestMatch(t, payee, keyword, blockedAccounts);
        if(acc == null) {
            String defaultAccountKey = payee.equals(MAIN_PAYEE) ? UNCATEGORIZED_MAIN : UNCATEGORIZED_SECONDARY;
            acc = accountMap.get(defaultAccountKey);
            noCategory.put(t.getMemo(), acc.getPathName());
        }else{
            logger.log(Level.FINE, "Account '"+acc.getPathName()+"' was matched automatically for transaction: "+ t.getMemo());
            autoCategorized.put(t.getMemo(), acc.getPathName());
        };
        return acc;
    }
    private String getOtherPayee(String payee){
        return payee.equals(MAIN_PAYEE) ? SECONDARY_PAYEE : MAIN_PAYEE;
    }
    private BigDecimal getAmountFromCsv(String amount){
        amount = amount.replace("$", "");
        return new BigDecimal(amount);
    }
    private String getOrDefault(CSVRecord record, String key, String defaultValue){
        try{
            String value =  record.get(key);
            return value.isEmpty() ? defaultValue : value;
        }catch (Exception e){
            return defaultValue;
        }
    }
    private String getMemoFromCsv(CSVRecord record){
        String finalMemo = "";
        String code = getOrDefault(record, CODE, "");
        if(!code.isEmpty()){
            finalMemo += "("+code+") ";
        }
        String nextMemo = getOrDefault(record, MEMO, "");
        int i = 1;
        while(nextMemo != null){
            finalMemo +=  (i > 1? " - " : "") + nextMemo;
            nextMemo = getOrDefault(record, MEMO + i, null);
            i++;
        }
        return finalMemo;
    }
    private LocalDate getDateFromCSV(String date) throws Exception{
        for(DateTimeFormatter formatter : formatterList) {
            try {
                return LocalDate.parse(date, formatter);
            } catch (Exception exp) {

            }
        }
        throw new Exception("Invalid Date");
    }

    public List<Transaction> getTransactions(){
        return transactions;
    }

    public Map<String, String> getAutoCategorizedTransactions(){
        return autoCategorized;
    }
    public Map<String, String> getNoCategoryTransactions(){
        return noCategory;
    }

    private void loadAccountMap() {
        engine.getAccountList().forEach( acc-> {
            if(!acc.getPathName().contains(IGNORE_ACC_KEYWORD))
                accountMap.put(acc.getPathName(), acc);
        });
    }

    private void moveTransactions(){
        List<Transaction> transactions = engine.getTransactions();
        LocalDate toMigrate = LocalDate.now();
        for(Transaction transaction : transactions){
            // only migrate this month trans
            if(transaction.getLocalDate().getYear() == toMigrate.getYear() && transaction.getLocalDate().getMonth().getValue() < 9){
                boolean toPersist = false;
                // clone the transaction
                try{
                    Transaction cloned = (Transaction) transaction.clone();
                List<TransactionEntry> entries = transaction.getTransactionEntries();
                TransactionEntry filipeEntry = null;
                TransactionEntry brianneEntry = null;
                    boolean isDebitBrianne = true;
                    boolean isDebitFilipe = true;
                for(TransactionEntry entry : entries){
                    // debit should always be the bank account
                    String debitPath = entry.getDebitAccount().getPathName();
                    String creditPath =entry.getCreditAccount().getPathName();
                    String pathName= debitPath +"_"+creditPath;
                    if(pathName.contains("Expenses")){
                        if(debitPath.contains("Brianne")){
                            brianneEntry = entry;
                        }else if(debitPath.contains("Filipe")){
                            filipeEntry = entry;
                        } else if(creditPath.contains("Brianne")){
                            isDebitBrianne = false;
                            brianneEntry = entry;
                        }else if(creditPath.contains("Filipe")){
                            isDebitFilipe = false;
                            filipeEntry = entry;
                        }
                    }
                }
                if(brianneEntry != null){
                    logger.log(Level.WARNING, "Original trans: '" + transaction.toString());
                    //to migrate
                    if(filipeEntry == null){
                        Account acc = AccountMatcher.getBestMatch(transaction, "Filipe", isDebitBrianne ? brianneEntry.getDebitAccount().getName() : brianneEntry.getCreditAccount().getName());
                        if(acc==null) {
                            logger.log(Level.FINE, "Transaction '" + transaction.getMemo() + "' could not match Filipe's account.");
                        }else{
                            TransactionEntry newEntry = (TransactionEntry) brianneEntry.clone();
                            cloned.setPayee("Brianne-100");
                            if(isDebitBrianne) {
                                // was an invalid debit/credit, fix order
                                newEntry.setCreditAccount(acc);
                                newEntry.setDebitAccount(brianneEntry.getCreditAccount());
                            }else{
                                newEntry.setCreditAccount(acc);
                            }
                            cloned.removeTransactionEntry(brianneEntry);
                            cloned.addTransactionEntry(newEntry);
                            toPersist = true;
                        }
                    }else{
                        // already exist filipe, simply update value.
                        cloned.setPayee("Filipe-50");
                        TransactionEntry newEntry = (TransactionEntry) filipeEntry.clone();
                        BigDecimal finalAmount = null;
                        if(isDebitFilipe){
                            // was an invalid debit/credit, fix order
                            newEntry.setCreditAccount(filipeEntry.getDebitAccount());
                            newEntry.setDebitAccount(filipeEntry.getCreditAccount());
                        }
                        finalAmount = filipeEntry.getDebitAmount().compareTo(new BigDecimal(0)) >= 1 ? filipeEntry.getDebitAmount() : filipeEntry.getCreditAmount();
                        finalAmount = finalAmount.add(brianneEntry.getDebitAmount().compareTo(new BigDecimal(0)) >= 1 ? brianneEntry.getDebitAmount() : brianneEntry.getCreditAmount());
                        if(finalAmount.compareTo(new BigDecimal(0)) < 0 ){
                            logger.log(Level.WARNING, "wrong final amount: '" + newEntry.toString());
                        }
                        newEntry.setAmount(finalAmount);
                        cloned.removeTransactionEntry(brianneEntry);
                        cloned.removeTransactionEntry(filipeEntry);
                        cloned.addTransactionEntry(newEntry);
                        toPersist = true;
                    }
                }
                // Current issue: all transactions in general seems fine, however by end of execution bank balances were slightly affected.
                if(toPersist){
                    cloned.setMemo("[Merged] "+cloned.getMemo());
                    logger.log(Level.WARNING, "Transaction to be updated: '" + cloned.toString());
                    engine.removeTransaction(transaction);
                    engine.addTransaction(cloned);
                }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }
}
