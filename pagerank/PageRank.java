import java.util.*;
import java.io.*;

public class PageRank {

    /**  
     *   Maximal number of documents. We're assuming here that we
     *   don't have more docs than we can keep in main memory.
     */
    final static int MAX_NUMBER_OF_DOCS = 2000000;

    /**
     *   Mapping from document names to document numbers.
     */
    HashMap<String,Integer> docNumber = new HashMap<String,Integer>();

    /**
     *   Mapping from document numbers to document names
     */
    String[] docName = new String[MAX_NUMBER_OF_DOCS];

    /**  
     *   A memory-efficient representation of the transition matrix.
    
	 *   The outlinks are represented as a HashMap, whose keys are 
     *   the numbers of the documents linked from.<p>
     *
     *   The value corresponding to key i is a HashMap whose keys are 
     *   all the numbers of documents j that i links to.<p>
     *
     *   If there are no outlinks from i, then the value corresponding 
     *   key i is null.
     */
    HashMap<Integer,HashMap<Integer,Boolean>> link = new HashMap<Integer,HashMap<Integer,Boolean>>();

    /**
     *   The number of outlinks from each node.
     */
    int[] out = new int[MAX_NUMBER_OF_DOCS];

    /**
     *   The probability that the surfer will be bored, stop
     *   following links, and take a random jump somewhere.
     */
    final static double BORED = 0.15;

    /**
     *   Convergence criterion: Transition probabilities do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.0001;

	static final int[] topDocIDs = {245, 121, 21, 31, 1040, 80, 452, 392, 561, 8, 884, 100, 169,
									 72, 202, 157, 942, 997, 645, 321, 27, 1200, 247, 81, 1158,
									490, 179, 145, 16, 484};
    
	private PrintWriter mcWriter;
       
    /* --------------------------------------------- */

	public PageRank( String filename ) {
        int noOfDocs = readDocs( filename );
        // iterate( noOfDocs, 1000 );
        mc4Wiki(noOfDocs, 400000);
		// // Open the output file (overwrite if it exists)
        // try {
        //     mcWriter = new PrintWriter(new FileWriter("MCranks.txt", false));
        // } catch(IOException e) {
        //     e.printStackTrace();
        // }
        
        // // Define several values of N to try
		// int start = 5000;
		// int stop = 50000;
		// int num = 20;
        // int[] Ns = new int[num];
        // for (int i = 0; i < num; i++) {
        //     Ns[i] = start + (int) ((stop - start) * i / (double) (num-1));
        // }
        
        
		// // For each value of N, run each Monte Carlo method.
		// // Do it several times for statistics
		// int numSamples = 100;
		// for (int h = 0; h < numSamples; h++) {
        // 	if (h % (numSamples/5) == 0) System.out.println("Sample iteration " + h + "/" +numSamples);
		// 	for (int N : Ns) {
		// 		mc1(noOfDocs, N);
		// 		mc2(noOfDocs, N);
		// 		mc4(noOfDocs, N);
		// 		mc5(noOfDocs, N);
		// 	}
        // }
        
        // mcWriter.close();
    }
    /* --------------------------------------------- */


    /**
     *   Reads the documents and fills the data structures. 
     *
     *   @return the number of documents read.
     */
    int readDocs( String filename ) {
		int fileIndex = 0;
		try {
			System.err.print( "Reading file... " );
			BufferedReader in = new BufferedReader( new FileReader( filename ));
			String line;
			while ((line = in.readLine()) != null && fileIndex<MAX_NUMBER_OF_DOCS ) {
			int index = line.indexOf( ";" );
			String title = line.substring( 0, index );
			Integer fromdoc = docNumber.get( title );
			//  Have we seen this document before?
			if ( fromdoc == null ) {	
				// This is a previously unseen doc, so add it to the table.
				fromdoc = fileIndex++;
				docNumber.put( title, fromdoc );
				docName[fromdoc] = title;
			}
			// Check all outlinks.
			StringTokenizer tok = new StringTokenizer( line.substring(index+1), "," );
			while ( tok.hasMoreTokens() && fileIndex<MAX_NUMBER_OF_DOCS ) {
				String otherTitle = tok.nextToken();
				Integer otherDoc = docNumber.get( otherTitle );
				if ( otherDoc == null ) {
				// This is a previousy unseen doc, so add it to the table.
				otherDoc = fileIndex++;
				docNumber.put( otherTitle, otherDoc );
				docName[otherDoc] = otherTitle;
				}
				// Set the probability to 0 for now, to indicate that there is
				// a link from fromdoc to otherDoc.
				if ( link.get(fromdoc) == null ) {
				link.put(fromdoc, new HashMap<Integer,Boolean>());
				}
				if ( link.get(fromdoc).get(otherDoc) == null ) {
				link.get(fromdoc).put( otherDoc, true );
				out[fromdoc]++;
				}
			}
			}
			if ( fileIndex >= MAX_NUMBER_OF_DOCS ) {
			System.err.print( "stopped reading since documents table is full. " );
			}
			else {
			System.err.print( "done. " );
			}
		}
		catch ( FileNotFoundException e ) {
			System.err.println( "File " + filename + " not found!" );
		}
		catch ( IOException e ) {
			System.err.println( "Error reading file " + filename );
		}
		System.err.println( "Read " + fileIndex + " number of documents" );
		return fileIndex;
    }


    /* --------------------------------------------- */


    /*
     *   Chooses a probability vector a, and repeatedly computes
     *   aP, aP^2, aP^3... until aP^i = aP^(i+1).
     */
    void iterate(int N, int maxIterations) {
		double[] PR = new double[N];
		Arrays.fill(PR, 1.0 / N); // Initialize PR(p) with uniform distribution
		double c = 1.0 - BORED; 
	
		int iteration = 0;
		while (iteration < maxIterations) {
			double[] PR_new = new double[N];
			
			// --- J matrix part ---
			for (int p = 0; p < N; p++) {
				double j = (1.0 - c) / N;  // this is the (1-c)*J term
				PR_new[p] = j; // has equal contribution to all
			}
			
			// --- P matrix part ---
			for (int q = 0; q < N; q++) {
				// Check if page q has any outlinks.
				if (link.containsKey(q) && link.get(q) != null) {
					HashMap<Integer, Boolean> outlinks = link.get(q);
					int L_q = out[q]; 
					for (Integer p : outlinks.keySet()) {
						PR_new[p] += c * PR[q] / L_q;  // add contribution from page q
					}
				} else {
					// If q is a sink, distribute uniformly to all pages
					for (int p = 0; p < N; p++) {
						PR_new[p] += c * PR[q] / N;
					}
				}
			}
			
			// --- Convergence Check ---
			double diff = 0.0;
			for (int p = 0; p < N; p++) {
				diff += Math.abs(PR_new[p] - PR[p]); //|x - x'|
			}
			if (diff < EPSILON) {
				break;
			}
			
			// Prepare for the next iteration
			PR = Arrays.copyOf(PR_new, N);
			iteration++;
		}
		
		writeToFile(PR, N, "pageRanks.txt", false);
	}
    /* --------------------------------------------- */
	
	void mc1(int n, int N) {
        double[] PR = new double[n];
        for (int t = 0; t < N; t++) {
            int doc = (int) (Math.random() * n);
            boolean terminate = false;
            do {
                HashMap<Integer, Boolean> outlinks = link.get(doc);
                if (Math.random() < BORED) {
                    PR[doc] += 1.0 / N;
                    terminate = true;
                }else if (outlinks == null) {
                    doc = (int) (Math.random() * n);
                } else {
                    int randomKey = (int) (Math.random() * outlinks.size());
                    doc = new ArrayList<>(outlinks.keySet()).get(randomKey);
                }
            } while (!terminate);
        }
		
        writeMCResult("mc1", N, PR);
    }

    /**
     *   Monte Carlo method MC2.
     */
    void mc2(int n, int N) {
        int m = Math.max(1, N / n);
        double[] PR = new double[n];
        for (int i = 0; i < n; i++) { 
            for (int j = 0; j < m; j++) {
                int doc = i;
                boolean terminate = false;
                do {
                    HashMap<Integer, Boolean> outlinks = link.get(doc);
                    if (Math.random() < BORED) {
                        PR[doc] += 1.0 / (n * m);
                        terminate = true;
                    } else if (outlinks == null) {
                        doc = (int) (Math.random() * n);
                    } else {
                        int randomKey = (int) (Math.random() * outlinks.size());
                        doc = new ArrayList<>(outlinks.keySet()).get(randomKey);
                    }
                } while (!terminate);
            }
        }
		
        writeMCResult("mc2", N, PR);
    }

    /**
     *   Monte Carlo method MC4.
     */
    void mc4(int n, int N) {
		int m = Math.max(1, N / n);
		double[] PR = new double[n];
		int visits = 0;  // Initialize to 0
		for (int i = 0; i < n; i++) { 
			for (int j = 0; j < m; j++) {
				int doc = i;
				boolean terminate = false;
				do {
					HashMap<Integer, Boolean> outlinks = link.get(doc);
					if (outlinks == null) {
						PR[doc] += 1.0;
						terminate = true;
						visits++;  // Count termination step
					} else if (Math.random() < BORED) {
						PR[doc] += 1.0;
						terminate = true;
						visits++;  // Count termination step
					} else {
						PR[doc] += 1.0;
						int randomKey = (int) (Math.random() * outlinks.size());
						doc = new ArrayList<>(outlinks.keySet()).get(randomKey);
						visits++;  // Already counted for transitions
					}
				} while (!terminate);
			}
		}
		// Normalize by total visits
		for (int j = 0; j < PR.length; j++) {
			PR[j] = PR[j] / visits;
		}
		writeMCResult("mc4", N, PR);
	}

    /**
     *   Monte Carlo method MC5.
     */
    void mc5(int n, int N) {
        double[] PR = new double[n];
        int visits = 0;
        for (int t = 0; t < N; t++) {
            int doc = (int) (Math.random() * n);
            boolean terminate = false;
            do {
                HashMap<Integer, Boolean> outlinks = link.get(doc);
                if (outlinks == null) {
                    PR[doc] += 1.0;
                    terminate = true;
					visits++;
                } else if (Math.random() < BORED) {
                    PR[doc] += 1.0;
                    terminate = true;
					visits++;
                } else {
                    PR[doc] += 1.0;
                    int randomKey = (int) (Math.random() * outlinks.size());
                    doc = new ArrayList<>(outlinks.keySet()).get(randomKey);
                    visits++;
                }
            } while (!terminate);
        }
        for (int j = 0; j < PR.length; j++) {
            PR[j] = PR[j] / visits;
        }
		
        writeMCResult("mc5", N, PR);
    }
	
	void mc4Wiki(int n, int N) {
		int m = Math.max(1, N / n);
		double[] PR = new double[n];
		int visits = 0;  // Initialize to 0
		for (int i = 0; i < n; i++) { 
			for (int j = 0; j < m; j++) {
				int doc = i;
				boolean terminate = false;
				do {
					HashMap<Integer, Boolean> outlinks = link.get(doc);
					if (outlinks == null) {
						PR[doc] += 1.0;
						terminate = true;
						visits++;  // Count termination step
					} else if (Math.random() < BORED) {
						PR[doc] += 1.0;
						terminate = true;
						visits++;  // Count termination step
					} else {
						PR[doc] += 1.0;
						int randomKey = (int) (Math.random() * outlinks.size());
						doc = new ArrayList<>(outlinks.keySet()).get(randomKey);
						visits++;  // Already counted for transitions
					}
				} while (!terminate);
			}
		}
		// Normalize by total visits
		for (int j = 0; j < PR.length; j++) {
			PR[j] = PR[j] / visits;
		}
		writeToFile(PR, n, "wikiPageRanks.txt", true);
	}

	private void writeMCResult(String mcType, int N, double[] PR) {
        for (int p = 0; p < PR.length; p++) {
            try {
                int docId = Integer.parseInt(docName[p]);
                for (int topId : topDocIDs) {
                    if (docId == topId) {
                        mcWriter.println(mcType + ", " + N + ", " + docName[p] + ", " 
                                + PR[p]);
                        break;
                    }
                }
            } catch (NumberFormatException e) {
                
            }
        }
        mcWriter.flush();
    }

	private void writeToFile( double[] PR, int N, String title, boolean top30) {
		// After convergence, sort the pages by their PageRank values and store the top 30 in a file.
		List<Integer> pages = new ArrayList<>();
		for (int p = 0; p < N; p++) {
			pages.add(p);
		}

		// Sort pages in descending order based on PR[p] (i.e., highest PageRank first)
		pages.sort((p1, p2) -> Double.compare(PR[p2], PR[p1]));

		// Determine how many results to write (either top 30 or all)
		int limit = top30 ? Math.min(30, N) : N;

		// Write the top 30 results to file
		try (PrintWriter writer = new PrintWriter(new FileWriter(title))) {
			for (int i = 0; i < limit; i++) {
				int p = pages.get(i);
				writer.println(docName[p] + ": " + PR[p]);
        	}
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}

    public static void main( String[] args ) {
	if ( args.length != 1 ) {
	    System.err.println( "Please give the name of the link file" );
	}
	else {
	    new PageRank( args[0] );
	}
    }
}


