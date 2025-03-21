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
import java.util.StringTokenizer;
import java.util.Iterator;
import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore.Entry;
import java.io.*;
import java.util.Map;


/**
 *  A class for representing a query as a list of words, each of which has
 *  an associated weight.
 */
public class Query {

    /**
     *  Help class to represent one query term, with its associated weight. 
     */
    class QueryTerm {
        String term;
        double weight;
        QueryTerm( String t, double w ) {
            term = t;
            weight = w;
        }
    }

    /** 
     *  Representation of the query as a list of terms with associated weights.
     *  In assignments 1 and 2, the weight of each term will always be 1.
     */
    public ArrayList<QueryTerm> queryTerms = new ArrayList<QueryTerm>();

    /**  
     *  Relevance feedback constant alpha (= weight of original query terms). 
     *  Should be between 0 and 1.
     *  (only used in assignment 3).
     */
    double alpha = 0.5;

    /**  
     *  Relevance feedback constant beta (= weight of query terms obtained by
     *  feedback from the user). 
     *  (only used in assignment 3).
     */
    double beta = 1 - alpha;
    
    
    /**
     *  Creates a new empty Query 
     */
    public Query() {
    }
    
    
    /**
     *  Creates a new Query from a string of words
     */
    public Query( String queryString  ) {
        StringTokenizer tok = new StringTokenizer( queryString );
        while ( tok.hasMoreTokens() ) {
            queryTerms.add( new QueryTerm(tok.nextToken(), 1.0) );
        }    
    }
    
    
    /**
     *  Returns the number of terms
     */
    public int size() {
        return queryTerms.size();
    }
    
    
    /**
     *  Returns the Manhattan query length
     */
    public double length() {
        double len = 0;
        for ( QueryTerm t : queryTerms ) {
            len += t.weight; 
        }
        return len;
    }
    
    
    /**
     *  Returns a copy of the Query
     */
    public Query copy() {
        Query queryCopy = new Query();
        for ( QueryTerm t : queryTerms ) {
            queryCopy.queryTerms.add( new QueryTerm(t.term, t.weight) );
        }
        return queryCopy;
    }
    
    
    /**
     *  Expands the Query using Relevance Feedback
     *
     *  @param results The results of the previous query.
     *  @param docIsRelevant A boolean array representing which query results the user deemed relevant.
     *  @param engine The search engine object
     */
    public void relevanceFeedback(PostingsList results, boolean[] docIsRelevant, Engine engine) {
        // Used to "normalize"
        double numRelevDocs = 0.0;
        for (Boolean doc: docIsRelevant) {
            if(Boolean.TRUE.equals(doc)) {
                numRelevDocs += 1.0;
            }
        }
        
        // If no documents are marked as relevant, do nothing
        if (numRelevDocs == 0.0) {
            return;
        }
    
        // Get all the relevant docIDs
        int i = 0;
        ArrayList<Integer> relevantDocIDs = new ArrayList<>();
        for (PostingsEntry entry: results) {
            if (i < docIsRelevant.length && docIsRelevant[i]) {
                relevantDocIDs.add(entry.docID);
                
            }
            i++;
        }
    
        // Get all the relevant term freqs
        HashMap<String, HashMap<Integer, Integer>> termFreqsPerDoc = engine.getTermFreqs(relevantDocIDs);
        
        // Make a map of new query terms with their weights
        HashMap<String, Double> newQueryTerms = new HashMap<>();
        
        // First add existing query terms with alpha weight
        for (QueryTerm queryTerm: queryTerms) {
            newQueryTerms.put(queryTerm.term, alpha * queryTerm.weight);
        }
    
        // Add terms from relevant documents with beta weight
        for (Map.Entry<String, HashMap<Integer, Integer>> entry : termFreqsPerDoc.entrySet()) {
            String term = entry.getKey();
            HashMap<Integer, Integer> docFreqs = entry.getValue();
            
            // For each relevant document containing this term
            for (Map.Entry<Integer, Integer> docFreq : docFreqs.entrySet()) {
                int tf = docFreq.getValue();
                double contribution = beta * tf / numRelevDocs;
                newQueryTerms.put(term, newQueryTerms.getOrDefault(term, 0.0) + contribution);
            }
        }
        
        // Clear the existing query terms and add the new ones
        queryTerms.clear();
        for (Map.Entry<String, Double> entry : newQueryTerms.entrySet()) {
            queryTerms.add(new QueryTerm(entry.getKey(), entry.getValue()));
        }
    }

    public String readDocument(int docID) {
        try {
            String docPath = Index.docNames.get(docID);
            if (docPath == null) {
                return null;
            }
            
            return new String(Files.readAllBytes(Paths.get(docPath)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}


