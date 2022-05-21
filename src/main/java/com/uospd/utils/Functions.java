package com.uospd.utils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Functions {
    private Functions(){}
    public static String getDate(){
        return new SimpleDateFormat("[yyyy/MM/dd HH:mm:ss]").format(Calendar.getInstance().getTime());
    }

    public static String getTime(){
        return new SimpleDateFormat("[HH:mm:ss]").format(Calendar.getInstance().getTime());
    }

    public static boolean containsIllegals(String toExamine) {
        Pattern pattern = Pattern.compile("[~#@*+%{}<>\\[\\]|\"\\_^]");
        Matcher matcher = pattern.matcher(toExamine);
        return matcher.find();
    }

    private static double bit=1.9, littlest =2, little = 2.1,  medium= 2.2, big = 2.4,great =3.4;
    private static Map<Character, Double> charWeights = new HashMap<>();
    static{
        //bit:
        charWeights.put('г',bit);
        //littlest
        charWeights.put('у',littlest);
        charWeights.put('т',littlest);
        charWeights.put('з',littlest);
        charWeights.put('э',littlest);
        charWeights.put('х',littlest);
        //little
        charWeights.put('с',little);
        charWeights.put('а',little);
        charWeights.put('е',little);
        charWeights.put('ё',little);
        charWeights.put('к',little);
        charWeights.put('ь',little);
        charWeights.put('ч',little);
        //medium
        charWeights.put('я',medium);
        charWeights.put('и',medium);
        charWeights.put('й',medium);
        charWeights.put('н',medium);
        charWeights.put('о',medium);
        charWeights.put('п',medium);
        charWeights.put('р',medium);
        charWeights.put('б',medium);
        charWeights.put('в',medium);
        charWeights.put('д',medium);
        charWeights.put('л',medium);
        charWeights.put('ц',medium);
        charWeights.put('ъ',medium);
        //big
        charWeights.put('ф',big);
        charWeights.put('м',big);
        charWeights.put('ы',big);
        charWeights.put('ж',big);
        // greater than big
        charWeights.put('ю',great);
        charWeights.put('ш',great);
        charWeights.put('щ',great);
        //other
        charWeights.put(' ',1.0);
        charWeights.put('.',1.0);
        charWeights.put(',',1.0);
        charWeights.put('/',2.0);
    }

    private static double getWordWeight(String word){
        double countWeight=0;
        for(char c : word.toCharArray()) countWeight+=charWeights.getOrDefault(c,2.1);
        return countWeight;
    }

    public static List<String> tableFormat(List<String> stringList){
        List<String> list = new ArrayList<>();
        String max="";
        for(String word: stringList){
            String lword = word.toLowerCase();
            double wordWeight = getWordWeight(lword);
            if(wordWeight > getWordWeight(max)) max = lword;
        }
        double biggestWordWeight = getWordWeight(max);

        for(int i = 0;i < stringList.size();i++){
            String thisWord = stringList.get(i).toLowerCase();
            double thisWordWeight = getWordWeight(thisWord);
            if(thisWordWeight < biggestWordWeight){
                StringBuilder builder = new StringBuilder(thisWord);
                double needWeight = Math.round(biggestWordWeight - thisWordWeight);
                for(int x = 0;x < needWeight;x++) builder.append(" ");
                list.add(builder.toString());
                continue;
            }
            list.add(thisWord);
        }
        return list;
    }

    public static String timePassed(Date date){
        long diff = System.currentTimeMillis() - date.getTime();
        // long diffSeconds = diff / 1000 % 60;
        long diffMinutes = diff / (60 * 1000) % 60;
        long diffHours = diff / (60 * 60 * 1000) % 24;
        long diffDays = diff / (24 * 60 * 60 * 1000);

        StringBuilder sb = new StringBuilder();
        if(diffDays> 0)sb.append(diffDays).append("д,");
        if(diffHours>0)sb.append(diffHours).append("ч,");
        sb.append(diffMinutes).append("м");
        return sb.toString();
    }

    public static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException er) {
            return false;
        }
        return true;
    }

    public static boolean isLong(String s) {
        try {
            Long.parseLong(s);
        } catch (NumberFormatException er) {
            return false;
        }
        return true;
    }

    public static String decToHex(int dec)
    {
        String hex = Integer.toHexString(dec);
        if(hex.length() == 1) hex = "0".concat(hex);
        return hex;
    }

    public static long getDateDiff(Date beforeDate, Date afterDate, TimeUnit timeUnit){
        if(beforeDate == null) throw new IllegalArgumentException("beforeDate cannot be null");
        if(afterDate == null) throw new NullPointerException("afterDate cannot be null");
        long diffInMillies = afterDate.getTime() - beforeDate.getTime();
        return timeUnit.convert(diffInMillies,TimeUnit.MILLISECONDS);
    }


    public static String getAsLink(String text,String url){
        return "<a href=\""+url+"\">"+text+"</a>";
    }

}
