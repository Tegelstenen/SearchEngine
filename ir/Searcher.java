/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

import ir.Query.QueryTerm;

/**
 *  Searches an index for results of a query.
 */
public class Searcher {

    /** The index to be searched by this Searcher. */
    Index index;

    /** The k-gram index to be searched by this Searcher */
    KGramIndex kgIndex;

    private double N = Index.docNames.size();
    
    /** Constructor */
    public Searcher( Index index, KGramIndex kgIndex ) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    private final double rankWeight = 1;
    private final double tfidfWeight = 1;
    /**
     *  Searches the index for postings matching the query.
     *  @return A postings list representing the result of the query.
     */
    public PostingsList search(Query query, QueryType queryType, RankingType rankingType, NormalizationType normType) { 
        Queue<PostingsList> postingLists = getPostingLists(query);
        
        // If there are no posting lists, return an empty list.
        if (postingLists.isEmpty()) {
            return new PostingsList();
        }

        Query query = wildcard(query);
        
        // Handle ranked queries separately.
        if (queryType == QueryType.RANKED_QUERY) {
            return rankedSearch(query, rankingType, normType);
        }
        
        // For intersection and phrase queries, iteratively combine posting lists.
        PostingsList intersection = postingLists.poll();
        while (!postingLists.isEmpty()) {
            PostingsList p2 = postingLists.poll();
            if (queryType == QueryType.INTERSECTION_QUERY) {
                intersection = intersect(intersection, p2);
            } else if (queryType == QueryType.PHRASE_QUERY) {
                intersection = positionalIntersect(intersection, p2);
            }
        }
        return intersection;
    }

    private Queue<PostingsList> getPostingLists(Query query) {
        Queue<PostingsList> postingLists = new LinkedList<>();
        for (QueryTerm queryTerm : query.queryTerms) {
            String term = queryTerm.term;
            PostingsList postingList = this.index.getPostings(term);
            postingLists.offer(postingList);
        }
        return postingLists;
    }


    private PostingsList intersect(PostingsList p1, PostingsList p2) {
        PostingsList answer = new PostingsList();
        int docIndex1 = 0;
        int docIndex2 = 0;

        while (docIndex1 < p1.size() && docIndex2 < p2.size()) {
            int docID1 = p1.get(docIndex1).docID;
            int docID2 = p2.get(docIndex2).docID;
    
            if (docID1 == docID2) {
                // Add the common document ID to the answer
                answer.add(docID1, 0);
                docIndex1++;
                docIndex2++;
            } else if (docID1 > docID2) {
                // Move to the next document in p2
                docIndex2++;
            } else {
                // Move to the next document in p1
                docIndex1++;
            }
        }
        return answer;
    }

    private PostingsList positionalIntersect(PostingsList p1, PostingsList p2) {
        PostingsList answer = new PostingsList();
        int docIndex1 = 0;
        int docIndex2 = 0;  
        while (docIndex1 < p1.size() && docIndex2 < p2.size()) {
            int docID1 = p1.get(docIndex1).docID;
            ArrayList<Integer> offsets1 = p1.get(docIndex1).offsets;
            int docID2 = p2.get(docIndex2).docID;
            ArrayList<Integer> offsets2 = p2.get(docIndex2).offsets;
            if (docID1 == docID2) {
                int offsetIndex1 = 0;
                int offsetIndex2 = 0;  
                while (offsetIndex1 < offsets1.size() && offsetIndex2 < offsets2.size()) {
                    int offset1 = offsets1.get(offsetIndex1);
                    int offset2 = offsets2.get(offsetIndex2);
                    if (offset1 - offset2 == -1) {
                        answer.add(docID1, offset2);
                        offsetIndex1++;
                        offsetIndex2++;
                    } else if(offset1 < offset2) {
                        offsetIndex1++;
                    } else {
                        offsetIndex2++;
                    }
                }
                docIndex1++;
                docIndex2++;
            } else if (docID1 > docID2) {
                // Move to the next document in p2
                docIndex2++;
            } else {
                // Move to the next document in p1
                docIndex1++;
            }
        }
        return answer;
    }
    
    private PostingsList rankedSearch(Query query, RankingType rankingType, NormalizationType normType) {
        PostingsList scores = new PostingsList();
        
        for (QueryTerm queryTerm: query.queryTerms) {
            String term = queryTerm.term;
            double termWeight = queryTerm.weight;

            PostingsList postingsList = this.index.getPostings(term);
            if (postingsList == null) continue;
            double docFreq = postingsList.size();
            double idf = Math.log(N/docFreq);
        
            for (PostingsEntry entry : postingsList) {

                if (rankingType == RankingType.TF_IDF || rankingType == RankingType.COMBINATION || rankingType == RankingType.HITS) {
                    double tf = entry.offsets.size();
                    double docLen = 1;
                    if (normType == NormalizationType.NUMBER_OF_WORDS) {
                        docLen = Index.docLengths.get(entry.docID);
                    } else if (normType == NormalizationType.EUCLIDEAN) {
                        docLen = Index.docEucLen.get(entry.docID);
                    }
                    double tfidf = tf * idf / docLen;
                    scores.add(entry.docID, 0, tfidf * tfidfWeight * termWeight);
                }

                if (rankingType == RankingType.PAGERANK || rankingType == RankingType.COMBINATION) {
                    double rank = Engine.pageRanks.get(entry.docID);
                    scores.add(entry.docID, 0, rank * rankWeight);
                }
            }    
        }    
        
        scores.sort();
        if (rankingType == RankingType.HITS) {
            scores.subList(10);
            scores = Engine.hr.rank(scores);
            scores.sort();
        }

        return scores;
    }

    private Query wildcard(Query query) {
        boolean containsWildcard = query.queryTerms.stream().anyMatch(term -> term.term.contains("*"));
        if(!containsWildcard) {
            return query;
        } else {
            kgIndex.
        }
    }
}