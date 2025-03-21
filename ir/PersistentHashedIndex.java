/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, KTH, 2018
 */  

package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.*;


/*
 *   Implements an inverted index as a hashtable on disk.
 *   
 *   Both the words (the dictionary) and the data (the postings list) are
 *   stored in RandomAccessFiles that permit fast (almost constant-time)
 *   disk seeks. 
 *
 *   When words are read and indexed, they are first put in an ordinary,
 *   main-memory HashMap. When all words are read, the index is committed
 *   to disk.
 */
public class PersistentHashedIndex implements Index {

    /** The directory where the persistent index files are stored. */
    // public static final String INDEXDIR = "./index";
    public static final String INDEXDIR = "./indexDavis";
        
    /** The dictionary file name */
    public static final String DICTIONARY_FNAME = "dictionary";

    /** The data file name */
    public static final String DATA_FNAME = "data";

    /** The terms file name */
    public static final String TERMS_FNAME = "terms";

    /** The doc info file name */
    public static final String DOCINFO_FNAME = "docInfo";

    /** The dictionary hash table on disk can fit this many entries. */
    // public static final long TABLESIZE = 611953L;
    public static final long TABLESIZE = 3500017L;
    

    /** Maximum number of bytes for storing the term in each dictionary slot. */
    public static final int MAX_TERM_BYTES = 100;

    /**
     * The size in bytes of each "slot" in the dictionary file.
     *   [valid:1 byte]
     *   [termLength:2 bytes]
     *   [termData: up to 40 bytes]
     *   [dataPtr:8 bytes]
     *   [dataSize:4 bytes]
     * = 1 + 2 + 100 + 8 + 4 = 115 bytes
     */
    public static final int SLOT_SIZE = 115;

    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile;

    /** Pointer to the first free memory cell in the data file. */
    long free = 0L;

    TreeSet<String> termsSet = new TreeSet<>();

    /** The cache as a main-memory hash map. */
    HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();

    
    // ===================================================================


    /**
     *   A helper class representing one entry in the dictionary hashtable.
     */ 
    public class Entry {
        String term;
        long dataPtr; 
        int dataSize;

        public Entry(String term, long dataPtr, int dataSize) {
            this.term = term;
            this.dataPtr = dataPtr;
            this.dataSize = dataSize;
        }
    }


    // ==================================================================

    
    /**
     *  Constructor. Opens the dictionary file and the data file.
     *  If these files don't exist, they will be created. 
     */
    public PersistentHashedIndex() {
        try {
            dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        try {
            readDocInfo();
        } catch ( FileNotFoundException e ) {
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    /**
     *  Writes data to the data file at a specified place.
     *
     *  @return The number of bytes written.
     */ 
    int writeData(RandomAccessFile dataFile, String dataString, long ptr ) {
        try {
            dataFile.seek( ptr ); 
            byte[] data = dataString.getBytes();
            dataFile.write( data );
            return data.length;
        } catch ( IOException e ) {
            e.printStackTrace();
            return -1;
        }
    }


    /**
     *  Reads data from the data file
     */ 
    String readData(RandomAccessFile dataFile, long ptr, int size ) {
        try {
            dataFile.seek( ptr );
            byte[] data = new byte[size];
            dataFile.readFully( data );
            return new String(data);
        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }
 

    // ==================================================================
    //
    //  Reading and writing to the dictionary file.

    /*
     *  Writes an entry to the dictionary hash table file. 
     *
     *  @param entry The key of this entry is assumed to have a fixed length
     *  @param ptr   The place in the dictionary file to store the entry
     */
    void writeEntry(RandomAccessFile dictionaryFile, Entry entry, long slot) {
        try {
            long byteOffset = slot * SLOT_SIZE;
            dictionaryFile.seek(byteOffset);
            
            // 1) Mark the slot as valid
            dictionaryFile.writeByte(1);  // "valid = 1"

            // 2) Write the length of the term (2 bytes)
            byte[] termBytes = entry.term.getBytes(StandardCharsets.UTF_8);
            if (termBytes.length > MAX_TERM_BYTES) {

                byte[] truncated = new byte[MAX_TERM_BYTES];
                System.arraycopy(termBytes, 0, truncated, 0, MAX_TERM_BYTES);
                termBytes = truncated;
            }
            dictionaryFile.writeShort(termBytes.length);

            // 3) Write exactly MAX_TERM_BYTES for the term
            dictionaryFile.write(termBytes);
            // If it's shorter than MAX_TERM_BYTES, pad with zeros.
            if (termBytes.length < MAX_TERM_BYTES) {
                dictionaryFile.write(new byte[MAX_TERM_BYTES - termBytes.length]);
            }

            // 4) Write dataPtr (8 bytes)
            dictionaryFile.writeLong(entry.dataPtr);

            // 5) Write dataSize (4 bytes)
            dictionaryFile.writeInt(entry.dataSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *  Reads an entry from the dictionary file.
     *
     *  @param ptr The place in the dictionary file where to start reading.
     */
    Entry readEntry(RandomAccessFile dictionaryFile, long slot) {
        try {
            long byteOffset = slot * SLOT_SIZE;
            // If we are beyond EOF, obviously no entry
            if (byteOffset >= dictionaryFile.length()) {
                return null;
            }
            dictionaryFile.seek(byteOffset);

            // (1) valid byte
            byte valid = dictionaryFile.readByte();
            if (valid != 1) {
                // Not a valid slot
                return null;
            }

            // (2) termLength
            int termLength = dictionaryFile.readShort();
            if (termLength < 0 || termLength > MAX_TERM_BYTES) {
                return null; // corrupted or invalid
            }

            // (3) read exactly MAX_TERM_BYTES, then substring
            byte[] termBuf = new byte[MAX_TERM_BYTES];
            dictionaryFile.read(termBuf);
            String term = new String(termBuf, 0, termLength, StandardCharsets.UTF_8);

            // (4) dataPtr
            long dataPtr = dictionaryFile.readLong();

            // (5) dataSize
            int dataSize = dictionaryFile.readInt();

            return new Entry(term, dataPtr, dataSize);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1); 
        }
        return null;
    }
    
    // ==================================================================

    public long hash(String term) {
        return (term.hashCode() & 0x7fffffff) % TABLESIZE;
    }

    // ==================================================================

    /**
     *  Writes the document names and document lengths to file.
     *
     * @throws IOException  { exception_description }
     */
    public void writeDocInfo() throws IOException {
        FileOutputStream fout = new FileOutputStream( INDEXDIR + "/docInfo" );
        for ( Map.Entry<Integer,String> entry : docNames.entrySet() ) {
            Integer key = entry.getKey();
            String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
            fout.write( docInfoEntry.getBytes() );
        }
        fout.close();
    }


    /**
     *  Reads the document names and document lengths from file, and
     *  put them in the appropriate data structures.
     *
     * @throws     IOException  { exception_description }
     */
    public void readDocInfo() throws IOException {
        File file = new File( INDEXDIR + "/docInfo" );
        FileReader freader = new FileReader(file);
        try ( BufferedReader br = new BufferedReader(freader) ) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(";");
                docNames.put( new Integer(data[0]), data[1] );
                docLengths.put( new Integer(data[0]), new Integer(data[2]) );
            }
        }
        freader.close();
    }


    public void writeTerms(TreeSet<String> termsSet) throws IOException {
        FileOutputStream fout = new FileOutputStream(INDEXDIR + "/" + TERMS_FNAME);
        for (String term : termsSet) {
            fout.write((term + "\n").getBytes());
        }
        fout.close();
        termsSet.clear();
    }

/**
 * Writes a single index entry (term and its postings) into the given dictionary
 * and data files. Returns the new free pointer after writing.
 */
public long writeIndexEntry(RandomAccessFile dictFile,
    RandomAccessFile dataFile,
    String term,
    String postingsString,
    long freePtr) throws IOException {
    // Write postings list to the data file at the current free pointer.
    int dataSize = writeData(dataFile, postingsString, freePtr);

    // Create the dictionary entry.
    Entry newEntry = new Entry(term, freePtr, dataSize);

    // Determine the hash slot and perform linear probing.
    long slot = hash(term);
    while (true) {
        Entry existing = readEntry(dictFile, slot);
        if (existing == null) {
            // Found an empty slot.
            writeEntry(dictFile, newEntry, slot);
            break;
        } else if (existing.term.equals(term)) {
            writeEntry(dictFile, newEntry, slot);
            break;
        } else {
            // Collision: try the next slot.
            slot = (slot + 1) % TABLESIZE;
        }
    }

    // Return the updated free pointer.
    return freePtr + dataSize;
}
    

    /**
     *  Write the index to files.
     */
    public void writeIndex() {
        try {
            writeDocInfo();
            for (Map.Entry<String, PostingsList> mapEntry : index.entrySet()) {
                String originalTerm = mapEntry.getKey();
                PostingsList postingsList = mapEntry.getValue();
                String postingsString = postingsList.toString();
    
                byte[] termBytes = originalTerm.getBytes(StandardCharsets.UTF_8);
                String termToStore = originalTerm;
                if (termBytes.length > MAX_TERM_BYTES) {
                    termBytes = Arrays.copyOf(termBytes, MAX_TERM_BYTES);
                    termToStore = new String(termBytes, StandardCharsets.UTF_8);
                }
                termsSet.add(termToStore);
    
                free = writeIndexEntry(dictionaryFile, dataFile, termToStore, postingsString, free);
            }
            writeTerms(termsSet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // ==================================================================
    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings(String token) {
        return getPostingsListFromFile(dictionaryFile, dataFile, token);
        
    }

    public PostingsList getPostingsListFromFile(RandomAccessFile dictionaryFile, RandomAccessFile dataFile, String token) {
        // Process the token to match how it's stored (truncate if necessary)
        byte[] termBytes = token.getBytes(StandardCharsets.UTF_8);
        if (termBytes.length > MAX_TERM_BYTES) {
            termBytes = Arrays.copyOf(termBytes, MAX_TERM_BYTES);
            token = new String(termBytes, StandardCharsets.UTF_8);
        }
    
        long dictPtr = hash(token);
        while (true) {
            Entry entry = readEntry(dictionaryFile, dictPtr);
            if (entry == null) {
                return null; // Term not found
            }
            if (entry.term.equals(token)) {
                String postingsData = readData(dataFile, entry.dataPtr, entry.dataSize);
                return PostingsList.fromString(postingsData);
            }
            dictPtr = (dictPtr + 1) % TABLESIZE;
        }
    }


    /**
     *  Inserts this token in the main-memory hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        PostingsList list = index.computeIfAbsent(token, k -> new PostingsList());
        
        list.add(docID, offset);
    }


    /**
     *  Write index to file after indexing is done.
     */
    public void cleanup() {
        System.err.println( index.keySet().size() + " unique words" );
        System.err.print( "Writing index to disk..." );
        writeIndex();
        System.err.println( "done!" );        
    }

}



