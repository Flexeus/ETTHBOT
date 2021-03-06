package com.uospd.utils;

import java.util.Comparator;

public class HouseNumberComparator implements Comparator<String>{

    private final boolean isDigit(char ch){
        return ch >= 48 && ch <= 57;
    }

    /** Length of string is passed in for improved efficiency (only need to calculate it once) **/
    private final String getChunk(String s, int slength, int marker){
        StringBuilder chunk = new StringBuilder();
        char c = s.charAt(marker);
        chunk.append(c);
        marker++;
        if(isDigit(c)){
            while(marker < slength){
                c = s.charAt(marker);
                if(!isDigit(c))
                    break;
                chunk.append(c);
                marker++;
            }
        }else{
            while(marker < slength){
                c = s.charAt(marker);
                if(isDigit(c))
                    break;
                chunk.append(c);
                marker++;
            }
        }
        return chunk.toString();
    }

    public int compare(String h1, String h2){
        {


            if(h1 == null){
                return 1;
            }
            if(h2 == null){
                return -1;
            }

            int thisMarker = 0;
            int thatMarker = 0;
            int s1Length = h1.length();
            int s2Length = h2.length();

            while(thisMarker < s1Length && thatMarker < s2Length){
                String thisChunk = getChunk(h1, s1Length, thisMarker);
                thisMarker += thisChunk.length();

                String thatChunk = getChunk(h2, s2Length, thatMarker);
                thatMarker += thatChunk.length();

                // If both chunks contain numeric characters, sort them numerically
                int result = 0;
                if(isDigit(thisChunk.charAt(0)) && isDigit(thatChunk.charAt(0))){
                    // Simple chunk comparison by length.
                    int thisChunkLength = thisChunk.length();
                    result = thisChunkLength - thatChunk.length();
                    // If equal, the first different number counts
                    if(result == 0){
                        for(int i = 0;i < thisChunkLength;i++){
                            result = thisChunk.charAt(i) - thatChunk.charAt(i);
                            if(result != 0){
                                return result;
                            }
                        }
                    }
                }else{
                    result = thisChunk.compareTo(thatChunk);
                }

                if(result != 0)
                    return result;
            }

            return s1Length - s2Length;
        }
    }
}

