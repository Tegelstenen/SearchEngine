/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.io.Serializable;

public class PostingsEntry implements Comparable<PostingsEntry>, Serializable {
    
    public int docID;
    public ArrayList<Integer> offsets;
    public double score = 0;

    public PostingsEntry(int docID, int offset) {
        this.docID = docID;
        this.offsets = new ArrayList<>();
        this.offsets.add(offset);
    }

    public PostingsEntry(int docID, int offset, double score) {
        this.docID = docID;
        this.offsets = new ArrayList<>();
        this.offsets.add(offset);
        this.score = score;
    }
    

    /**
     *  PostingsEntries are compared by their score (only relevant
     *  in ranked retrieval).
     *
     *  The comparison is defined so that entries will be put in 
     *  descending order.
     */
    public int compareTo( PostingsEntry other ) {
       return Double.compare( other.score, score );
    }


    public void addOffset( int offset) {
        this.offsets.add(offset);
    }

    @Override
    public String toString() {
        // docID:offset1,offset2,offset3
        StringBuilder sb = new StringBuilder();
        sb.append(docID).append(":");
        for (int i = 0; i < offsets.size(); i++) {
            sb.append(offsets.get(i));
            if (i < offsets.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    public static PostingsEntry fromString(String entryString) {
        // expects exactly docID: offset1,offset2,...
        String[] parts = entryString.split(":");
        int docID = Integer.parseInt(parts[0]);
        String[] offsetStrings = parts[1].split(",");
        PostingsEntry entry = new PostingsEntry(docID, 0);
        entry.offsets.clear();
        for (String o : offsetStrings) {
            entry.offsets.add(Integer.parseInt(o));
        }
        return entry;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getScore() {
        return this.score;
    }

}

