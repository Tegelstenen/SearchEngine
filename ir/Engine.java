/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */

package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

/**
 *  This is the main class for the search engine.
 */
public class Engine {

    /** The inverted index. */
    // Index index = new HashedIndex();
    // Assignment 1.7: Comment the line above and uncomment the next line
    // Index index = new PersistentHashedIndex();
    Index index = new PersistentScalableHashedIndex();
    public static final HashMap<Integer, Double> pageRanks = new HashMap<Integer, Double>();
    /** The indexer creating the search index. */
    Indexer indexer;

    /** The searcher used to search the index. */
    Searcher searcher;

    /** K-gram index */
    // KGramIndex kgIndex = null;
    // Assignment 3: Comment the line above and uncomment the next line
    KGramIndex kgIndex = new KGramIndex(2);

    /** Spell checker */
    // SpellChecker speller;
    // Assignment 3: Comment the line above and uncomment the next line
    SpellChecker speller = new SpellChecker( index, kgIndex );
    
    /** The engine GUI. */
    SearchGUI gui;

    /** Directories that should be . */
    ArrayList<String> dirNames = new ArrayList<String>();

    /** Lock to prevent simultaneous access to the index. */
    Object indexLock = new Object();

    /** The patterns matching non-standard words (e-mail addresses, etc.) */
    String patterns_file = null;

    /** The file containing the logo. */
    String pic_file = "";

    /** The file containing the pageranks. */
    public static String rank_file = "pagerank/pageRanks.txt";
    

    /** For persistent indexes, we might not need to do any indexing. */
    boolean is_indexing = true;

    public static HITSRanker hr;


    /* ----------------------------------------------- */


    /**  
     *   Constructor. 
     *   Indexes all chosen directories and files
     */
    public Engine( String[] args ) {
        decodeArgs( args );
        indexer = new Indexer( index, kgIndex, patterns_file );
        searcher = new Searcher( index, kgIndex );
        gui = new SearchGUI( this );
        gui.init();
        /* 
         *   Calls the indexer to index the chosen directory structure.
         *   Access to the index is synchronized since we don't want to 
         *   search at the same time we're indexing new files (this might 
         *   corrupt the index).
         */
        if (is_indexing) {
            synchronized ( indexLock ) {
                gui.displayInfoText( "Indexing, please wait..." );
                long startTime = System.currentTimeMillis();
                for ( int i=0; i<dirNames.size(); i++ ) {
                    File dokDir = new File( dirNames.get( i ));
                    indexer.processFiles( dokDir, is_indexing );
                }
                long elapsedTime = System.currentTimeMillis() - startTime;
                gui.displayInfoText( String.format( "Indexing done in %.1f seconds.", elapsedTime/1000.0 ));
                index.cleanup();
                System.err.println("Calculating Euclidean lengths");
                calculateEuclidean();
                System.err.println("Writing Euclidean to file");
                writeEuclidean();
            }
        } else {
            gui.displayInfoText( "Index is loaded from disk" );
            readEuclidean();
        }
        readPageRanks();
        this.hr = new HITSRanker( "pagerank/linksDavis.txt", "pagerank/davisTitles.txt", this.index );
    }


    /* ----------------------------------------------- */

    /**
     *   Decodes the command line arguments.
     */
    private void decodeArgs( String[] args ) {
        int i=0, j=0;
        while ( i < args.length ) {
            if ( "-d".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    dirNames.add( args[i++] );
                }
            } else if ( "-p".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    patterns_file = args[i++];
                }
            } else if ( "-l".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    pic_file = args[i++];
                }
            } else if ( "-r".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    rank_file = args[i++];
                }
            } else if ( "-ni".equals( args[i] )) {
                i++;
                is_indexing = false;
            } else {
                System.err.println( "Unknown option: " + args[i] );
                break;
            }
        }                   
    }


    /* ----------------------------------------------- */
    private void calculateEuclidean() { 
        double N = Index.docNames.size();
        Set<String> uniqueTerms = new HashSet<>();
    
        // First pass: Collect all unique terms
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream("term_freqs.bin")))) {
            while (dis.available() > 0) {
                int docID = dis.readInt();
                int numTerms = dis.readInt();
                for (int i = 0; i < numTerms; i++) {
                    String token = dis.readUTF();
                    dis.readInt(); // Skip term frequency for now
                    uniqueTerms.add(token);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading term_freqs.bin for unique terms: " + e.getMessage());
            return;
        }
    
        // Precompute IDF for each unique term
        Map<String, Double> idfMap = new HashMap<>();
        for (String term : uniqueTerms) {
            PostingsList postingsList = index.getPostings(term);
            if (postingsList != null) {
                int df = postingsList.size();
                double idf = Math.log(N / df);
                idfMap.put(term, idf);
            } else {
                idfMap.put(term, 0.0);
            }
        }
    
        // Second pass: Calculate Euclidean norms using precomputed IDFs
        try (DataInputStream dis = new DataInputStream(
            new BufferedInputStream(new FileInputStream("term_freqs.bin")))) {
            while (dis.available() > 0) {
                int docID = dis.readInt();
                int numTerms = dis.readInt();
                double tfidfSquareSum = 0.0;
                for (int i = 0; i < numTerms; i++) {
                    String token = dis.readUTF();
                    int tf = dis.readInt();
                    Double idf = idfMap.get(token);
                    if (idf != null) {
                        tfidfSquareSum += Math.pow(tf * idf, 2);
                    }
                }
                Index.docEucLen.put(docID, Math.sqrt(tfidfSquareSum));
            }
        } catch (IOException e) {
            System.err.println("Error reading term_freqs.bin for Euclidean calc: " + e.getMessage());
        }
    }

    private void writeEuclidean() { 
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream("docEuclidean.bin")))) {
            dos.writeInt(Index.docEucLen.size());
            for (Map.Entry<Integer, Double> entry : Index.docEucLen.entrySet()) {
                dos.writeInt(entry.getKey());
                dos.writeDouble(entry.getValue());
            }
        } catch (IOException e) {
            System.err.println("Error writing docEuclidean.bin: " + e.getMessage());
        }
    }

    /**
     * Reads the Euclidean lengths from the binary file.
     */
    private void readEuclidean() { 
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream("docEuclidean.bin")))) {
            int size = dis.readInt();
            for (int i = 0; i < size; i++) {
                int docID = dis.readInt();
                double eucLen = dis.readDouble();
                Index.docEucLen.put(docID, eucLen);
            }
        } catch (IOException e) {
            System.err.println("Error reading docEuclidean.bin: " + e.getMessage());
        }
    }
    
    /**
     * Gets all terms and their frequencies for a specific document.
     * 
     * @param docID The document ID to retrieve terms for
     * @return A map of terms to their frequencies in the document
     */
    public HashMap<String, Integer> getDocumentTerms(int docID) {
        HashMap<String, Integer> terms = new HashMap<>();
        try {
            // Read term frequencies from the binary file
            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(new FileInputStream("term_freqs.bin")))) {
                while (dis.available() > 0) {
                    int currentDocID = dis.readInt();
                    int numTerms = dis.readInt();
                    if (currentDocID == docID) {
                        // Found the document, read all its terms
                        for (int i = 0; i < numTerms; i++) {
                            String token = dis.readUTF();
                            int tf = dis.readInt();
                            terms.put(token, tf);
                        }
                        break; // Found what we were looking for
                    } else {
                        // Skip this document's terms
                        for (int i = 0; i < numTerms; i++) {
                            dis.readUTF(); // Skip term
                            dis.readInt(); // Skip frequency
                        }
                    }
                }
            }
            return terms;
        } catch (IOException e) {
            System.err.println("Error retrieving document terms: " + e.getMessage());
            return null;
        }
    }
    
    public void readPageRanks() { 
        HashMap<Integer, String> titles = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("pagerank/davisTitles.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");
                int pageId = Integer.parseInt(parts[0]);
                String pageTitle = parts[1];
                titles.put(pageId, pageTitle);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedReader br = new BufferedReader(new FileReader(Engine.rank_file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                int pageId = Integer.parseInt(parts[0]);
                String pageTitle = titles.get(pageId);
                int docId = getDocIdFromName(pageTitle);
                double rank = Double.parseDouble(parts[1]);
                pageRanks.put(docId, rank);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static int getDocIdFromName(String targetName) {
        for (Entry<Integer, String> entry : Index.docNames.entrySet()) {
            if (entry.getValue().equals("davisWiki/" + targetName)) {
                return entry.getKey();
            }
        }
        throw new RuntimeException("Document ID not found for: " + targetName);
    }

    public HashMap<String, HashMap<Integer, Integer>> getTermFreqs(ArrayList<Integer> docIDs) {
        // To store the term freqs for each document
        HashMap<String, HashMap<Integer, Integer>> termFreqs = new HashMap<>();

        // Read term_freq file
        try (DataInputStream dis = new DataInputStream(
            new BufferedInputStream(new FileInputStream("term_freqs.bin")))) {
            
        // Continue until EOF
        while (dis.available() > 0) {
            int currentDocID = dis.readInt();
            int numTerms = dis.readInt();
            // If row matches a docID, extract term freqs
            if (docIDs.contains(currentDocID)) { 
                // Iterate over the stated number of terms and add them to map
                for (int i = 0; i < numTerms; i++) {
                    String token = dis.readUTF();
                    int freq = dis.readInt();
                    
                    // Create inner map if it doesn't exist
                    if (!termFreqs.containsKey(token)) {
                        termFreqs.put(token, new HashMap<>());
                    }
                    
                    // Store frequency for this term in this document
                    termFreqs.get(token).put(currentDocID, freq);
                }
            } else {
                // Skip this document's terms
                for (int i = 0; i < numTerms; i++) {
                    dis.readUTF(); 
                    dis.readInt(); 
                } 
            } 
        }
    } catch (IOException e) {
        System.err.println("Error reading term_freqs.bin: " + e.getMessage());
    }
    return termFreqs;
}

public static void main( String[] args ) {
    Engine e = new Engine( args );
}

}

