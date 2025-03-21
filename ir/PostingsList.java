/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;


public class PostingsList implements Iterable<PostingsEntry> {
    
    /** The postings list */
    private ArrayList<PostingsEntry> list = new ArrayList<>();


    /** Number of postings in this list. */
    public int size() {
        return list.size();
    }

    /** Returns the ith posting. */
    public PostingsEntry get( int i ) {
        return list.get( i );
    }

    /** 
     * Standard add: if the last entry has the same docID, add the offset.
     * Otherwise, create a new entry.
     */
    public void add(int docID, int offset) {
        addOrUpdate(docID, offset, 0);
    }

    /**
     * New overloaded add: uses the optional score parameter.
     * If an entry with the same docID exists, add the offset and add to its score.
     */
    public void add(int docID, int offset, double score) {
        addOrUpdate(docID, offset, score);
    }

    /**
     * Searches the current list for an entry with the given docID.
     * If found, adds the offset and accumulates the score.
     * Otherwise, creates a new entry.
     */
    public void addOrUpdate(int docID, int offset, double score) {
        for (PostingsEntry entry : list) {
            if (entry.docID == docID) {
                entry.addOffset(offset);
                entry.setScore(entry.getScore() + score);
                return;
            }
        }
        // Not found? Add a new entry.
        list.add(new PostingsEntry(docID, offset, score));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i).toString());
            if (i < list.size() - 1) {
                sb.append(";");
            }
        }
        return sb.toString();
    }

    public static PostingsList fromString(String postingsString) {
        PostingsList postingsList = new PostingsList();
        String[] entryStrings = postingsString.split(";");
        for (String entryString : entryStrings) {
            PostingsEntry entry = PostingsEntry.fromString(entryString);
            postingsList.list.add(entry);
        }
        return postingsList;
    }

    public static PostingsList merge(PostingsList listA, PostingsList listB) {
        PostingsList mergedList = new PostingsList();
        int i = 0, j = 0;

        while (i < listA.size() && j < listB.size()) {
            PostingsEntry entryA = listA.get(i);
            PostingsEntry entryB = listB.get(j);

            if (entryA.docID == entryB.docID) {
                // Create merged entry with combined capacity
                PostingsEntry mergedEntry = new PostingsEntry(entryA.docID, 0);
                mergedEntry.offsets = new ArrayList<>(entryA.offsets.size() + entryB.offsets.size());
                
                // Add all offsets and ensure they're sorted
                mergedEntry.offsets.addAll(entryA.offsets);
                mergedEntry.offsets.addAll(entryB.offsets);
                Collections.sort(mergedEntry.offsets);
                
                mergedList.list.add(mergedEntry);
                i++;
                j++;
            } else if (entryA.docID < entryB.docID) {
                mergedList.list.add(entryA);
                i++;
            } else {
                mergedList.list.add(entryB);
                j++;
            }
        }

        // Add remaining entries from listA
        while (i < listA.size()) {
            mergedList.list.add(listA.get(i++));
        }

        // Add remaining entries from listB
        while (j < listB.size()) {
            mergedList.list.add(listB.get(j++));
        }

        return mergedList;
    }
    
    public void subList(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }
        if (n > list.size()) {
            throw new IndexOutOfBoundsException("n cannot be greater than the list size");
        }
        list.subList(n, list.size()).clear();
    }

    // Implementing the Iterable interface
    @Override
    public Iterator<PostingsEntry> iterator() {
        return list.iterator();
    }

    public void sort() {
        Collections.sort(list);
    }


}

