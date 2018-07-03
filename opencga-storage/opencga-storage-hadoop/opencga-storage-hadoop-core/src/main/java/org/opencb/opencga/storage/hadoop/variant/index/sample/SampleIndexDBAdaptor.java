package org.opencb.opencga.storage.hadoop.variant.index.sample;

import com.google.common.collect.Iterators;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.CollectionUtils;
import org.opencb.biodata.models.core.Region;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.metadata.StudyConfigurationManager;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBIterator;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryException;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.QueryOperation;
import org.opencb.opencga.storage.hadoop.utils.HBaseManager;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.HadoopVariantStorageEngine;
import org.opencb.opencga.storage.hadoop.variant.index.sample.iterators.*;
import org.opencb.opencga.storage.hadoop.variant.utils.HBaseVariantTableNameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created on 14/05/18.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class SampleIndexDBAdaptor {

    private final HBaseManager hBaseManager;
    private final HBaseVariantTableNameGenerator tableNameGenerator;
    private final StudyConfigurationManager scm;
    private final byte[] family;
    private static Logger logger = LoggerFactory.getLogger(SampleIndexDBAdaptor.class);

    public SampleIndexDBAdaptor(GenomeHelper helper, HBaseManager hBaseManager, HBaseVariantTableNameGenerator tableNameGenerator,
                                StudyConfigurationManager scm) {
        this.hBaseManager = hBaseManager;
        this.tableNameGenerator = tableNameGenerator;
        this.scm = scm;
        family = helper.getColumnFamily();
    }

    protected Integer getStudyId(String study) {
        Integer studyId;
        if (StringUtils.isEmpty(study)) {
            Map<String, Integer> studies = scm.getStudies(null);
            if (studies.size() == 1) {
                studyId = studies.values().iterator().next();
            } else {
                throw VariantQueryException.studyNotFound(study, studies.keySet());
            }
        } else {
            studyId = scm.getStudyId(study, null);
        }
        return studyId;
    }

    public SampleIndexVariantDBIterator iterator(List<Region> regions, String study, Map<String, List<String>> samples,
                                                 QueryOperation operation) {
        if (samples.size() == 1) {
            Map.Entry<String, List<String>> entry = samples.entrySet().iterator().next();
            return iterator(regions, study, entry.getKey(), entry.getValue());
        } else if (samples.isEmpty()) {
            throw new VariantQueryException("At least one sample expected to query SampleIndex!");
        }

        List<VariantDBIterator> iterators = new ArrayList<>(samples.size());

        for (Map.Entry<String, List<String>> entry : samples.entrySet()) {
            iterators.add(iterator(regions, study, entry.getKey(), entry.getValue()));
        }
        if (operation.equals(QueryOperation.OR)) {
            return new UnionMultiSampleIndexVariantDBIterator(iterators);
        } else {
            return new IntersectMultiSampleIndexVariantDBIterator(iterators);
        }

    }

    public SampleIndexVariantDBIterator iterator(List<Region> regions, String study, String sample, List<String> gts) {

        Integer studyId = getStudyId(study);

        String tableName = tableNameGenerator.getSampleIndexTableName(studyId);

        try {
            return hBaseManager.act(tableName, table -> {
                return new SingleSampleIndexVariantDBIterator(table, regions, studyId, sample, gts, this);
            });
        } catch (IOException e) {
            throw VariantQueryException.internalException(e);
        }
    }

    public Iterator<Map<String, List<Variant>>> rawIterator(int study, int sample) throws IOException {
        String tableName = tableNameGenerator.getSampleIndexTableName(study);

        return hBaseManager.act(tableName, table -> {

            Scan scan = new Scan();
            scan.setRowPrefixFilter(SampleIndexConverter.toRowKey(sample));
            SampleIndexConverter converter = new SampleIndexConverter();
            try {
                ResultScanner scanner = table.getScanner(scan);
                Iterator<Result> resultIterator = scanner.iterator();
                Iterator<Map<String, List<Variant>>> iterator = Iterators.transform(resultIterator, converter::convertToMap);
                return iterator;
            } catch (IOException e) {
                throw VariantQueryException.internalException(e);
            }
        });
    }

    public long count(List<Region> regions, String study, String sample, List<String> gts) {
        List<Region> regionsList;
        if (CollectionUtils.isEmpty(regions)) {
            // If no regions are defined, get a list of one null element to initialize the stream.
            regionsList = Collections.singletonList(null);
        } else {
            regionsList = VariantQueryUtils.mergeRegions(regions);
        }

        Integer studyId = getStudyId(study);
        if (CollectionUtils.isEmpty(gts)) {
            StudyConfiguration sc = scm.getStudyConfiguration(studyId,
                    new QueryOptions(StudyConfigurationManager.READ_ONLY, true).append(StudyConfigurationManager.CACHED, true)).first();
            gts = sc.getAttributes().getAsStringList(HadoopVariantStorageEngine.LOADED_GENOTYPES);
        }
        String tableName = tableNameGenerator.getSampleIndexTableName(studyId);

        List<String> finalGts = gts;
        try {
            return hBaseManager.act(tableName, table -> {
                long count = 0;
                for (Region region : regionsList) {
                    // Split region in countable regions
                    List<Region> subRegions = region == null ? Collections.singletonList((Region) null) : splitRegion(region);
                    for (Region subRegion : subRegions) {
                        if (subRegion == null || startsAtBatch(subRegion) && endsAtBatch(subRegion)) {
                            SampleIndexConverter converter = new SampleIndexConverter(subRegion);
                            Scan scan = parse(subRegion, studyId, sample, finalGts, true);
                            try {
                                ResultScanner scanner = table.getScanner(scan);
                                Result result = scanner.next();
                                while (result != null) {
                                    count += converter.convertToCount(result);
                                    result = scanner.next();
                                }
                            } catch (IOException e) {
                                throw VariantQueryException.internalException(e);
                            }
                        } else {
                            SampleIndexConverter converter = new SampleIndexConverter(subRegion);
                            Scan scan = parse(subRegion, studyId, sample, finalGts, false);
                            try {
                                ResultScanner scanner = table.getScanner(scan);
                                Result result = scanner.next();
                                while (result != null) {
                                    count += converter.convert(result).size();
                                    result = scanner.next();
                                }
                            } catch (IOException e) {
                                throw VariantQueryException.internalException(e);
                            }
                        }
                    }
                }
                return count;
            });
        } catch (IOException e) {
            throw VariantQueryException.internalException(e);
        }
    }

    /**
     * Split region into regions that match with batches at SampleIndexTable.
     *
     * @param region Region to split
     * @return List of regions.
     */
    protected static List<Region> splitRegion(Region region) {
        List<Region> regions;
        if (region.getEnd() - region.getStart() < SampleIndexDBLoader.BATCH_SIZE) {
            // Less than one batch. Do not split region
            regions = Collections.singletonList(region);
        } else if (region.getStart() / SampleIndexDBLoader.BATCH_SIZE + 1 == region.getEnd() / SampleIndexDBLoader.BATCH_SIZE
                && !startsAtBatch(region)
                && !endsAtBatch(region)) {
            // Consecutive partial batches. Do not split region
            regions = Collections.singletonList(region);
        } else {
            regions = new ArrayList<>(3);
            if (!startsAtBatch(region)) {
                int splitPoint = region.getStart() - region.getStart() % SampleIndexDBLoader.BATCH_SIZE + SampleIndexDBLoader.BATCH_SIZE;
                regions.add(new Region(region.getChromosome(), region.getStart(), splitPoint - 1));
                region.setStart(splitPoint);
            }
            regions.add(region);
            if (!endsAtBatch(region)) {
                int splitPoint = region.getEnd() - region.getEnd() % SampleIndexDBLoader.BATCH_SIZE;
                regions.add(new Region(region.getChromosome(), splitPoint, region.getEnd()));
                region.setEnd(splitPoint - 1);
            }
        }
        return regions;
    }

    protected static boolean startsAtBatch(Region region) {
        return region.getStart() % SampleIndexDBLoader.BATCH_SIZE == 0;
    }

    protected static boolean endsAtBatch(Region region) {
        return region.getEnd() + 1 % SampleIndexDBLoader.BATCH_SIZE == 0;
    }

    public Scan parse(Region region, int study, String sample, List<String> gts, boolean count) {

        Scan scan = new Scan();
        int sampleId = toSampleId(study, sample);
        if (region != null) {
            scan.setStartRow(SampleIndexConverter.toRowKey(sampleId, region.getChromosome(), region.getStart()));
            scan.setStopRow(SampleIndexConverter.toRowKey(sampleId, region.getChromosome(),
                    region.getEnd() + (region.getEnd() == Integer.MAX_VALUE ? 0 : SampleIndexDBLoader.BATCH_SIZE)));
        } else {
            scan.setRowPrefixFilter(SampleIndexConverter.toRowKey(sampleId));
        }
        for (String gt : gts) {
            if (count) {
                scan.addColumn(family, SampleIndexConverter.toGenotypeCountColumn(gt));
            } else {
                scan.addColumn(family, SampleIndexConverter.toGenotypeColumn(gt));
            }
        }


        logger.info("StartRow = " + Bytes.toStringBinary(scan.getStartRow()) + " == "
                + SampleIndexConverter.rowKeyToString(scan.getStartRow()));
        logger.info("StopRow = " + Bytes.toStringBinary(scan.getStopRow()) + " == "
                + SampleIndexConverter.rowKeyToString(scan.getStopRow()));
        logger.info("columns = " + scan.getFamilyMap().getOrDefault(family, Collections.emptyNavigableSet())
                .stream().map(Bytes::toString).collect(Collectors.joining(",")));
        logger.info("MaxResultSize = " + scan.getMaxResultSize());
        logger.info("Filters = " + scan.getFilter());
        logger.info("Batch = " + scan.getBatch());
        logger.info("Caching = " + scan.getCaching());

//        try {
//            System.out.println("scan = " + scan.toJSON() + " " + rowKeyToString(scan.getStartRow()) + " -> + "
// + rowKeyToString(scan.getStopRow()));
//        } catch (IOException e) {
//            throw VariantQueryException.internalException(e);
//        }

        return scan;
    }


    private int toSampleId(int studyId, String sample) {
        StudyConfiguration sc = scm.getStudyConfiguration(studyId, new QueryOptions(StudyConfigurationManager.READ_ONLY, true)
                .append(StudyConfigurationManager.CACHED, true)).first();
        if (sc == null) {
            throw VariantQueryException.studyNotFound(studyId, scm.getStudies(null).keySet());
        }
        return scm.getSampleId(sample, sc);
    }


}
