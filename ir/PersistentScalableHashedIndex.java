package ir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class PersistentScalableHashedIndex extends PersistentHashedIndex {

    /** Count the tokens inserted since last flush. */
    private int tokenCount = 0;
    
    private static final long MAX_IN_MEMORY_TERMS = 12000000;
    
    private int intermediateCount = 1;
    private int mergeCount = 1;


    private String dictPath = INDEXDIR + "/" + DICTIONARY_FNAME;
    private String dataPath = INDEXDIR + "/" + DATA_FNAME;
    private String docPath = INDEXDIR + "/" + DOCINFO_FNAME;
    private String termPath = INDEXDIR + "/" + TERMS_FNAME;

    public class IndexFilePaths {
        // These fields represent the file paths for each component of the index.
        private final String dictionaryFilePath;
        private final String dataFilePath;
        private final String docInfoFilePath;
        private final String termFilePath;

        public IndexFilePaths(String dict, String data, String docInfo, String term) {
            this.dictionaryFilePath = dict;
            this.dataFilePath = data;
            this.docInfoFilePath = docInfo;
            this.termFilePath = term;
        }

        public void delete(){
            File dictionaryFile = new File(dictionaryFilePath);
            File dataFile = new File(dataFilePath);
            File docInfoFile = new File(docInfoFilePath);
            File termFile = new File(termFilePath);

            if (!dictionaryFile.delete()) {
                System.err.println("Failed to delete " + dictionaryFilePath);
            }
            
            if (!dataFile.delete()) {
                System.err.println("Failed to delete " + dataFilePath);
            }
            
            if (!docInfoFile.delete()) {
                System.err.println("Failed to delete " + docInfoFilePath);
            }
            
            if (!termFile.delete()) {
                System.err.println("Failed to delete " + termFilePath);
            }
        }
    }
    
    /** A list of intermediate indexes saved so far. */
    private Queue<IndexFilePaths> allIndexFilePaths = new LinkedList<>();
    
    private volatile Thread mergeThread = null;

    @Override
    public void insert(String token, int docID, int offset) {
        super.insert(token, docID, offset);
        tokenCount++;
        if (tokenCount >= MAX_IN_MEMORY_TERMS) {
            System.err.println("Memory threshold reached. Writing partial index #" + intermediateCount + "...");
            writePartialIndex();
            tokenCount = 0;
        }
    }
    
    @Override
    public PostingsList getPostings(String token) {
        try (
            RandomAccessFile dictionaryFile = new RandomAccessFile(dictPath, "rw");
            RandomAccessFile dataFile = new RandomAccessFile(dataPath, "rw");) {
            return getPostingsListFromFile(dictionaryFile, dataFile, token);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Flush the current in‑memory index to disk, renaming the files so that they
     * can later be merged. Also, flush the docInfo file.
     */
    private void writePartialIndex() {
            writeIndex(); // will write only over the standard path names, thus rename afterwards
            // Close the current files.
            try {
                dictionaryFile.close();
                dataFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            // Rename the dictionary and data files to unique names.
            String[] paths = {dictPath, dataPath, docPath, termPath};
            String[] newPaths = new String[paths.length];
            for (int i = 0; i < paths.length; i++) {
                newPaths[i] = paths[i] + "_" + intermediateCount;
                if (!new File(paths[i]).renameTo(new File(newPaths[i]))) {
                    System.err.println("Failed to rename " + paths[i] + " to " + newPaths[i]);
                }
            }
            // Save the file names in our list.
            allIndexFilePaths.add(new IndexFilePaths(newPaths[0], newPaths[1], newPaths[2], newPaths[3]));
            intermediateCount++;
            
            // Re-open dictionary and data files and reset pointer.
            try {
                dictionaryFile = new RandomAccessFile(dictPath, "rw");
                dataFile = new RandomAccessFile(dataPath, "rw");
                free = 0L;
                // Note: The docInfo and term file will be re-created by writeIndex() when needed.
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            // Clear in‑memory index as well as document info.
            index.clear();
            docNames.clear();
            docLengths.clear();
            
            // Try to start a merge if there are at least two intermediate indexes.
            if (allIndexFilePaths.size() >= 2 && 
               (mergeThread == null || !mergeThread.isAlive())) {
                System.err.println("Starting background merge thread for " + allIndexFilePaths.size() + " intermediate indexes");
                mergeThread = new Thread(this::mergeIntermediateIndexes);
                mergeThread.start();
            }
    }

    private void mergeIntermediateIndexes() {
            try {
                while (allIndexFilePaths.size() > 1) {
                    IndexFilePaths indexA = allIndexFilePaths.poll();
                    IndexFilePaths indexB = allIndexFilePaths.poll();
                    System.err.println("Merging indexes: " + indexA.dictionaryFilePath + " and " + indexB.dictionaryFilePath);
                    mergeTwoIndexes(indexA, indexB);
                    System.err.println("Cleaning up merged files...");
                    indexA.delete();
                    indexB.delete();
                    System.err.println("Merged two indexes; " + allIndexFilePaths.size() + " remain.");
                    
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    System.err.println("Merge interrupted");
                    Thread.currentThread().interrupt();
                } else {
                    System.err.println("Error during merge: " + e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                mergeThread = null;
            }
    }
    
    private void mergeTwoIndexes(IndexFilePaths indexA, IndexFilePaths indexB) throws IOException {
        // new paths for the merged files
        String mergedDictPath = dictPath + "_merged_" + mergeCount;
        String mergedDataPath = dataPath + "_merged_" + mergeCount;
        String mergedDocInfoPath = docPath + "_merged_" + mergeCount;
        String mergedTermsPath = termPath + "_merged_" + mergeCount;
        mergeCount++;
        try (
            RandomAccessFile dictMerged = new RandomAccessFile(mergedDictPath, "rw");
            RandomAccessFile dataMerged = new RandomAccessFile(mergedDataPath, "rw");
            RandomAccessFile dictA = new RandomAccessFile(indexA.dictionaryFilePath, "rw");
            RandomAccessFile dictB = new RandomAccessFile(indexB.dictionaryFilePath, "rw");
            RandomAccessFile dataA = new RandomAccessFile(indexA.dataFilePath, "rw");
            RandomAccessFile dataB = new RandomAccessFile(indexB.dataFilePath, "rw");) {
       
            dictMerged.setLength(0);
            dataMerged.setLength(0);
            long freeMerge = 0;
    
            // iterate over terms
            File termFileA = new File(indexA.termFilePath);
            File termFileB = new File(indexB.termFilePath);
            try (BufferedReader termReaderA = new BufferedReader(new InputStreamReader(new FileInputStream(termFileA), StandardCharsets.UTF_8));
                 BufferedReader termReaderB = new BufferedReader(new InputStreamReader(new FileInputStream(termFileB), StandardCharsets.UTF_8));
                 BufferedWriter termWriterMerged = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mergedTermsPath), StandardCharsets.UTF_8))) {
                    
                String termA = termReaderA.readLine();
                String termB = termReaderB.readLine();
                while (termA != null || termB != null) {
                    if (termA != null && termB != null) {
                        int cmp = termA.compareTo(termB);
                        
                        if (cmp == 0) { // Terms are identical
                            PostingsList postingsListA = getPostingsListFromFile(dictA, dataA, termA);
                            PostingsList postingsListB = getPostingsListFromFile(dictB, dataB, termB);                                            
                            // System.err.println("Before merge Term A: '" + termA + "' => " + postingsListA.toString());
                            // System.err.println("Before merge Term B: '" + termB + "' => " + postingsListB.toString());
                            PostingsList mergedPostingsList = PostingsList.merge(postingsListA, postingsListB);
                            // System.err.println("after merge Term: => " + mergedPostingsList.toString());
                            String mergedPostingsString = mergedPostingsList.toString();
                            
                            freeMerge = writeIndexEntry(dictMerged, dataMerged, termA, mergedPostingsString, freeMerge);
                            
                            PostingsList fetched = getPostingsListFromFile(dictMerged, dataMerged, termA);
                            if (mergedPostingsString.compareTo(fetched.toString()) != 0) {
                                System.err.println("before insert " + mergedPostingsString);
                                System.err.println("fecthed " + fetched.toString());
                            }
                            
                            termWriterMerged.write(termA);
                            termWriterMerged.newLine();
                            
                            termA = termReaderA.readLine();
                            termB = termReaderB.readLine();
    
                        } else if (cmp < 0) { // termA is alphabetically before termB: unique to indexA.
                            PostingsList postingsListA = getPostingsListFromFile(dictA, dataA, termA);
                            String postingsStringA = postingsListA.toString();
                            // System.err.println("Term A before B: '" + termA + ">"+ termB + "' => " + postingsListA.toString());
                            freeMerge = writeIndexEntry(dictMerged, dataMerged, termA, postingsStringA, freeMerge);
                            termWriterMerged.write(termA);
                            termWriterMerged.newLine();
                            termA = termReaderA.readLine();
                        } else { // cmp > 0, so termB is alphabetically before termA.
                            PostingsList postingsListB = getPostingsListFromFile(dictB, dataB, termB); 
                            String postingsStringB = postingsListB.toString();
                            // System.err.println("Term B before A: '" + termB + ">"+ termA + "' => " + postingsListB.toString());
                            freeMerge = writeIndexEntry(dictMerged, dataMerged, termB, postingsStringB, freeMerge);
                            termWriterMerged.write(termB);
                            termWriterMerged.newLine();
                            termB = termReaderB.readLine();
                        }
                    } else if (termA != null) {// indexB is finished
                        PostingsList postingsListA = getPostingsListFromFile(dictA, dataA, termA);
                        String postingsStringA = postingsListA.toString();
                        // System.err.println("Term A only left: '" + termA + "' => " + postingsListA.toString());
                        freeMerge = writeIndexEntry(dictMerged, dataMerged, termA, postingsStringA, freeMerge);
                        termWriterMerged.write(termA);
                        termWriterMerged.newLine();
                        termA = termReaderA.readLine();
                    } else if (termB != null) { // indexA is finished
                        PostingsList postingsListB = getPostingsListFromFile(dictB, dataB, termB); 
                        String postingsStringB = postingsListB.toString();
                        // System.err.println("Term A only left: '" + termB + "' => " + postingsListB.toString());
                        freeMerge = writeIndexEntry(dictMerged, dataMerged, termB, postingsStringB, freeMerge);
                        termWriterMerged.write(termB);
                        termWriterMerged.newLine();
                        termB = termReaderB.readLine();
                    }
                }
            }
       
            mergeDocInfoFiles(indexA.docInfoFilePath, indexB.docInfoFilePath, mergedDocInfoPath);
    
            IndexFilePaths indexMerged = new IndexFilePaths(mergedDictPath, mergedDataPath, mergedDocInfoPath, mergedTermsPath);
            allIndexFilePaths.add(indexMerged);
        } 
    }

        
    /**
     * Merge two docInfo files. If one of the files does not exist, it is simply skipped.
     * not sure if order matters
     */
    private void mergeDocInfoFiles(String docInfoA, String docInfoB, String mergedDocInfo) throws IOException {
        Set<String> seenDocIDs = new HashSet<>();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mergedDocInfo), StandardCharsets.UTF_8))) {
            appendDocInfoFile(docInfoA, writer, seenDocIDs);
            appendDocInfoFile(docInfoB, writer, seenDocIDs);
        }
    }
    
    /**
     * Append the contents of a docInfo file to the writer.
     * If the file does not exist, nothing is appended.
     */
    private void appendDocInfoFile(String docInfoFile, BufferedWriter writer, Set<String> seenDocIDs) throws IOException {
        File file = new File(docInfoFile);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String docID = line.split(";")[0];
                if (seenDocIDs.add(docID)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }
    }    
    
    @Override
    public void cleanup() {
        System.err.println("Starting cleanup process...");
        
        if (!index.isEmpty()) {
            System.err.println("Writing final in-memory index to disk...");
            writePartialIndex();
        }
        
        // Wait for merge thread
        if (mergeThread != null) {
            System.err.println("Waiting for background merge to complete...");
            try {
                mergeThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting for merge thread");
            }
        }
        
        // Final merge
        if (allIndexFilePaths.size() > 1) {
            System.err.println("Performing final merge of remaining indexes...");
            mergeIntermediateIndexes();
        }

        if (allIndexFilePaths.size() == 1) {
            System.err.println("Finalizing index files...");
            IndexFilePaths finalIndex = allIndexFilePaths.poll();
            File dictionaryFile = new File(finalIndex.dictionaryFilePath);
            File dataFile = new File(finalIndex.dataFilePath);
            File docInfoFile = new File(finalIndex.docInfoFilePath);
            File termFile = new File(finalIndex.termFilePath);
        
            try {
                Files.copy(dictionaryFile.toPath(), Paths.get(dictPath), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(dataFile.toPath(), Paths.get(dataPath), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(docInfoFile.toPath(), Paths.get(docPath), StandardCopyOption.REPLACE_EXISTING);
                readDocInfo();
            } catch (IOException e) {
                System.err.println("Failed to copy files: " + e.getMessage());
            }
            
            if (!termFile.delete()) {
                System.err.println("Failed to delete " + finalIndex.termFilePath);
            }
            finalIndex.delete();
            
            System.err.println("Cleanup completed successfully.");
        }
        
    }
}