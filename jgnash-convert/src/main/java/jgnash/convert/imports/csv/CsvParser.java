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
    private static final String DEFAULT_PAYEE = MAIN_PAYEE+"-50";
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
        String[] splitTags = t.getPayee().split(SPLIT_PAYEE);
        BigDecimal splitPercentage = (new BigDecimal(splitTags[1])).divide(new BigDecimal(100));
        BigDecimal totalAmount = getAmountFromCsv(csvRecord.get(AMOUNT));
        BigDecimal entry1Amount = totalAmount.multiply(splitPercentage).setScale(2, RoundingMode.HALF_EVEN);
        String keyword = getOrDefault(csvRecord, "Keyword", null);
        Account entry1Acc = findMatchingAccount(t, splitTags[0], keyword, baseAccount);
        TransactionEntry entry = createEntry(entry1Amount, baseAccount, entry1Acc, t.getMemo());
        t.addTransactionEntry(entry);
        BigDecimal remainder = totalAmount.subtract(entry1Amount);
        if(remainder.compareTo(new BigDecimal(0)) != 0){
            Account entry2Acc = findMatchingAccount(t, getOtherPayee(splitTags[0]), keyword, entry.getCreditAccount(), entry.getDebitAccount());
            t.addTransactionEntry(createEntry(remainder, baseAccount, entry2Acc, t.getMemo()));
        }
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

    private void loadAccountMap() {
        engine.getAccountList().forEach( acc-> {
            if(!acc.getPathName().contains(IGNORE_ACC_KEYWORD))
                accountMap.put(acc.getPathName(), acc);
        });
    }
}
