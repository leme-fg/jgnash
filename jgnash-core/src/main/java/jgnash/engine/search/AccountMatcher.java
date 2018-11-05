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
package jgnash.engine.search;

import jgnash.engine.*;
import org.apache.poi.util.SystemOutLogger;

import java.util.*;

/**
 * Memo to Account matcher.
 *
 * @author Filipe Leme
 *
 */
public class AccountMatcher {
    private static final String IGNORE_ACC_KEYWORD = "_Brazil";

    public static final double EXPENSES_FACTOR = 1.1;
    private static Map<String, HashMap<Account, Double>> memoToAccountFrequency;
    public AccountMatcher() {
        if(memoToAccountFrequency==null){
            updateMatchingMap();
        }
    }
    private static void updateMatchingMap(String memo, String keyword){
        Engine engine = EngineFactory.getEngine(EngineFactory.DEFAULT);
        engine.getAccountList().forEach(account -> {
            if(account.getPathName().contains(keyword)){
                HashMap<Account, Double> current = memoToAccountFrequency.getOrDefault(memo, new HashMap<>());
                current.put(account, 10.0);
                memoToAccountFrequency.put(memo, current);
            }
        });
    }
    private static void updateMatchingMap(){
        memoToAccountFrequency = new HashMap<>();
        Engine engine = EngineFactory.getEngine(EngineFactory.DEFAULT);

        engine.getTransactions().forEach(transaction -> {
            String memo = sanitizeMemo(transaction.getMemo());
            if(!memo.isEmpty()){
                HashMap<Account, Double> current = memoToAccountFrequency.getOrDefault(memo, new HashMap<>());
                transaction.getAccounts().forEach(acc->{
                    // for now it is simply the frequency
                    Double score = current.getOrDefault(acc, 0.0);
                    double increment = 1.0;
                    if(acc.getPathName().contains("Expenses")){
                        increment *= EXPENSES_FACTOR;
                    }
                    current.put(acc, score+increment);
                });
                memoToAccountFrequency.put(memo, current);
            }
        });
    }
    public static String sanitizeMemo(String memo){
        // testing if grabbing just first 2 words are enough to match correctly.
        String[] memoWords = memo.split(" ");
        String sanitized = memoWords[0] + (memoWords.length>1?" " + memoWords[1]:"");
        return sanitized;
        /*
//        if(memo.startsWith("(CW) INTERAC E-TRANSFER SENT")){
//            return "(CW) INTERAC E-TRANSFER SENT".toLowerCase();
//        }
//        if(memo.startsWith("(DC) HANDLING CHG")){
//            return "(DC) HANDLING CHG".toLowerCase();
//        }
//        if(memo.startsWith("UBER TRIP")){
//            return "(DC) HANDLING CHG".toLowerCase();
//        }
        String sanitized = memo.toLowerCase().replaceAll("\\d{2}/\\d{2}/\\d{4}", "").replaceAll("-", "");
        if(!sanitized.startsWith("(")){
            // also remove digits (store number) in the end if memo doesn't start with "(CODE)"
            sanitized = sanitized.replaceAll("\\d+$", "");
        }
        return sanitized.trim();*/
    }

    public static Account getBestMatch(Transaction transaction, String nextPayee, String keyword, Account... blockedAccounts){
        if(memoToAccountFrequency == null){
            updateMatchingMap();
        }
        boolean hasKeyword = keyword!=null && !keyword.equals("");
        String memo = sanitizeMemo(transaction.getMemo());
        if(hasKeyword){
            updateMatchingMap(memo, keyword);
        }
        HashMap<Account, Double> matchMap = memoToAccountFrequency.get(memo);
        Account result = null;
        if(matchMap!=null) {
            double currentScore = 0;
            for (Map.Entry<Account, Double> entry : matchMap.entrySet()) {
                Account acc = entry.getKey();
                boolean isValid=true;
                boolean isExpense = acc.getPathName().contains("Expenses");

                if(acc.getPathName().contains(IGNORE_ACC_KEYWORD))
                   continue;

                // invalid if expenses are not for the correct payee
                if(isExpense && !acc.getPathName().contains(nextPayee)){
                    continue;
                }
                if(hasKeyword && !acc.getPathName().contains(keyword)){
                    isValid = false;
                }
                for (Account blocked : blockedAccounts) {
                    if (acc.equals(blocked)) {
                        isValid = false;
                        continue;
                    }
                    if(isCreditCard(blocked) && isCreditCard(acc)){
                        isValid = false;
                        continue;
                    }
                }
                if(isValid) {
                    if (result == null) {
                        result = acc;
                        currentScore = entry.getValue();
                        continue;
                    }
                    // update if higher score
                    if (entry.getValue() > currentScore) {
                        result = acc;
                        currentScore = entry.getValue();
                    }
                }
            }
        }
        return result;
    }
    private static boolean isCreditCard(Account acc){
        String name = acc.getPathName().toLowerCase();
        if(name.contains("mastercard") || name.contains(":visa")){
            return true;
        }
        return false;
    }

}
