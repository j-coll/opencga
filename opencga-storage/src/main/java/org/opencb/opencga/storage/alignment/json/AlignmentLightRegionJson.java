package org.opencb.opencga.storage.alignment.json;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.opencb.biodata.models.alignment.Alignment;

/**
 * Created by jacobo on 5/08/14.
 */
public class AlignmentLightRegionJson {

    static public class File{

        public File(List<Integer> reads, List<Integer> soft, String fileId) {
            this.reads = reads;
            this.soft = soft;
            this.fileId = fileId;
        }
        
        /**
         *      A, 12b    B, 8b    C, 12b
         * \------------|--------|--------\         11,10,11 --- 11,9,12
         *
         *  A: Start
         *  B: End-Start
         *  C: ????
         */
        private List<Integer> reads; //Location of each read
        private List<Integer> soft;  //SoftClippings
        private String fileId;
         /*
         * JSON Getters Setters
         */
        
        public String           getFileId() {
            return fileId;
        }
        public void             setFileId(String fileId) {
            this.fileId = fileId;
        }
        
        public List<Integer>    getReads() {
            return reads;
        }
        public void             setReads(List<Integer> reads) {
            this.reads = reads;
        }

        public List<Integer> getSoft() {
            return soft;
        }
        public void setSoft(List<Integer> soft) {
            this.soft = soft;
        }
        
    }

    static public class Difference{
        private static final Map<Character,Integer> diffMap;

        static{
            diffMap= new HashMap<>();
            diffMap.put(Alignment.AlignmentDifference.INSERTION, 0);
            diffMap.put(Alignment.AlignmentDifference.DELETION, 1);
            diffMap.put(Alignment.AlignmentDifference.MATCH_MISMATCH, 2);
            diffMap.put(Alignment.AlignmentDifference.MISMATCH, 3);
            diffMap.put(Alignment.AlignmentDifference.SOFT_CLIPPING, 4);
            diffMap.put(Alignment.AlignmentDifference.HARD_CLIPPING, 5);
            diffMap.put(Alignment.AlignmentDifference.PADDING, 6);
            diffMap.put(Alignment.AlignmentDifference.SKIPPED_REGION, 7);
        }

        /*
            pos, length, alter, operation...
         */
        public Difference(int pos, int length, char operation, String seq) {
            if(pos >> 12 != 0){
                System.out.println("[ERROR] Difference Pos " + pos);
            }
            if(length >> 8 != 0){
                System.out.println("[ERROR] Difference Length " + length);
            }
            this.diff = ((pos & 0xFFF) << 20) + ((length & 0xFF) << 12) + ((diffMap.get(operation) << 9));
            this.seq = seq;
        }

        /**
         * Diff bitwise structure
         *
         *      A, 12b    B, 8b  C,3b D,4b
         * \------------|--------|---|-----\
         *
         *  A: Start (chunk relative)
         *  B: Length
         *  C: Operation
         *  D: ???
         */

        private int diff;
        private String seq;
        /**
         * Reads bitwise structure
         *
         *      A, 10b    B, 22b
         * \----------|------------------\
         *
         *  A: FileID
         *  B: AlignmentID
         */
        private List<Integer> reads = new LinkedList<>();

        public void addRead(int fileIndex, int readIndex){
            if(fileIndex >> 10 != 0){
                System.out.println("[ERROR] File Index " + fileIndex);
            }
            if(readIndex >> 22 != 0){
                System.out.println("[ERROR] Read Index " + readIndex);
            }
            reads.add(( ( fileIndex & 0x3FF ) << 22) + readIndex&0x3FFFFF);
        }
        /*
         * JSON Getters Setters
         */
        public List<Integer>    getReads() {
            return reads;
        }
        public void             setReads(List<Integer> reads) {
            this.reads = reads;
        }

        public String           getSeq() {
            return seq;
        }
        public void             setSeq(String seq) {
            this.seq = seq;
        }

        public int              getDiff() {
            return diff;
        }
        public void             setDiff(int diff) {
            this.diff = diff;
        }
    }

    static public class Builder{
        final private Map<String, AlignmentLightRegionJson.Difference> mapDiff = new HashMap<>();

        final private List<AlignmentLightRegionJson.Difference> diffs = new LinkedList<>();
        final private List<AlignmentLightRegionJson.File> files = new LinkedList<>();
        final private int chunkStart;
        final private int chunkSize;
        final private String chunkSizeStr;
        final private String chromosome;
        public Builder(String chromosome, int chunkStart, String chunkSizeStr){
            this.chromosome = chromosome;
            this.chunkStart = chunkStart;
            this.chunkSizeStr = chunkSizeStr;


            if (chunkSizeStr.endsWith("K")) {
                chunkSizeStr = chunkSizeStr.substring(0,chunkSizeStr.length()-1);
                this.chunkSize = Integer.valueOf(chunkSizeStr)*1000;
            } else if (chunkSizeStr.endsWith("M")) {
                chunkSizeStr = chunkSizeStr.substring(0,chunkSizeStr.length()-1);
                this.chunkSize = Integer.valueOf(chunkSizeStr)*1000000;
            } else {
                this.chunkSize = Integer.valueOf(chunkSizeStr)  ;
            }

            //this.chunkSize = chunkSize;
        }

        public AlignmentLightRegionJson build(){
            AlignmentLightRegionJson alignmentLightRegionJson = new AlignmentLightRegionJson();
            alignmentLightRegionJson.diffs = diffs;
            alignmentLightRegionJson.files = files;
            alignmentLightRegionJson.chunk = chromosome + "_" + chunkStart/chunkSize + "_" + chunkSizeStr;
            System.out.println(
                    "[" + alignmentLightRegionJson.chunk + "] Diffs: " + diffs.size() +"\t" +
                    "Shared: " + diffRepe + "(" + diffRepe/(diffs.size()+diffRepe) + "%)\t" +
                    "Soft Clip: " + diffS 
            ); 
            return alignmentLightRegionJson;
        }

        
        int diffRepe = 0;
        int diffS = 0;
        public void addAlignmentsFile(List<Alignment> alignments, String fileId){
            int alignmentIndex = 0;
            List<Integer> reads = new LinkedList<>();
            List<Integer> softs = new LinkedList<>();
            int fileIndex = files.size();

            for(Alignment a : alignments){
                //Create AlignmentLight
                int s = (int) a.getStart() - chunkStart;
                int es = (int) (a.getEnd() - a.getStart());
                es = es<0? 0: es;
                if(s >> 12 != 0){
                    System.out.println("[ERROR] Start read " + s);
                }
                if(es >> 8 != 0){
                    System.out.println("[ERROR] End-Start " + es);
                }
                int read = ((s & 0xFFF) << 20) + ((es & 0xFF) << 8);
                reads.add(read);

                //Check differences
                if((a.getFlags() & Alignment.SEGMENT_UNMAPPED) == 0){
                    for(Alignment.AlignmentDifference d : a.getDifferences()){
                        if(d.getOp() == Alignment.AlignmentDifference.SOFT_CLIPPING){
                            diffS++;
                            if(alignmentIndex >> 12 != 0){
                                System.out.println("[ERROR] Soft AlignmentIndex");
                            }
                            if(d.getLength() >> 8 != 0){
                                System.out.println("[ERROR] Soft Length");
                            }
                            int soft = ((alignmentIndex & 0xFFF) << 20) + ((d.getLength() & 0xFF) << 12) + ((d.getPos()==0? 1: 0)<<11);
                            softs.add(soft);
                        } else {
                            String diffKey = d.getPos()+a.getUnclippedStart()+"_"+d.getOp()+"_"+ (d.getSeq()==null?d.getLength()+"":d.getSeq()); //Critical point
                            AlignmentLightRegionJson.Difference diff;
                            if ((diff = mapDiff.get(diffKey)) == null) {
                                //Create new difference if necessary
                                diff = new AlignmentLightRegionJson.Difference((int)(d.getPos()+a.getUnclippedStart()-chunkStart),d.getLength(), d.getOp(), d.getSeq());
                                mapDiff.put(diffKey, diff);
                                diffs.add(diff);

                            } else {
                                diffRepe++;
                            }
                            //Add Alignment to the difference reads list
                            diff.addRead(fileIndex, alignmentIndex);
                        }
                    }
                }
                alignmentIndex++;   //Increment the index for the next alignments
            }
            AlignmentLightRegionJson.File file = new AlignmentLightRegionJson.File(reads, softs, fileId);
            files.add(file);
        }
    }

    private String chunk;
    private List<Difference> diffs;
    private List<File> files;

    /*
     * JSON Getters Setters
     */

    public String           getChunk() {
        return chunk;
    }
    public void             setChunk(String chunk) {
        this.chunk = chunk;
    }

    public List<Difference> getDiffs() {
        return diffs;
    }
    public void             setDiffs(List<Difference> diffs) {
        this.diffs = diffs;
    }

    public List<File>       getFiles() {
        return files;
    }
    public void             setFiles(List<File> files) {
        this.files = files;
    }
}
