package org.opencb.opencga.storage.hadoop.variant.index.sample;

import com.google.common.collect.Lists;
import org.apache.hadoop.hbase.io.compress.Compression;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.opencb.biodata.models.metadata.SampleSetType;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.core.results.VariantQueryResult;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.metadata.models.SampleMetadata;
import org.opencb.opencga.storage.core.metadata.models.StudyMetadata;
import org.opencb.opencga.storage.core.variant.VariantStorageBaseTest;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.adaptors.VariantField;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils;
import org.opencb.opencga.storage.core.variant.annotation.VariantAnnotationManager;
import org.opencb.opencga.storage.core.variant.annotation.annotators.CellBaseRestVariantAnnotator;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.HadoopVariantStorageEngine;
import org.opencb.opencga.storage.hadoop.variant.HadoopVariantStorageTest;
import org.opencb.opencga.storage.hadoop.variant.VariantHbaseTestUtils;
import org.opencb.opencga.storage.hadoop.variant.adaptors.VariantHadoopDBAdaptor;
import org.opencb.opencga.storage.hadoop.variant.index.IndexUtils;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;
import static org.junit.Assert.*;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantMatchers.*;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam.*;

/**
 * Created on 12/04/19.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class SampleIndexTest extends VariantStorageBaseTest implements HadoopVariantStorageTest {

    @ClassRule
    public static ExternalResource externalResource = new HadoopExternalResource();

    private VariantHadoopDBAdaptor dbAdaptor;
    private static boolean loaded = false;
    protected static final List<String> sampleNames = Arrays.asList("NA19600", "NA19660", "NA19661", "NA19685");
    private static StudyMetadata studyMetadata;

    @Before
    public void before() throws Exception {
        dbAdaptor = getVariantStorageEngine().getDBAdaptor();
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    public void load() throws Exception {
        clearDB(DB_NAME);

        ObjectMap params = new ObjectMap(VariantStorageEngine.Options.STUDY_TYPE.key(), SampleSetType.FAMILY)
                .append(VariantStorageEngine.Options.STUDY.key(), STUDY_NAME)
                .append(VariantStorageEngine.Options.ANNOTATE.key(), true)
                .append(VariantStorageEngine.Options.EXTRA_GENOTYPE_FIELDS.key(), "DS,GL")
                .append(VariantAnnotationManager.VARIANT_ANNOTATOR_CLASSNAME, CellBaseRestVariantAnnotator.class.getName())
                .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), false);

        runETL(getVariantStorageEngine(), smallInputUri, outputUri, params, true, true, true);

        VariantHbaseTestUtils.printVariants(dbAdaptor, newOutputUri());

        studyMetadata = variantStorageEngine.getMetadataManager().getStudyMetadata(STUDY_NAME);
    }

    @Test
    public void checkLoadedData() throws Exception {
        HadoopVariantStorageEngine variantStorageEngine = getVariantStorageEngine();
        Iterator<SampleMetadata> it = variantStorageEngine.getMetadataManager().sampleMetadataIterator(1);
        while (it.hasNext()) {
            SampleMetadata sample = it.next();
            Iterator<SampleIndexEntry> indexIt = variantStorageEngine.getSampleIndexDBAdaptor().rawIterator(1, sample.getId());
            while (indexIt.hasNext()) {
                SampleIndexEntry record = indexIt.next();

                List<Variant> variants = variantStorageEngine.getDBAdaptor().get(new Query()
                        .append(VariantQueryParam.SAMPLE.key(), sample.getName())
                        .append(VariantQueryParam.REGION.key(),
                                record.getChromosome() + ":" + record.getBatchStart() + "-" + (record.getBatchStart() + SampleIndexSchema.BATCH_SIZE - 1)), null).getResult();

                Map<String, List<String>> gtsMap = variants.stream().collect(groupingBy(v -> v.getStudies().get(0).getSampleData(0).get(0), mapping(Variant::toString, toList())));
//                System.out.println("record = " + record);

                Assert.assertEquals(gtsMap.keySet(), record.getGts().keySet());
                for (Map.Entry<String, SampleIndexEntry.SampleIndexGtEntry> entry : record.getGts().entrySet()) {
                    String gt = entry.getKey();
                    List<String> expectedVariants = gtsMap.get(gt);
                    List<String> actualVariants;
                    if (entry.getValue().getVariants() == null) {
                        actualVariants = Collections.emptyList();
                    } else {
                        actualVariants = Lists.newArrayList(entry.getValue().getVariants())
                                .stream()
                                .map(Variant::toString)
                                .collect(toList());
                    }
                    Assert.assertEquals(gt, expectedVariants, actualVariants);
                }

            }
        }

    }

    @Test
    public void regenerateSampleIndex() throws Exception {

        String copy = dbAdaptor.getTableNameGenerator().getSampleIndexTableName(1) + "_copy";

        dbAdaptor.getHBaseManager().createTableIfNeeded(copy, Bytes.toBytes(GenomeHelper.DEFAULT_COLUMN_FAMILY),
                Compression.Algorithm.NONE);

        ObjectMap options = new ObjectMap()
                .append(SampleIndexDriver.OUTPUT, copy)
                .append(SampleIndexDriver.SAMPLES, "all");
        new TestMRExecutor().run(SampleIndexDriver.class, SampleIndexDriver.buildArgs(
                dbAdaptor.getArchiveTableName(1),
                dbAdaptor.getVariantTable(),
                1,
                Collections.emptySet(), options), options);

        VariantHbaseTestUtils.printSampleIndexTable(dbAdaptor, Paths.get(newOutputUri()), copy);

    }


    public VariantQueryResult<Variant> dbAdaptorQuery(Query query, QueryOptions options) {
        query = variantStorageEngine.preProcessQuery(query, options);
        return dbAdaptor.get(query, options);
    }


    @Test
    public void testQueryFileIndex() throws Exception {
        testQueryFileIndex(new Query(TYPE.key(), "SNV"));
        testQueryFileIndex(new Query(TYPE.key(), "INDEL"));
    }

    @Test
    public void testQueryAnnotationIndex() throws Exception {
        testQueryAnnotationIndex(new Query(ANNOT_BIOTYPE.key(), "protein_coding"));
        testQueryAnnotationIndex(new Query(ANNOT_CONSEQUENCE_TYPE.key(), "missense_variant"));
        testQueryAnnotationIndex(new Query(ANNOT_CONSEQUENCE_TYPE.key(), "stop_lost"));
        testQueryAnnotationIndex(new Query(ANNOT_CONSEQUENCE_TYPE.key(), "stop_lost").append(ANNOT_TRANSCRIPT_FLAG.key(), "basic"));
        testQueryAnnotationIndex(new Query(ANNOT_CONSEQUENCE_TYPE.key(), "missense_variant,stop_lost").append(ANNOT_TRANSCRIPT_FLAG.key(), "basic"));
        testQueryAnnotationIndex(new Query(ANNOT_CONSEQUENCE_TYPE.key(), "stop_gained"));
        testQueryAnnotationIndex(new Query(ANNOT_PROTEIN_SUBSTITUTION.key(), "sift=tolerated"));
        testQueryAnnotationIndex(new Query(ANNOT_POPULATION_ALTERNATE_FREQUENCY.key(), "1kG_phase3:ALL<0.001"));

//        testQueryAnnotationIndex(new Query(ANNOT_CONSEQUENCE_TYPE.key(), "intergenic_variant"));
        testQueryAnnotationIndex(new Query(ANNOT_CONSEQUENCE_TYPE.key(), "missense_variant,stop_gained"));
        testQueryAnnotationIndex(new Query(ANNOT_CONSEQUENCE_TYPE.key(), "missense_variant,stop_gained,mature_miRNA_variant"));
    }

    public void testQueryAnnotationIndex(Query annotationQuery) throws Exception {
        assertFalse(testQueryIndex(annotationQuery).emptyAnnotationIndex());
    }

    public void testQueryFileIndex(Query annotationQuery) throws Exception {
        testQueryIndex(annotationQuery);
    }

    public SampleIndexQuery testQueryIndex(Query annotationQuery) throws Exception {
        return testQueryIndex(annotationQuery, new Query()
                .append(STUDY.key(), STUDY_NAME)
                .append(SAMPLE.key(), "NA19600"));
    }

    public SampleIndexQuery testQueryIndex(Query annotationQuery, Query query) throws Exception {
//        System.out.println("----------------------------------------------------------");
//        queryResult = query(query, new QueryOptions());
//        int numResultsSample = queryResult.getNumResults();
//        System.out.println("Sample query: " + numResultsSample);

        // Query DBAdaptor
        System.out.println("Query DBAdaptor");
        query.putAll(annotationQuery);
        VariantQueryResult<Variant> queryResult = dbAdaptorQuery(new Query(query), new QueryOptions());
        int onlyDBAdaptor = queryResult.getNumResults();

        // Query SampleIndex
        System.out.println("Query SampleIndex");
        SampleIndexQuery indexQuery = SampleIndexQueryParser.parseSampleIndexQuery(new Query(query), variantStorageEngine.getMetadataManager());
//        int onlyIndex = (int) ((HadoopVariantStorageEngine) variantStorageEngine).getSampleIndexDBAdaptor()
//                .count(indexQuery, "NA19600");
        SampleIndexDBAdaptor sampleIndexDBAdaptor = ((HadoopVariantStorageEngine) variantStorageEngine).getSampleIndexDBAdaptor();
        int onlyIndex = sampleIndexDBAdaptor.iterator(indexQuery).toQueryResult().getResult().size();

        // Query SampleIndex+DBAdaptor
        System.out.println("Query SampleIndex+DBAdaptor");
        queryResult = variantStorageEngine.get(query, new QueryOptions());
        int indexAndDBAdaptor = queryResult.getNumResults();
        System.out.println("queryResult.source = " + queryResult.getSource());

        System.out.println("----------------------------------------------------------");
        System.out.println("query = " + annotationQuery.toJson());
        System.out.println("annotationIndex    = " + IndexUtils.maskToString(indexQuery.getAnnotationIndexMask(), indexQuery.getAnnotationIndex()));
        System.out.println("biotypeMask        = " + IndexUtils.byteToString(indexQuery.getAnnotationIndexQuery().getBiotypeMask()));
        System.out.println("ctMask             = " + IndexUtils.shortToString(indexQuery.getAnnotationIndexQuery().getConsequenceTypeMask()));
        for (String sample : indexQuery.getSamplesMap().keySet()) {
            System.out.println("fileIndex("+sample+") = " + IndexUtils.maskToString(indexQuery.getFileIndexMask(sample), indexQuery.getFileIndex(sample)));
        }
        System.out.println("Query ONLY_INDEX = " + onlyIndex         + "\t = SampleIndex");
        System.out.println("Query NO_INDEX   = " + onlyDBAdaptor     + "\t = DBAdaptor");
        System.out.println("Query INDEX      = " + indexAndDBAdaptor + "\t = SampleIndex+DBAdaptor");

        if (onlyDBAdaptor != indexAndDBAdaptor) {
            queryResult = variantStorageEngine.get(query, new QueryOptions());
            List<String> indexAndDB = queryResult.getResult().stream().map(Variant::toString).sorted().collect(Collectors.toList());
            queryResult = dbAdaptorQuery(query, new QueryOptions());
            List<String> noIndex = queryResult.getResult().stream().map(Variant::toString).sorted().collect(Collectors.toList());

            for (String s : indexAndDB) {
                if (!noIndex.contains(s)) {
                    System.out.println("From IndexAndDB, not in NoIndex = " + s);
                }
            }

            for (String s : noIndex) {
                if (!indexAndDB.contains(s)) {
                    System.out.println("From NoIndex, not in IndexAndDB = " + s);
                }
            }
        }
        assertEquals(onlyDBAdaptor, indexAndDBAdaptor);
        assertThat(queryResult, numResults(lte(onlyIndex)));
        assertThat(queryResult, numResults(gt(0)));
        return indexQuery;
    }

    @Test
    public void testSampleIndexSkipIntersect() throws StorageEngineException {
        Query query = new Query(VariantQueryParam.SAMPLE.key(), sampleNames.get(0)).append(VariantQueryParam.STUDY.key(), studyMetadata.getName());
        VariantQueryResult<Variant> result = variantStorageEngine.get(query, new QueryOptions(QueryOptions.INCLUDE, VariantField.ID).append(QueryOptions.LIMIT, 1));
        assertEquals("sample_index_table", result.getSource());

        query.append(VariantQueryParam.ANNOT_CONSEQUENCE_TYPE.key(), String.join(",", new ArrayList<>(VariantQueryUtils.LOF_SET)));
        result = variantStorageEngine.get(query, new QueryOptions(QueryOptions.INCLUDE, VariantField.ID).append(QueryOptions.LIMIT, 1));
        assertEquals("sample_index_table", result.getSource());

        query.append(VariantQueryParam.ANNOT_CONSEQUENCE_TYPE.key(), String.join(",", new ArrayList<>(VariantQueryUtils.LOF_EXTENDED_SET))).append(TYPE.key(), VariantType.INDEL);
        result = variantStorageEngine.get(query, new QueryOptions(QueryOptions.INCLUDE, VariantField.ID).append(QueryOptions.LIMIT, 1));
        assertEquals("sample_index_table", result.getSource());

        query.append(VariantQueryParam.ANNOT_CONSEQUENCE_TYPE.key(), new ArrayList<>(VariantQueryUtils.LOF_EXTENDED_SET)
                .subList(2, 4)
                .stream()
                .collect(Collectors.joining(",")));
        result = variantStorageEngine.get(query, new QueryOptions(QueryOptions.INCLUDE, VariantField.ID).append(QueryOptions.LIMIT, 1));
        assertNotEquals("sample_index_table", result.getSource());
    }

}