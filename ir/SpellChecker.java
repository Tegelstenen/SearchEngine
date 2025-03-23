/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;


public class SpellChecker {
    /** The regular inverted index to be used by the spell checker */
    Index index;

    /** K-gram index to be used by the spell checker */
    KGramIndex kgIndex;

    /** The auxiliary class for containing the value of your ranking function for a token */
    class KGramStat implements Comparable {
        double score;
        String token;

        KGramStat(String token, double score) {
            this.token = token;
            this.score = score;
        }

        public String getToken() {
            return token;
        }

        public int compareTo(Object other) {
            if (this.score == ((KGramStat)other).score) return 0;
            return this.score < ((KGramStat)other).score ? -1 : 1;
        }

        public String toString() {
            return token + ";" + score;
        }
    }

    /**
     * The threshold for Jaccard coefficient; a candidate spelling
     * correction should pass the threshold in order to be accepted
     */
    private static final double JACCARD_THRESHOLD = 0.4;


    /**
      * The threshold for edit distance for a candidate spelling
      * correction to be accepted.
      */
    private static final int MAX_EDIT_DISTANCE = 2;


    public SpellChecker(Index index, KGramIndex kgIndex) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    /**
     *  Computes the Jaccard coefficient for two sets A and B, where the size of set A is 
     *  <code>szA</code>, the size of set B is <code>szB</code> and the intersection 
     *  of the two sets contains <code>intersection</code> elements.
     */
    private double jaccard(int szA, int szB, int intersection) {
        return (double) intersection/(szA + szB - intersection);
    }

    /**
     * Computing Levenshtein edit distance using dynamic programming.
     * Allowed operations are:
     *      => insert (cost 1)
     *      => delete (cost 1)
     *      => substitute (cost 2)
     */
    private int editDistance(String s1, String s2) {
        int[][] matr = new int[s1.length()+1][s2.length()+1];
        
        // Initialize first col
        for (int i = 0; i <= s1.length(); i++) {
            matr[i][0] = i;
        }
        
        // Initialize first row
        for (int j = 0; j <= s2.length(); j++) {
            matr[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for(int j = 1; j <= s2.length(); j++) {
                // Cost is 0 if characters match, 1 if they don't
                int diagonalCost = s1.charAt(i-1) == s2.charAt(j-1) ? 0 : 2;
                
                // Calculate minimum of three options
                int deletion = matr[i-1][j] + 1;
                int insertion = matr[i][j-1] + 1;
                int substitution = matr[i-1][j-1] + diagonalCost;
                
                matr[i][j] = Math.min(substitution, Math.min(deletion, insertion));
            }
        }
        
        return matr[s1.length()][s2.length()];
    }

    /**
     *  Checks spelling of all terms in <code>query</code> and returns up to
     *  <code>limit</code> ranked suggestions for spelling correction.
     */
    public String[] check(Query query, int limit) {
        
        // Get all kgrams of query
        HashSet<String> queryKGrams = new HashSet<>();
        for(Query.QueryTerm queryTerm: query.queryTerms) { 
            queryKGrams.addAll(getKgrams(queryTerm.term));
        }
        int szA = queryKGrams.size();

        // Store candidates with their scores
        List<KGramStat> candidates = new ArrayList<>();
        HashSet<String> seenTerms = new HashSet<>(); // To avoid duplicates
        
        // Get all terms' edit distance with Jaccard > THRESHOLD 
        for(String queryKGram: queryKGrams) {
            // For each kgram in query, find matching terms
            List<KGramPostingsEntry> postingEntries = kgIndex.getPostings(queryKGram);
            if (postingEntries == null) continue;
            
            for (KGramPostingsEntry entry: postingEntries) {
                int tokenID = entry.tokenID;
                String term = kgIndex.getTermByID(tokenID);
                
                // Skip if we've already processed this term
                if (seenTerms.contains(term)) continue;
                seenTerms.add(term);
                
                // Get the kgrams of those matching terms
                HashSet<String> termKgrams = getKgrams(term);
                int union = getUnionCardinality(queryKGrams, termKgrams);
                int szB = kgIndex.getNumKgrams(term);
                int intersection = szA + szB - union;
                double jacCoef = jaccard(szA, szB, intersection);
                
                // If jaccard is high enough, check edit distance
                if (jacCoef >= JACCARD_THRESHOLD) {
                    String queryTerm = query.queryTerms.get(0).term; // ! will fail for multi-word error corrections
                    int distance = editDistance(term, queryTerm);
                    // If low enough distance, get score and add to candidate list
                    if (distance <= MAX_EDIT_DISTANCE) {
                        // Ranking formula: combine jaccard similarity and edit distance
                        // Higher jaccard and lower edit distance = better score
                        double score = jacCoef / (distance + 1.0);
                        candidates.add(new KGramStat(term, score));
                    }
                }
            }
        }
        
        // Sort candidates by score (descending)
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        
        // Take top 'limit' results
        int resultSize = Math.min(limit, candidates.size());
        String[] result = new String[resultSize];
        for (int i = 0; i < resultSize; i++) {
            result[i] = candidates.get(i).getToken();
        }
        
        return result;
    }

    private int getUnionCardinality(HashSet<String> a, HashSet<String> b) {
        HashSet<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.size();
    }

    private HashSet<String> getKgrams(String token){
        HashSet<String> kgrams = new HashSet<>();
        String term = "^" + token + "$";
        int K = kgIndex.getK();
        if (term.length() >= K) {
            for (int i = 0; i <= term.length() - K; i++) {
                String kgram = term.substring(i, i + K);
                kgrams.add(kgram);
            }
        }
        return kgrams;
    }

    /**
     *  Merging ranked candidate spelling corrections for all query terms available in
     *  <code>qCorrections</code> into one final merging of query phrases. Returns up
     *  to <code>limit</code> corrected phrases.
     */
    private List<KGramStat> mergeCorrections(List<List<KGramStat>> qCorrections, int limit) {
        //
        // YOUR CODE HERE
        //
        return null;
    }
}
