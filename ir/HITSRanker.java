/**
 *   Computes the Hubs and Authorities for an every document in a query-specific
 *   link graph, induced by the base set of pages.
 *
 *   @author Dmytro Kalpakchi
 */

package ir;

import java.util.*;
import java.io.*;
import java.lang.reflect.Array;


public class HITSRanker {

    /**
     *   Max number of iterations for HITS
     */
    final static int MAX_NUMBER_OF_STEPS = 1000;

    /**
     *   Convergence criterion: hub and authority scores do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.001;

    /**
     *   The inverted index
     */
    Index index;

    /**
     *   Mapping from the titles to internal document ids used in the links file
     */
    HashMap<String,Integer> titleToId = new HashMap<String,Integer>();
    HashMap<Integer,String> idToTitle = new HashMap<Integer,String> ();

    /**
     *   Sparse vector containing hub scores
     */
    HashMap<Integer,Double> hubs;

    /**
     *   Sparse vector containing authority scores
     */
    HashMap<Integer,Double> authorities;

    HashMap<Integer, ArrayList<Integer>> graph = new HashMap<>();

    HashSet<Integer> baseSet;

    
    /* --------------------------------------------- */

    /**
     * Constructs the HITSRanker object
     * 
     * A set of linked documents can be presented as a graph.
     * Each page is a node in graph with a distinct nodeID associated with it.
     * There is an edge between two nodes if there is a link between two pages.
     * 
     * Each line in the links file has the following format:
     *  nodeID;outNodeID1,outNodeID2,...,outNodeIDK
     * This means that there are edges between nodeID and outNodeIDi, where i is between 1 and K.
     * 
     * Each line in the titles file has the following format:
     *  nodeID;pageTitle
     *  
     * NOTE: nodeIDs are consistent between these two files, but they are NOT the same
     *       as docIDs used by search engine's Indexer
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     * @param      index           The inverted index
     */
    public HITSRanker( String linksFilename, String titlesFilename, Index index ) {
        this.index = index;
        readDocs( linksFilename, titlesFilename );
    }


    /* --------------------------------------------- */

    /**
     * A utility function that gets a file name given its path.
     * For example, given the path "davisWiki/hello.f",
     * the function will return "hello.f".
     *
     * @param      path  The file path
     *
     * @return     The file name.
     */
    private String getFileName( String path ) {
        String result = "";
        StringTokenizer tok = new StringTokenizer( path, "\\/" );
        while ( tok.hasMoreTokens() ) {
            result = tok.nextToken();
        }
        return result;
    }


    /**
     * Reads the files describing the graph of the given set of pages.
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     */
    void readDocs( String linksFilename, String titlesFilename ) {
        readTitles(titlesFilename);
        readLinks(linksFilename);
    }

    void readTitles(String titlesFilename) {
        int fileIndex = 0;
		try {
			System.err.print("Reading titles file... ");
            BufferedReader in = new BufferedReader(new FileReader(titlesFilename));
            String line;
            while ((line = in.readLine()) != null) {
                int index = line.indexOf(";");
                if (index != -1) { // Ensure the semicolon exists
                    int nodeID = Integer.parseInt(line.substring(0, index)); // Extract nodeID
                    String title = line.substring(index + 1); // Extract pageTitle
                    // Now you can use nodeID and title as needed
                    titleToId.put(title, nodeID);
                    idToTitle.put(nodeID, title);
                    fileIndex++;
                }
            }
		}
		catch ( FileNotFoundException e ) {
			System.err.println( "File " + titlesFilename + " not found!" );
		}
		catch ( IOException e ) {
			System.err.println( "Error reading file " + titlesFilename );
		}
		System.err.println( "Read " + fileIndex + " number of title documents" );
    }

    void readLinks(String linksFilename) {
        int fileIndex = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(linksFilename))) {
            System.err.print("Reading links file... ");
            String line;
            
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length < 2) continue;  // Skip lines without outgoing links
                
                int nodeID = Integer.parseInt(parts[0]); // First part is nodeID
                String[] outNodes = parts[1].split(",");

                ArrayList<Integer> neighbors = new ArrayList<>();
                for (String outNode : outNodes) {
                    if (!outNode.isEmpty()) {
                        neighbors.add(Integer.parseInt(outNode));
                    }
                }

                graph.put(nodeID, neighbors);
                fileIndex++;
            }
        } catch (FileNotFoundException e) {
            System.err.println("File " + linksFilename + " not found!");
        } catch (IOException e) {
            System.err.println("Error reading file " + linksFilename);
        }
        
        System.err.println("Read " + fileIndex + " number of link documents");
    }


    /**
     * Perform HITS iterations until convergence
     *
     * @param      titles  The titles of the documents in the root set
     */
    private void iterate(String[] titles) {
        System.out.println("Initialzing base set");
        // Create root set
        HashSet<Integer> rootSet = new HashSet<>();
        for (String title : titles) {
            Integer nodeId = titleToId.get(title);
            if (nodeId != null) {
                rootSet.add(nodeId);
            } else {
                System.err.println("Warning: title '" + title + "' not found in titleToId mapping.");
            }
        }

        // Create base set
        baseSet = new HashSet<>(rootSet);

        // Add out-links (nodes linked from root set)
        for (int nodeId : rootSet) {
            ArrayList<Integer> outLinks = graph.get(nodeId);
            if (outLinks != null) {
                baseSet.addAll(outLinks);
            }
        }

        // Add in-links (nodes linking to the root set)
        for (int nodeId = 0; nodeId < graph.size(); nodeId++) {
            ArrayList<Integer> links = graph.get(nodeId);
            if (links != null) {
                for (int link : links) {
                    if (rootSet.contains(link)) {
                        baseSet.add(nodeId);
                    }
                }
            }
        }

        // Precompute reverse links for nodes in baseSet
        Map<Integer, List<Integer>> inLinksMap = new HashMap<>();
        for (int nodeId : baseSet) {
            ArrayList<Integer> outLinks = graph.get(nodeId);
            if (outLinks != null) {
                for (int target : outLinks) {
                    // Only consider nodes that are in baseSet
                    if (baseSet.contains(target)) {
                        inLinksMap.computeIfAbsent(target, k -> new ArrayList<>()).add(nodeId);
                    }
                }
            }
        }

        // initialize authoriteis and hubs
        authorities = new HashMap<Integer,Double>(baseSet.size());
        hubs = new HashMap<Integer,Double>(baseSet.size());
        for (int nodeId : baseSet) {
            authorities.put(nodeId, 1.0);
            hubs.put(nodeId, 1.0);
        }

        System.out.println("Began iteration");

        // iterate
        boolean converged = false;
        int iters = 0;
        do {
            HashMap<Integer, Double> authoritiesTemp = new HashMap<>(baseSet.size());
            HashMap<Integer, Double> hubsTemp = new HashMap<>(baseSet.size());
            
            // Update authorities and hubs using precomputed in-links
            for (int nodeId : baseSet) {
                double authorityScore = 0.0;
                double hubScore = 0.0;
                
                // Sum contributions from in-links for authority
                List<Integer> inLinks = inLinksMap.get(nodeId);
                if (inLinks != null) {
                    for (int inNode : inLinks) {
                        authorityScore += hubs.get(inNode);
                    }
                }
                
                // Sum contributions from out-links for hub
                ArrayList<Integer> outLinks = graph.get(nodeId);
                if (outLinks != null) {
                    for (int outNode : outLinks) {
                        // Only consider nodes in the base set
                        if (baseSet.contains(outNode)) {
                            hubScore += authorities.get(outNode);
                        }
                    }
                }
                authoritiesTemp.put(nodeId, authorityScore);
                hubsTemp.put(nodeId, hubScore);
            } 
            
            // Normalize and check convergence (combine loops if possible)
            double authDenom = 0.0;
            double hubDenom = 0.0;
            for (int nodeId : baseSet) {
                authDenom += Math.pow(authoritiesTemp.get(nodeId), 2);
                hubDenom += Math.pow(hubsTemp.get(nodeId), 2);
            }
            double authNormalizingFactor = 1/Math.sqrt(authDenom);
            double hubNormalizingFactor = 1/Math.sqrt(hubDenom);
            
            double authAbsDiff = 0.0;
            double hubAbsDiff = 0.0;
            for (int nodeId : baseSet) {
                double authNormalized = authoritiesTemp.get(nodeId) * authNormalizingFactor;
                double hubNormalized = hubsTemp.get(nodeId) * hubNormalizingFactor;
                authAbsDiff += Math.abs(authorities.get(nodeId) - authNormalized);
                hubAbsDiff += Math.abs(hubs.get(nodeId) - hubNormalized);
                
                // Update scores
                authorities.put(nodeId, authNormalized);
                hubs.put(nodeId, hubNormalized);
            }
            
            if (authAbsDiff < EPSILON && hubAbsDiff < EPSILON) {
                converged = true;
            }
            
            iters++;
        } while (!converged);
        System.out.println("HITS converged");
    }


    /**
     * Rank the documents in the subgraph induced by the documents present
     * in the postings list `post`.
     *
     * @param      post  The list of postings fulfilling a certain information need
     *
     * @return     A list of postings ranked according to the hub and authority scores.
     */
    PostingsList rank(PostingsList post) {
        PostingsList hubAuthQueryResult = new PostingsList();
        double authWeight = 0.5;
        double hubWeight = 0.5;
        String[] titles = new String[post.size()];
        int index = 0;
        for (PostingsEntry entry : post) {
            String docTitle = getFileName(Index.docNames.get(entry.docID));
            titles[index] = docTitle;
            index++;
        }

        iterate(titles);
        
        for (Integer nodeId : baseSet) {
            double h = hubs.get(nodeId);
            double a = authorities.get(nodeId);
            double linearCombScore = authWeight*a + hubWeight*h;
            String title = idToTitle.get(nodeId);
            Integer docId = Engine.getDocIdFromName(title);
            hubAuthQueryResult.add(docId, 0, linearCombScore);
        }
        return hubAuthQueryResult;
    }


    /**
     * Sort a hash map by values in the descending order
     *
     * @param      map    A hash map to sorted
     *
     * @return     A hash map sorted by values
     */
    private HashMap<Integer,Double> sortHashMapByValue(HashMap<Integer,Double> map) {
        if (map == null) {
            return null;
        } else {
            List<Map.Entry<Integer,Double> > list = new ArrayList<Map.Entry<Integer,Double> >(map.entrySet());
      
            Collections.sort(list, new Comparator<Map.Entry<Integer,Double>>() {
                public int compare(Map.Entry<Integer,Double> o1, Map.Entry<Integer,Double> o2) { 
                    return (o2.getValue()).compareTo(o1.getValue()); 
                } 
            }); 
              
            HashMap<Integer,Double> res = new LinkedHashMap<Integer,Double>(); 
            for (Map.Entry<Integer,Double> el : list) { 
                res.put(el.getKey(), el.getValue()); 
            }
            return res;
        }
    } 


    /**
     * Write the first `k` entries of a hash map `map` to the file `fname`.
     *
     * @param      map        A hash map
     * @param      fname      The filename
     * @param      k          A number of entries to write
     */
    void writeToFile(HashMap<Integer,Double> map, String fname, int k) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fname));
            
            if (map != null) {
                int i = 0;
                for (Map.Entry<Integer,Double> e : map.entrySet()) {
                    i++;
                    writer.write(e.getKey() + ": " + String.format("%.5g%n", e.getValue()));
                    if (i >= k) break;
                }
            }
            writer.close();
        } catch (IOException e) {}
    }


    /**
     * Rank all the documents in the links file. Produces two files:
     *  hubs_top_30.txt with documents containing top 30 hub scores
     *  authorities_top_30.txt with documents containing top 30 authority scores
     */
    void rank() {
        iterate(titleToId.keySet().toArray(new String[0]));
        HashMap<Integer,Double> sortedHubs = sortHashMapByValue(hubs);
        HashMap<Integer,Double> sortedAuthorities = sortHashMapByValue(authorities);
        writeToFile(sortedHubs, "hubs_top_30.txt", 30);
        writeToFile(sortedAuthorities, "authorities_top_30.txt", 30);
    }


    /* --------------------------------------------- */


    public static void main( String[] args ) {
        if ( args.length != 2 ) {
            System.err.println( "Please give the names of the link and title files" );
        }
        else {
            HITSRanker hr = new HITSRanker( args[0], args[1], null );
            hr.rank();
        }
    }
} 