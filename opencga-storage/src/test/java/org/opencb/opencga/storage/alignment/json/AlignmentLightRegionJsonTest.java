package org.opencb.opencga.storage.alignment.json;


import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import org.junit.Test;
import org.opencb.biodata.formats.alignment.AlignmentHelper;
import org.opencb.biodata.formats.alignment.io.AlignmentRegionDataReader;
import org.opencb.biodata.formats.alignment.sam.io.AlignmentSamDataReader;
import org.opencb.biodata.models.alignment.Alignment;
import org.opencb.biodata.models.alignment.AlignmentRegion;
import org.opencb.biodata.models.alignment.exceptions.ShortReferenceSequenceException;
import org.opencb.biodata.models.feature.Region;
import org.opencb.commons.containers.map.QueryOptions;
import org.opencb.commons.run.Task;

public class AlignmentLightRegionJsonTest {

    public class AlignmentRegionCompactorTask extends Task<AlignmentRegion> {

        private final QueryOptions queryOptions;

        public AlignmentRegionCompactorTask() {
            this.queryOptions = new QueryOptions();
        }

        public AlignmentRegionCompactorTask(QueryOptions queryOptions) {
            this.queryOptions = queryOptions;
        }

        @Override
        public boolean apply(List<AlignmentRegion> batch) throws IOException {

            for (AlignmentRegion alignmentRegion : batch) {
                Region region = alignmentRegion.getRegion();
                long start = region.getStart();
                if (start <= 0) {
                    start = 1;
                    region.setStart(1);
                }
                System.out.println("Asking for sequence: " + region.toString() + " size = " + (region.getEnd() - region.getStart()));
                String sequence = AlignmentHelper.getSequence(region, queryOptions);
                for (Alignment alignment : alignmentRegion.getAlignments()) {
                    try {
                        AlignmentHelper.completeDifferencesFromReference(alignment, sequence, start);
                    } catch (ShortReferenceSequenceException e) {
                        System.out.println("[ERROR] NOT ENOUGH REFERENCE SEQUENCE!!");
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            }
            return true;
        }
    }
    
    
    @Test
    public void createtest() throws IOException{
//        AlignmentSamDataReader reader = new AlignmentSamDataReader("/tmp/small.bam", "study");
        AlignmentSamDataReader reader = new AlignmentSamDataReader("/tmp/NA12154.chrom20.ILLUMINA.bwa.CEU.low_coverage.20120522.bam", "study");
        AlignmentRegionDataReader regionreader = new AlignmentRegionDataReader(reader);
        regionreader.setMaxSequenceSize(2000);
        AlignmentRegionCompactorTask compactor = new AlignmentRegionCompactorTask();
        regionreader.open();
        regionreader.pre();
        compactor.pre();
        
        
        List<AlignmentRegion> regions = regionreader.read(50);
        compactor.apply(regions);
        for(AlignmentRegion r : regions){
            List<Alignment> reads = r.getAlignments();
            int chunkStart = (int) r.getStart();
            AlignmentLightRegionJson.Builder builder = new AlignmentLightRegionJson.Builder("20", chunkStart, "2K");
            builder.addAlignmentsFile(reads, "small.bam");
            AlignmentLightRegionJson alignmentLightRegionJson = builder.build();
        }
        
        compactor.post();
        regionreader.post();
        regionreader.close();
    }
}