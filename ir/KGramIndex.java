/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;


public class KGramIndex {

    /** Mapping from term ids to actual term strings */
    HashMap<Integer,String> id2term = new HashMap<Integer,String>();

    /** Mapping from term strings to term ids */
    HashMap<String,Integer> term2id = new HashMap<String,Integer>();

    /** Index from k-grams to list of term ids that contain the k-gram */
    HashMap<String,List<KGramPostingsEntry>> index = new HashMap<String,List<KGramPostingsEntry>>();

    /** The ID of the last processed term */
    int lastTermID = -1;

    /** Number of symbols to form a K-gram */
    int K = 3;

    public KGramIndex(int k) {
        K = k;
        if (k <= 0) {
            System.err.println("The K-gram index can't be constructed for a negative K value");
            System.exit(1);
        }
    }

    /** Generate the ID for an unknown term */
    private int generateTermID() {
        return ++lastTermID;
    }

    public int getK() {
        return K;
    }


    /**
     *  Get intersection of two postings lists
     */
    private List<KGramPostingsEntry> intersect(List<KGramPostingsEntry> p1, List<KGramPostingsEntry> p2) {
        List<KGramPostingsEntry> answer = new ArrayList<KGramPostingsEntry>();
        int tokenIndex1 = 0;
        int tokenIndex2 = 0;

        while (tokenIndex1 < p1.size() && tokenIndex2 < p2.size()) {
            int tokenID1 = p1.get(tokenIndex1).tokenID;
            int tokenID2 = p2.get(tokenIndex2).tokenID;
    
            if (tokenID1 == tokenID2) {
                // Add the common token ID to the answer
                KGramPostingsEntry entry = new KGramPostingsEntry(tokenID1);
                answer.add(entry);
                tokenIndex1++;
                tokenIndex2++;
            } else if (tokenID1 > tokenID2) {
                // Move to the next token in p2
                tokenIndex2++;
            } else {
                // Move to the next token in p1
                tokenIndex1++;
            }
        }
        return answer;
    }


    /** Inserts all k-grams from a token into the index. */
    public void insert(String token) {
        // store token in id2term only once per token
        if (!term2id.containsKey(token)) {
            int tokenId = generateTermID();
            id2term.put(tokenId, token);
            term2id.put(token, tokenId);
        }
        int tokenId = term2id.get(token);
        
        String rgxToken = "^" + token + "$";
        
        for (int i = 0; i <= rgxToken.length() - K; i++) {
            String kgram = rgxToken.substring(i, i + K);
            KGramPostingsEntry entry = new KGramPostingsEntry(tokenId);
            
            // Check if this kgram exists in the index
            List<KGramPostingsEntry> postingsList = index.computeIfAbsent(kgram, k -> new ArrayList<KGramPostingsEntry>());
            
            // Check if this tokenId is already in the postings list before adding
            boolean exists = false;
            for (KGramPostingsEntry existing : postingsList) {
                if (existing.tokenID == tokenId) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                postingsList.add(entry);
            }
        }
    }

    /** Get postings for the given k-gram */
    public List<KGramPostingsEntry> getPostings(String kgram) {
        return index.get(kgram);
    }

    /** Get id of a term */
    public Integer getIDByTerm(String term) {
        return term2id.get(term);
    }

    /** Get a term by the given id */
    public String getTermByID(Integer id) {
        return id2term.get(id);
    }

    private static HashMap<String,String> decodeArgs( String[] args ) {
        HashMap<String,String> decodedArgs = new HashMap<String,String>();
        int i=0, j=0;
        while ( i < args.length ) {
            if ( "-p".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("patterns_file", args[i++]);
                }
            } else if ( "-f".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("file", args[i++]);
                }
            } else if ( "-k".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("k", args[i++]);
                }
            } else if ( "-kg".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    decodedArgs.put("kgram", args[i++]);
                }
            } else {
                System.err.println( "Unknown option: " + args[i] );
                break;
            }
        }
        return decodedArgs;
    }

    public static void main(String[] arguments) throws FileNotFoundException, IOException {
        HashMap<String,String> args = decodeArgs(arguments);

        int k = Integer.parseInt(args.getOrDefault("k", "3"));
        KGramIndex kgIndex = new KGramIndex(k);

        File f = new File(args.get("file"));
        Reader reader = new InputStreamReader( new FileInputStream(f), StandardCharsets.UTF_8 );
        Tokenizer tok = new Tokenizer( reader, true, false, true, args.get("patterns_file") );
        while ( tok.hasMoreTokens() ) {
            String token = tok.nextToken();
            kgIndex.insert(token);
        }

        String[] kgrams = args.get("kgram").split(" ");
        List<KGramPostingsEntry> postings = null;
        for (String kgram : kgrams) {
            if (kgram.length() != k) {
                System.err.println("Cannot search k-gram index: " + kgram.length() + "-gram provided instead of " + k + "-gram");
                System.exit(1);
            }

            if (postings == null) {
                postings = kgIndex.getPostings(kgram);
            } else {
                postings = kgIndex.intersect(postings, kgIndex.getPostings(kgram));
            }
        }
        if (postings == null) {
            System.err.println("Found 0 posting(s)");
        } else {
            int resNum = postings.size();
            System.err.println("Found " + resNum + " posting(s)");
            if (resNum > 10) {
                System.err.println("The first 10 of them are:");
                resNum = 10;
            }
            for (int i = 0; i < resNum; i++) {
                System.err.println(kgIndex.getTermByID(postings.get(i).tokenID));
            }
        }
    }
}
