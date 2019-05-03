/*
 * Copyright 2015-2017 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.storage.hadoop.variant;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.io.compress.Compression.Algorithm;
import org.apache.hadoop.hbase.util.Bytes;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.core.common.UriUtils;
import org.opencb.opencga.storage.core.StoragePipelineResult;
import org.opencb.opencga.storage.core.config.DatabaseCredentials;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.config.StorageEtlConfiguration;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.exceptions.StoragePipelineException;
import org.opencb.opencga.storage.core.exceptions.VariantSearchException;
import org.opencb.opencga.storage.core.io.managers.IOManagerProvider;
import org.opencb.opencga.storage.core.metadata.VariantMetadataFactory;
import org.opencb.opencga.storage.core.metadata.VariantStorageMetadataManager;
import org.opencb.opencga.storage.core.metadata.models.StudyMetadata;
import org.opencb.opencga.storage.core.metadata.models.TaskMetadata;
import org.opencb.opencga.storage.core.utils.CellBaseUtils;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.VariantStoragePipeline;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryException;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils;
import org.opencb.opencga.storage.core.variant.adaptors.iterators.VariantDBIterator;
import org.opencb.opencga.storage.core.variant.annotation.VariantAnnotationManager;
import org.opencb.opencga.storage.core.variant.annotation.annotators.VariantAnnotator;
import org.opencb.opencga.storage.core.variant.io.VariantExporter;
import org.opencb.opencga.storage.core.variant.query.DBAdaptorVariantQueryExecutor;
import org.opencb.opencga.storage.core.variant.query.VariantQueryExecutor;
import org.opencb.opencga.storage.core.variant.search.SamplesSearchIndexVariantQueryExecutor;
import org.opencb.opencga.storage.core.variant.search.SearchIndexVariantQueryExecutor;
import org.opencb.opencga.storage.core.variant.search.solr.VariantSearchLoadListener;
import org.opencb.opencga.storage.core.variant.search.solr.VariantSearchLoadResult;
import org.opencb.opencga.storage.core.variant.stats.VariantStatisticsManager;
import org.opencb.opencga.storage.hadoop.auth.HBaseCredentials;
import org.opencb.opencga.storage.hadoop.io.HDFSIOManager;
import org.opencb.opencga.storage.hadoop.utils.DeleteHBaseColumnDriver;
import org.opencb.opencga.storage.hadoop.utils.HBaseManager;
import org.opencb.opencga.storage.hadoop.variant.adaptors.HBaseColumnIntersectVariantQueryExecutor;
import org.opencb.opencga.storage.hadoop.variant.adaptors.VariantHadoopDBAdaptor;
import org.opencb.opencga.storage.hadoop.variant.adaptors.phoenix.VariantPhoenixHelper;
import org.opencb.opencga.storage.hadoop.variant.annotation.HadoopDefaultVariantAnnotationManager;
import org.opencb.opencga.storage.hadoop.variant.archive.ArchiveTableHelper;
import org.opencb.opencga.storage.hadoop.variant.executors.ExternalMRExecutor;
import org.opencb.opencga.storage.hadoop.variant.executors.MRExecutor;
import org.opencb.opencga.storage.hadoop.variant.gaps.FillGapsDriver;
import org.opencb.opencga.storage.hadoop.variant.gaps.FillGapsFromArchiveMapper;
import org.opencb.opencga.storage.hadoop.variant.gaps.PrepareFillMissingDriver;
import org.opencb.opencga.storage.hadoop.variant.gaps.write.FillMissingHBaseWriterDriver;
import org.opencb.opencga.storage.hadoop.variant.index.SampleIndexCompoundHeterozygousQueryExecutor;
import org.opencb.opencga.storage.hadoop.variant.index.SampleIndexVariantQueryExecutor;
import org.opencb.opencga.storage.hadoop.variant.index.family.FamilyIndexDriver;
import org.opencb.opencga.storage.hadoop.variant.index.sample.SampleIndexConsolidationDrive;
import org.opencb.opencga.storage.hadoop.variant.index.sample.SampleIndexDBAdaptor;
import org.opencb.opencga.storage.hadoop.variant.index.sample.SampleIndexSchema;
import org.opencb.opencga.storage.hadoop.variant.io.HadoopVariantExporter;
import org.opencb.opencga.storage.hadoop.variant.search.HadoopVariantSearchLoadListener;
import org.opencb.opencga.storage.hadoop.variant.stats.HadoopDefaultVariantStatisticsManager;
import org.opencb.opencga.storage.hadoop.variant.stats.HadoopMRVariantStatisticsManager;
import org.opencb.opencga.storage.hadoop.variant.utils.HBaseVariantTableNameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.opencb.opencga.storage.core.variant.VariantStorageEngine.Options.MERGE_MODE;
import static org.opencb.opencga.storage.core.variant.VariantStorageEngine.Options.RESUME;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam.REGION;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam.STUDY;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.convertGenesToRegionsQuery;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.isValidParam;
import static org.opencb.opencga.storage.hadoop.variant.gaps.FillGapsDriver.*;

/**
 * Created by mh719 on 16/06/15.
 */
public class HadoopVariantStorageEngine extends VariantStorageEngine {
    public static final String STORAGE_ENGINE_ID = "hadoop";

    public static final String HADOOP_BIN = "hadoop.bin";
    public static final String HADOOP_ENV = "hadoop.env";
    public static final String OPENCGA_STORAGE_HADOOP_JAR_WITH_DEPENDENCIES = "opencga.storage.hadoop.jar-with-dependencies";
    @Deprecated
    public static final String HADOOP_LOAD_ARCHIVE = "hadoop.load.archive";
    @Deprecated
    public static final String HADOOP_LOAD_VARIANT = "hadoop.load.variant";
    // Resume merge variants if the current status is RUNNING or DONE
    /**
     * @deprecated use {@link Options#RESUME}
     */
    @Deprecated
    public static final String HADOOP_LOAD_VARIANT_RESUME = "hadoop.load.variant.resume";
    // Merge variants operation status. Skip merge and run post-load/post-merge step if status is DONE
    public static final String HADOOP_LOAD_VARIANT_STATUS = "hadoop.load.variant.status";
    //Other files to be loaded from Archive to Variant
    @Deprecated
    public static final String HADOOP_LOAD_VARIANT_PENDING_FILES = "opencga.storage.hadoop.load.pending.files";
    public static final String INTERMEDIATE_HDFS_DIRECTORY = "opencga.storage.hadoop.intermediate.hdfs.directory";

    public static final String HADOOP_LOAD_ARCHIVE_BATCH_SIZE = "hadoop.load.archive.batch.size";
    public static final String HADOOP_LOAD_VARIANT_BATCH_SIZE = "hadoop.load.variant.batch.size";
    @Deprecated
    public static final String HADOOP_LOAD_DIRECT = "hadoop.load.direct";
    @Deprecated
    public static final boolean HADOOP_LOAD_DIRECT_DEFAULT = true;

    @Deprecated
    public static final String MERGE_ARCHIVE_SCAN_BATCH_SIZE = "opencga.storage.hadoop.hbase.merge.archive.scan.batchsize";
    @Deprecated
    public static final int DEFAULT_MERGE_ARCHIVE_SCAN_BATCH_SIZE = 500;
    @Deprecated
    public static final String MERGE_COLLAPSE_DELETIONS = "opencga.storage.hadoop.hbase.merge.collapse-deletions";
    @Deprecated
    public static final boolean DEFAULT_MERGE_COLLAPSE_DELETIONS = false;
    @Deprecated
    public static final String MERGE_LOAD_SPECIFIC_PUT = "opencga.storage.hadoop.hbase.merge.use_specific_put";

    //upload HBase jars and jars for any of the configured job classes via the distributed cache (tmpjars).
    public static final String MAPREDUCE_ADD_DEPENDENCY_JARS = "opencga.mapreduce.addDependencyJars";
    public static final String MAPREDUCE_HBASE_SCANNER_TIMEOUT = "opencga.storage.hadoop.mapreduce.scanner.timeout";
    public static final String MAPREDUCE_HBASE_KEYVALUE_SIZE_MAX = "hadoop.load.variant.hbase.client.keyvalue.maxsize";
    public static final String MAPREDUCE_HBASE_SCAN_CACHING = "hadoop.load.variant.scan.caching";

    public static final String HBASE_NAMESPACE = "opencga.storage.hadoop.variant.hbase.namespace";
    public static final String HBASE_COLUMN_FAMILY = "opencga.hbase.column_family";
    public static final String EXPECTED_FILES_NUMBER = "expected_files_number";
    public static final int DEFAULT_EXPECTED_FILES_NUMBER = 5000;

    // Variant table configuration
    public static final String VARIANT_TABLE_COMPRESSION = "opencga.variant.table.compression";
    public static final String VARIANT_TABLE_PRESPLIT_SIZE = "opencga.variant.table.presplit.size";
    // Do not create phoenix indexes. Testing purposes only
    public static final String VARIANT_TABLE_INDEXES_SKIP = "opencga.variant.table.indexes.skip";
    public static final String VARIANT_TABLE_LOAD_REFERENCE = "opencga.variant.table.load.reference";

    // Archive table configuration
    public static final String ARCHIVE_TABLE_COMPRESSION = "opencga.archive.table.compression";
    public static final String ARCHIVE_TABLE_PRESPLIT_SIZE = "opencga.archive.table.presplit.size";
    public static final int DEFAULT_ARCHIVE_TABLE_PRESPLIT_SIZE = 100;
    public static final String ARCHIVE_CHUNK_SIZE = "opencga.archive.chunk_size";
    public static final int DEFAULT_ARCHIVE_CHUNK_SIZE = 1000;
    public static final String ARCHIVE_ROW_KEY_SEPARATOR = "opencga.archive.row_key_sep";
    public static final String ARCHIVE_FILE_BATCH_SIZE = "opencga.archive.file_batch_size";
    public static final int DEFAULT_ARCHIVE_FILE_BATCH_SIZE = 1000;
    public static final String ARCHIVE_FIELDS = "opencga.archive.fields";
    public static final String ARCHIVE_NON_REF_FILTER = "opencga.archive.non-ref.filter";

    // Sample index table configuration
    public static final String SAMPLE_INDEX_TABLE_COMPRESSION = "opencga.sample-index.table.compression";
    public static final String SAMPLE_INDEX_TABLE_PRESPLIT_SIZE = "opencga.sample-index.table.presplit.size";
    public static final int DEFAULT_SAMPLE_INDEX_TABLE_PRESPLIT_SIZE = 15;

    // Annotation index table  configuration
    public static final String ANNOTATION_INDEX_TABLE_COMPRESSION = "opencga.annotation-index.table.compression";
    public static final String PENDING_ANNOTATION_TABLE_COMPRESSION = "opencga.pending-annotation.table.compression";

    public static final String EXTERNAL_MR_EXECUTOR = "opencga.external.mr.executor";
    public static final String STATS_LOCAL = "stats.local";

    public static final String DBADAPTOR_PHOENIX_FETCH_SIZE = "dbadaptor.phoenix.fetch_size";
    public static final String MISSING_GENOTYPES_UPDATED = "missing_genotypes_updated";
    public static final int FILL_GAPS_MAX_SAMPLES = 100;

    public static final EnumSet<VariantType> TARGET_VARIANT_TYPE_SET = EnumSet.of(
            VariantType.SNV, VariantType.SNP,
            VariantType.INDEL, /* VariantType.INSERTION, VariantType.DELETION,*/
            VariantType.MNV, VariantType.MNP,
            VariantType.INSERTION, VariantType.DELETION,
            VariantType.CNV, VariantType.DUPLICATION, VariantType.TRANSLOCATION,
            VariantType.BREAKEND,
            VariantType.SV, VariantType.SYMBOLIC
    );

    public static final String FILE_ID = "fileId";
    public static final String STUDY_ID = "studyId";

    // Project attributes
    // Last time (in millis from epoch) that a file was loaded
    public static final String LAST_LOADED_FILE_TS = "lastLoadedFileTs";
    // Last time (in millis from epoch) that the list of "pendingVariantsToAnnotate" was updated
    public static final String LAST_VARIANTS_TO_ANNOTATE_UPDATE_TS = "lastVariantsToAnnotateUpdateTs";

    protected Configuration conf = null;
    protected MRExecutor mrExecutor;
    private HBaseManager hBaseManager;
    private final AtomicReference<VariantHadoopDBAdaptor> dbAdaptor = new AtomicReference<>();
    private Logger logger = LoggerFactory.getLogger(HadoopVariantStorageEngine.class);
    private HBaseVariantTableNameGenerator tableNameGenerator;
    private final AtomicReference<SampleIndexDBAdaptor> sampleIndexDBAdaptor = new AtomicReference<>();

    public HadoopVariantStorageEngine() {
//        variantReaderUtils = new HdfsVariantReaderUtils(conf);
    }

    @Override
    protected IOManagerProvider createIOManagerProvider(StorageConfiguration configuration) {
        IOManagerProvider ioManagerProvider = super.createIOManagerProvider(configuration);
        try {
            ioManagerProvider.add(new HDFSIOManager(getHadoopConfiguration()));
        } catch (StorageEngineException e) {
            throw new RuntimeException(e);
        }
        return ioManagerProvider;
    }

    @Override
    public List<StoragePipelineResult> index(List<URI> inputFiles, URI outdirUri, boolean doExtract, boolean doTransform, boolean doLoad)
            throws StorageEngineException {

        if (inputFiles.size() == 1 || !doLoad) {
            return super.index(inputFiles, outdirUri, doExtract, doTransform, doLoad);
        }

        final int nThreadArchive = getOptions().getInt(HADOOP_LOAD_ARCHIVE_BATCH_SIZE, 2);
        ObjectMap extraOptions = new ObjectMap();

        final List<StoragePipelineResult> concurrResult = new CopyOnWriteArrayList<>();
        List<VariantStoragePipeline> etlList = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(
                nThreadArchive,
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                }); // Set Daemon for quick shutdown !!!
        LinkedList<Future<StoragePipelineResult>> futures = new LinkedList<>();
        List<Integer> indexedFiles = new CopyOnWriteArrayList<>();
        AtomicBoolean continueLoading = new AtomicBoolean(true);
        for (URI inputFile : inputFiles) {
            //Provide a connected storageETL if load is required.

            VariantStoragePipeline storageETL = newStoragePipeline(doLoad, new ObjectMap(extraOptions));
            futures.add(executorService.submit(() -> {
                if (!continueLoading.get()) {
                    return null;
                }
                try {
                    Thread.currentThread().setName(UriUtils.fileName(inputFile));
                    StoragePipelineResult storagePipelineResult = new StoragePipelineResult(inputFile);
                    URI nextUri = inputFile;
                    boolean error = false;
                    if (doTransform) {
                        try {
                            nextUri = transformFile(storageETL, storagePipelineResult, concurrResult, nextUri, outdirUri);

                        } catch (StoragePipelineException ignore) {
                            //Ignore here. Errors are stored in the ETLResult
                            error = true;
                        }
                    }

                    if (doLoad && !error) {
                        try {
                            loadFile(storageETL, storagePipelineResult, concurrResult, nextUri, outdirUri);
                        } catch (StoragePipelineException ignore) {
                            //Ignore here. Errors are stored in the ETLResult
                            error = true;
                        }
                    }
                    if (doLoad && !error) {
                        // Read the VariantSource to get the original fileName (it may be different from the
                        // nextUri.getFileName if this is the transformed file)
                        String filePath = storageETL.readVariantFileMetadata(nextUri).getPath();
                        String fileName = Paths.get(filePath).getFileName().toString();
                        // Get latest study metadata from DB, might have been changed since
                        StudyMetadata studyMetadata = storageETL.getStudyMetadata();
                        // Get file ID for the provided file name
                        Integer fileId = storageETL.getMetadataManager().getFileId(studyMetadata.getId(), fileName);
                        indexedFiles.add(fileId);
                    }
                    return storagePipelineResult;
                } finally {
                    try {
                        storageETL.close();
                    } catch (StorageEngineException e) {
                        logger.error("Issue closing DB connection ", e);
                    }
                }
            }));
        }

        executorService.shutdown();

        int errors = 0;
        try {
            while (!futures.isEmpty()) {
                // Check values
                if (futures.peek().isDone() || futures.peek().isCancelled()) {
                    Future<StoragePipelineResult> first = futures.pop();
                    StoragePipelineResult result = first.get(1, TimeUnit.MINUTES);
                    if (result == null) {
                        continue;
                    }
                    boolean error = false;
                    if (result.getTransformError() != null) {
                        logger.error("Error transforming file " + result.getInput(), result.getTransformError());
                        error = true;
                    } else if (result.getLoadError() != null) {
                        error = true;
                        logger.error("Error loading file " + result.getInput(), result.getLoadError());
                    }
                    if (error) {
                        //TODO: Handle errors. Retry?
                        errors++;
                        if (getOptions().getBoolean("abortOnError", false)) {
                            continueLoading.set(false);
                        }
                    }
                    concurrResult.add(result);
                } else {
                    // Sleep only if the task is not done
                    executorService.awaitTermination(1, TimeUnit.MINUTES);
                }
            }
            if (errors > 0) {
                throw new StoragePipelineException("Errors found", concurrResult);
            }

            if (doLoad) {
                annotateLoadedFiles(outdirUri, inputFiles, concurrResult, getOptions());
                calculateStatsForLoadedFiles(outdirUri, inputFiles, concurrResult, getOptions());
                searchIndexLoadedFiles(inputFiles, getOptions());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StoragePipelineException("Interrupted!", e, concurrResult);
        } catch (ExecutionException e) {
            throw new StoragePipelineException("Execution exception!", e, concurrResult);
        } catch (TimeoutException e) {
            throw new StoragePipelineException("Timeout Exception", e, concurrResult);
        } finally {
            if (!executorService.isShutdown()) {
                try {
                    executorService.shutdownNow();
                } catch (Exception e) {
                    logger.error("Problems shutting executer service down", e);
                }
            }
        }
        return concurrResult;
    }

    @Override
    public HadoopVariantStoragePipeline newStoragePipeline(boolean connected) throws StorageEngineException {
        return newStoragePipeline(connected, null);
    }

    @Override
    protected VariantAnnotationManager newVariantAnnotationManager(VariantAnnotator annotator) throws StorageEngineException {
        return new HadoopDefaultVariantAnnotationManager(annotator, getDBAdaptor(), getMRExecutor(), getOptions(), ioManagerProvider);
    }

    @Override
    protected VariantExporter newVariantExporter(VariantMetadataFactory metadataFactory) throws StorageEngineException {
        return new HadoopVariantExporter(this, metadataFactory, getMRExecutor(), ioManagerProvider);
    }

    @Override
    public void calculateMendelianErrors(String study, List<List<String>> trios, ObjectMap options) throws StorageEngineException {
        options = getMergedOptions(options);
        if (trios.size() < 1000) {
            options.put(FamilyIndexDriver.TRIOS, trios.stream().map(trio -> String.join(",", trio)).collect(Collectors.joining(";")));
        } else {
            File mendelianErrorsFile = null;
            try {
                mendelianErrorsFile = File.createTempFile("mendelian_errors.", ".tmp");
                try (OutputStream os = FileUtils.openOutputStream(mendelianErrorsFile)) {
                    for (List<String> trio : trios) {
                        os.write(String.join(",", trio).getBytes());
                        os.write('\n');
                    }
                }
            } catch (IOException e) {
                if (mendelianErrorsFile == null) {
                    throw new StorageEngineException("Error generating temporary file.", e);
                } else {
                    throw new StorageEngineException("Error writing temporary file " + mendelianErrorsFile, e);
                }
            }
            options.put(FamilyIndexDriver.TRIOS_FILE, mendelianErrorsFile.toPath().toAbsolutePath().toString());
            options.put(FamilyIndexDriver.TRIOS_FILE_DELETE, true);
        }

        int studyId = getMetadataManager().getStudyId(study);
        getMRExecutor().run(FamilyIndexDriver.class, FamilyIndexDriver.buildArgs(getArchiveTableName(studyId), getVariantTableName(),
                studyId, null, options), options,
                "Precompute mendelian errors for " + (trios.size() == 1 ? "trio " + trios.get(0) : trios.size() + " trios"));
    }


    @Override
    public VariantStatisticsManager newVariantStatisticsManager() throws StorageEngineException {
        // By default, execute a MR to calculate statistics
        if (getOptions().getBoolean(STATS_LOCAL, false)) {
            return new HadoopDefaultVariantStatisticsManager(getDBAdaptor(), ioManagerProvider);
        } else {
            return new HadoopMRVariantStatisticsManager(getDBAdaptor(), getMRExecutor(), getOptions());
        }
    }

    @Override
    public void fillMissing(String study, ObjectMap options, boolean overwrite) throws StorageEngineException {
        logger.info("FillMissing: Study " + study);

        VariantStorageMetadataManager metadataManager = getMetadataManager();
        StudyMetadata studyMetadata = metadataManager.getStudyMetadata(study);

        fillGapsOrMissing(study, studyMetadata, metadataManager.getIndexedFiles(studyMetadata.getId()), Collections.emptyList(),
                false, overwrite, options);
    }

    @Override
    public VariantSearchLoadResult searchIndex(Query query, QueryOptions queryOptions, boolean overwrite)
            throws StorageEngineException, IOException, VariantSearchException {
        queryOptions = queryOptions == null ? new QueryOptions() : new QueryOptions(queryOptions);
        queryOptions.putIfAbsent(VariantHadoopDBAdaptor.NATIVE, true);
        return super.searchIndex(query, queryOptions, overwrite);
    }

    @Override
    protected VariantDBIterator getVariantsToIndex(boolean overwrite, Query query, QueryOptions queryOptions, VariantDBAdaptor dbAdaptor)
            throws StorageEngineException {
        if (!overwrite) {
            query.put(VariantQueryUtils.VARIANTS_TO_INDEX.key(), true);
            logger.info("Column intersect!");
//        queryOptions.put("multiIteratorBatchSize", 1000);
            return new HBaseColumnIntersectVariantQueryExecutor(getDBAdaptor(), getStorageEngineId(), getOptions())
                    .iterator(query, queryOptions);
        } else {
            logger.info("Get variants to index");
            return super.getVariantsToIndex(overwrite, query, queryOptions, dbAdaptor);
        }
    }

    @Override
    protected VariantSearchLoadListener newVariantSearchLoadListener() throws StorageEngineException {
        return new HadoopVariantSearchLoadListener(getDBAdaptor());
    }

    @Override
    public void fillGaps(String study, List<String> samples, ObjectMap options) throws StorageEngineException {
        if (samples == null || samples.size() < 2) {
            throw new IllegalArgumentException("Fill gaps operation requires at least two samples.");
        } else if (samples.size() > FILL_GAPS_MAX_SAMPLES) {
            throw new IllegalArgumentException("Unable to execute fill gaps operation with more than "
                    + FILL_GAPS_MAX_SAMPLES + " samples.");
        }

        VariantStorageMetadataManager metadataManager = getMetadataManager();
        StudyMetadata studyMetadata = metadataManager.getStudyMetadata(study);
        List<Integer> sampleIds = new ArrayList<>(samples.size());
        for (String sample : samples) {
            Integer sampleId = metadataManager.getSampleId(studyMetadata.getId(), sample);
            if (sampleId != null) {
                sampleIds.add(sampleId);
            } else {
                throw VariantQueryException.sampleNotFound(sample, studyMetadata.getName());
            }
        }

        // Get files
        Set<Integer> fileIds = metadataManager.getFileIdsFromSampleIds(studyMetadata.getId(), sampleIds);

        logger.info("FillGaps: Study " + study + ", samples " + samples);
        fillGapsOrMissing(study, studyMetadata, fileIds, sampleIds, true, false, options);
    }

    private void fillGapsOrMissing(String study, StudyMetadata studyMetadata, Set<Integer> fileIds, List<Integer> sampleIds,
                                   boolean fillGaps, boolean overwrite, ObjectMap inputOptions) throws StorageEngineException {
        ObjectMap options = new ObjectMap(getOptions());
        if (inputOptions != null) {
            options.putAll(inputOptions);
        }

        VariantStorageMetadataManager metadataManager = getMetadataManager();
        int studyId = studyMetadata.getId();

        String jobOperationName = fillGaps ? FILL_GAPS_OPERATION_NAME : FILL_MISSING_OPERATION_NAME;
        List<Integer> fileIdsList = new ArrayList<>(fileIds);
        fileIdsList.sort(Integer::compareTo);

        boolean resume = options.getBoolean(RESUME.key(), RESUME.defaultValue());
        TaskMetadata task = metadataManager.addRunningTask(
                studyId,
                jobOperationName,
                fileIdsList,
                resume,
                TaskMetadata.Type.OTHER,
                // Allow concurrent operations if fillGaps.
                (v) -> fillGaps || v.getName().equals(FILL_GAPS_OPERATION_NAME));
        options.put(AbstractVariantsTableDriver.TIMESTAMP, task.getTimestamp());

        if (!fillGaps) {
            URI directory = URI.create(options.getString(INTERMEDIATE_HDFS_DIRECTORY));
            if (directory.getScheme() != null && !directory.getScheme().equals("hdfs")) {
                throw new StorageEngineException("Output must be in HDFS");
            }
            String regionStr = options.getString(VariantQueryParam.REGION.key());
            String outputPath = directory.resolve(dbName + "_fill_missing_study_" + studyId
                    + (StringUtils.isNotEmpty(regionStr) ? '_' + regionStr.replace(':', '_').replace('-', '_') : "") + ".bin").toString();
            logger.info("Using intermediate file = " + outputPath);
            options.put(FILL_MISSING_INTERMEDIATE_FILE, outputPath);
        }

        Thread hook = metadataManager.buildShutdownHook(jobOperationName, studyId, task.getId());
        Exception exception = null;
        try {
            Runtime.getRuntime().addShutdownHook(hook);

            options.put(FillGapsFromArchiveMapper.SAMPLES, sampleIds);
            options.put(FillGapsFromArchiveMapper.FILL_GAPS, fillGaps);
            options.put(FillGapsFromArchiveMapper.OVERWRITE, overwrite);

            String[] args = FillGapsDriver.buildArgs(
                    getArchiveTableName(studyId),
                    getVariantTableName(),
                    studyId, fileIds, options);

            // TODO: Save progress in Metadata

            // Prepare fill missing
            if (!fillGaps) {
                if (options.getBoolean("skipPrepareFillMissing", false)) {
                    logger.info("=================================================");
                    logger.info("SKIP prepare archive table for " + FILL_MISSING_OPERATION_NAME);
                    logger.info("=================================================");
                } else {
                    String taskDescription = "Prepare archive table for " + FILL_MISSING_OPERATION_NAME;
                    getMRExecutor().run(PrepareFillMissingDriver.class, args, options, taskDescription);
                }
            }

            // Execute main operation
            String taskDescription = jobOperationName + " of samples " + (fillGaps ? sampleIds.toString() : "\"ALL\"")
                    + " into variants table '" + getVariantTableName() + '\'';
            getMRExecutor().run(FillGapsDriver.class, args, options, taskDescription);

            // Write results
            if (!fillGaps) {
                taskDescription = "Write results in variants table for " + FILL_MISSING_OPERATION_NAME;
                getMRExecutor().run(FillMissingHBaseWriterDriver.class, args, options, taskDescription);
            }

            // Consolidate sample index table
            taskDescription = "Consolidate sample index table";
            getMRExecutor().run(SampleIndexConsolidationDrive.class, args, options, taskDescription);

        } catch (RuntimeException e) {
            exception = e;
            throw new StorageEngineException("Error " + jobOperationName + " for samples " + sampleIds, e);
        } catch (StorageEngineException e) {
            exception = e;
            throw e;
        } finally {
            boolean fail = exception != null;
            metadataManager.setStatus(studyId, task.getId(), fail ? TaskMetadata.Status.ERROR : TaskMetadata.Status.READY);
            metadataManager.updateStudyMetadata(study, sm -> {
                if (!fillGaps && StringUtils.isEmpty(options.getString(REGION.key()))) {
                    sm.getAttributes().put(MISSING_GENOTYPES_UPDATED, !fail);
                }
                return sm;
            });
            Runtime.getRuntime().removeShutdownHook(hook);
        }

    }

    public HadoopVariantStoragePipeline newStoragePipeline(boolean connected, Map<? extends String, ?> extraOptions)
            throws StorageEngineException {
        ObjectMap options = getMergedOptions(extraOptions);
//        if (connected) {
//            // Ensure ProjectMetadata exists. Don't really care about the value.
//            getStudyConfigurationManager().getProjectMetadata(getMergedOptions(options)).first();
//        }
        VariantHadoopDBAdaptor dbAdaptor = connected ? getDBAdaptor() : null;
        Configuration hadoopConfiguration = null == dbAdaptor ? null : dbAdaptor.getConfiguration();
        hadoopConfiguration = hadoopConfiguration == null ? getHadoopConfiguration(options) : hadoopConfiguration;
        hadoopConfiguration.setIfUnset(ARCHIVE_TABLE_COMPRESSION, Algorithm.SNAPPY.getName());
        for (String key : options.keySet()) {
            hadoopConfiguration.set(key, options.getString(key));
        }

        MergeMode mergeMode;
        if (connected) {
            String study = options.getString(Options.STUDY.key());
//            archiveCredentials = buildCredentials(getArchiveTableName(studyId));
            StudyMetadata sm = getMetadataManager().getStudyMetadata(study);
            if (sm == null || !sm.getAttributes().containsKey(MERGE_MODE.key())) {
                mergeMode = MergeMode.from(options);
            } else {
                mergeMode = MergeMode.from(sm.getAttributes());
            }
        } else {
            mergeMode = MergeMode.BASIC;
        }

        if (mergeMode.equals(MergeMode.ADVANCED)) {
//            throw new IllegalStateException("Unable to load with MergeMode " + MergeMode.ADVANCED);
            // Force to use MergeMode=BASIC for Hadoop
            options.put(MERGE_MODE.key(), MergeMode.BASIC.name());
            mergeMode = MergeMode.BASIC;
        }
        HadoopVariantStoragePipeline storageETL = new HadoopLocalLoadVariantStoragePipeline(configuration, dbAdaptor,
                ioManagerProvider, hadoopConfiguration, options);
//        if (mergeMode.equals(MergeMode.BASIC)) {
//            storageETL = new HadoopMergeBasicVariantStoragePipeline(configuration, dbAdaptor,
//                    hadoopConfiguration, archiveCredentials, getVariantReaderUtils(hadoopConfiguration), options);
//        } else {
//            storageETL = new HadoopVariantStoragePipelineMRLoad(configuration, dbAdaptor, getMRExecutor(options),
//                    hadoopConfiguration, archiveCredentials, getVariantReaderUtils(hadoopConfiguration), options);
//        }
        return storageETL;
    }

    @Override
    public void removeFiles(String study, List<String> files) throws StorageEngineException {
        ObjectMap options = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant().getOptions();

        VariantHadoopDBAdaptor dbAdaptor = getDBAdaptor();
        VariantStorageMetadataManager metadataManager = dbAdaptor.getMetadataManager();
        TaskMetadata task = preRemoveFiles(study, files);
        List<Integer> fileIds = task.getFileIds();
        final int studyId = metadataManager.getStudyId(study);

//        // Pre delete
//        scm.lockAndUpdate(studyId, sc -> {
//            if (!sc.getIndexedFiles().contains(fileId)) {
//                throw StorageEngineException.unableToExecute("File not indexed.", fileId, sc);
//            }
//            boolean resume = options.getBoolean(Options.RESUME.key(), Options.RESUME.defaultValue())
//                    || options.getBoolean(HadoopVariantStorageEngine.HADOOP_LOAD_VARIANT_RESUME, false);
//            BatchFileOperation operation =
//                    etl.addBatchOperation(sc, VariantTableRemoveFileDriver.JOB_OPERATION_NAME, fileList, resume,
//                            BatchFileOperation.Type.REMOVE);
//            options.put(AbstractAnalysisTableDriver.TIMESTAMP, operation.getTimestamp());
//            return sc;
//        });

        StudyMetadata sm = metadataManager.getStudyMetadata(studyId);
        LinkedHashSet<Integer> indexedFiles = metadataManager.getIndexedFiles(sm.getId());
        boolean removeWholeStudy = indexedFiles.size() == fileIds.size() && indexedFiles.containsAll(fileIds);
        options.put(AbstractVariantsTableDriver.TIMESTAMP, task.getTimestamp());

        // Delete
        Thread hook = getMetadataManager().buildShutdownHook(REMOVE_OPERATION_NAME, studyId, task.getId());
        try {
            Runtime.getRuntime().addShutdownHook(hook);

            String archiveTable = getArchiveTableName(studyId);
            String variantsTable = getVariantTableName();
            String sampleIndexTable = getTableNameGenerator().getSampleIndexTableName(studyId);
            options.put(DeleteHBaseColumnDriver.DELETE_HBASE_COLUMN_MAPPER_CLASS, MyDeleteHBaseColumnMapper.class.getName());

            long startTime = System.currentTimeMillis();
            logger.info("------------------------------------------------------");
            logger.info("Remove files {} in archive '{}' and analysis table '{}'", fileIds, archiveTable, variantsTable);
            logger.info("------------------------------------------------------");
            ExecutorService service = Executors.newFixedThreadPool(options.getBoolean("delete.parallel", true) ? 3 : 1);
            Future<Integer> deleteFromVariants = service.submit(() -> {
                List<String> variantsColumns = new ArrayList<>();
                String family = Bytes.toString(dbAdaptor.getGenomeHelper().getColumnFamily());
                for (Integer fileId : fileIds) {
                    variantsColumns.add(family + ':' + VariantPhoenixHelper.getFileColumn(studyId, fileId).column());
                    for (Integer sampleId : metadataManager.getFileMetadata(sm.getId(), fileId).getSamples()) {
                        variantsColumns.add(family + ':' + VariantPhoenixHelper.getSampleColumn(studyId, sampleId).column());
                    }
                }
                if (removeWholeStudy) {
                    variantsColumns.add(family + ':' + VariantPhoenixHelper.getStudyColumn(studyId).column());
                }

                String[] deleteFromVariantsArgs = DeleteHBaseColumnDriver.buildArgs(variantsTable, variantsColumns, options);
                getMRExecutor().run(DeleteHBaseColumnDriver.class, deleteFromVariantsArgs, options, "Delete from variants table");
                return 0;
            });
            // TODO: Remove whole table if removeWholeStudy
            Future<Integer> deleteFromArchive = service.submit(() -> {
                List<String> archiveColumns = new ArrayList<>();
                String family = Bytes.toString(dbAdaptor.getGenomeHelper().getColumnFamily());
                for (Integer fileId : fileIds) {
                    archiveColumns.add(family + ':' + ArchiveTableHelper.getRefColumnName(fileId));
                    archiveColumns.add(family + ':' + ArchiveTableHelper.getNonRefColumnName(fileId));
                }
                String[] deleteFromArchiveArgs = DeleteHBaseColumnDriver.buildArgs(archiveTable, archiveColumns, options);
                getMRExecutor().run(DeleteHBaseColumnDriver.class, deleteFromArchiveArgs, options, "Delete from archive table");
                return 0;
            });
            // TODO: Remove whole table if removeWholeStudy
            Future<Integer> deleteFromSampleIndex = service.submit(() -> {
                Set<Integer> sampleIds = new HashSet<>();
                for (Integer fileId : fileIds) {
                    sampleIds.addAll(metadataManager.getFileMetadata(sm.getId(), fileId).getSamples());
                }
                if (!sampleIds.isEmpty()) {
                    List<org.apache.hadoop.hbase.util.Pair<byte[], byte[]>> regions = new ArrayList<>();
                    for (Integer sampleId : sampleIds) {
                        regions.add(new org.apache.hadoop.hbase.util.Pair<>(
                                SampleIndexSchema.toRowKey(sampleId),
                                SampleIndexSchema.toRowKey(sampleId + 1)));
                    }
                    String[] deleteFromSampleIndexArgs = DeleteHBaseColumnDriver.buildArgs(sampleIndexTable, null, true, regions, options);
                    getMRExecutor().run(DeleteHBaseColumnDriver.class, deleteFromSampleIndexArgs, options,
                            "Delete from SamplesIndex table");
                }
                return 0;
            });
            service.shutdown();
            service.awaitTermination(12, TimeUnit.HOURS);
            logger.info("------------------------------------------------------");
            logger.info("Exit value delete from variants: {}", deleteFromVariants.get());
            logger.info("Exit value delete from archive: {}", deleteFromArchive.get());
            logger.info("Exit value delete from sample index: {}", deleteFromSampleIndex.get());
            logger.info("Total time: {}s", (System.currentTimeMillis() - startTime) / 1000.0);
            if (deleteFromArchive.get() != 0 || deleteFromVariants.get() != 0) {
                throw new StorageEngineException("Error removing files " + fileIds + " from tables ");
            }

//            // Post Delete
//            // If everything went fine, remove file column from Archive table and from studyconfig
//            scm.lockAndUpdate(studyId, sc -> {
//                scm.setStatus(sc, BatchFileOperation.Status.READY,
//                        VariantTableRemoveFileDriver.JOB_OPERATION_NAME, fileIds);
//                sc.getIndexedFiles().remove(fileId);
//                return sc;
//            });

            postRemoveFiles(study, fileIds, task.getId(), false);
        } catch (StorageEngineException e) {
            postRemoveFiles(study, fileIds, task.getId(), true);
            throw e;
        } catch (Exception e) {
            postRemoveFiles(study, fileIds, task.getId(), true);
            throw new StorageEngineException("Error removing files " + fileIds + " from tables ", e);
        } finally {
            Runtime.getRuntime().removeShutdownHook(hook);
        }
    }

    @Override
    protected void postRemoveFiles(String study, List<Integer> fileIds, int taskId, boolean error) throws StorageEngineException {
        super.postRemoveFiles(study, fileIds, taskId, error);
        if (!error) {
            VariantHadoopDBAdaptor dbAdaptor = getDBAdaptor();
            VariantPhoenixHelper phoenixHelper = new VariantPhoenixHelper(dbAdaptor.getGenomeHelper());

            StudyMetadata sm = getMetadataManager().getStudyMetadata(study);

            List<Integer> sampleIds = new ArrayList<>();
            for (Integer fileId : fileIds) {
                sampleIds.addAll(getMetadataManager().getFileMetadata(sm.getId(), fileId).getSamples());
            }

            try {
                phoenixHelper.dropFiles(dbAdaptor.getJdbcConnection(), dbAdaptor.getVariantTable(), sm.getId(), fileIds, sampleIds);
            } catch (SQLException e) {
                throw new StorageEngineException("Error removing columns from Phoenix", e);
            }
        }
    }

    @Override
    public void removeStudy(String studyName) throws StorageEngineException {
        int studyId = getMetadataManager().getStudyId(studyName);
        removeFiles(studyName, getMetadataManager().getIndexedFiles(studyId).stream().map(Object::toString).collect(Collectors.toList()));
    }

    private HBaseCredentials getDbCredentials() throws StorageEngineException {
        String table = getVariantTableName();
        return buildCredentials(table);
    }

    @Override
    public VariantHadoopDBAdaptor getDBAdaptor() throws StorageEngineException {
        if (dbAdaptor.get() == null) {
            synchronized (dbAdaptor) {
                if (dbAdaptor.get() == null) {
                    HBaseCredentials credentials = getDbCredentials();
                    try {
                        Configuration configuration = getHadoopConfiguration();
                        configuration = VariantHadoopDBAdaptor.getHbaseConfiguration(configuration, credentials);
                        dbAdaptor.set(new VariantHadoopDBAdaptor(getHBaseManager(configuration), credentials,
                                this.configuration, configuration, getTableNameGenerator()));
                    } catch (IOException e) {
                        throw new StorageEngineException("Error creating DB Adapter", e);
                    }
                }
            }
        }
        return dbAdaptor.get();
    }

    public SampleIndexDBAdaptor getSampleIndexDBAdaptor() throws StorageEngineException {
        VariantHadoopDBAdaptor dbAdaptor = getDBAdaptor();
        SampleIndexDBAdaptor sampleIndexDBAdaptor = this.sampleIndexDBAdaptor.get();
        if (sampleIndexDBAdaptor == null) {
            synchronized (this.sampleIndexDBAdaptor) {
                sampleIndexDBAdaptor = this.sampleIndexDBAdaptor.get();
                if (sampleIndexDBAdaptor == null) {
                    sampleIndexDBAdaptor = new SampleIndexDBAdaptor(dbAdaptor.getGenomeHelper(), dbAdaptor.getHBaseManager(),
                            dbAdaptor.getTableNameGenerator(), dbAdaptor.getMetadataManager());
                    this.sampleIndexDBAdaptor.set(sampleIndexDBAdaptor);
                }
            }
        }
        return sampleIndexDBAdaptor;
    }

    private synchronized HBaseManager getHBaseManager(Configuration configuration) {
        if (hBaseManager == null) {
            hBaseManager = new HBaseManager(configuration);
        }
        return hBaseManager;
    }

    @Override
    public Query preProcessQuery(Query originalQuery, QueryOptions options) {
        Query query = super.preProcessQuery(originalQuery, options);

        VariantStorageMetadataManager metadataManager;
        CellBaseUtils cellBaseUtils;
        try {
            metadataManager = getMetadataManager();
            cellBaseUtils = getCellBaseUtils();
        } catch (StorageEngineException e) {
            throw VariantQueryException.internalException(e);
        }
        List<String> studyNames = metadataManager.getStudyNames();

        if (isValidParam(query, STUDY) && studyNames.size() == 1) {
            query.remove(STUDY.key());
        }

        convertGenesToRegionsQuery(query, cellBaseUtils);
        return query;
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (hBaseManager != null) {
            hBaseManager.close();
            hBaseManager = null;
        }
        if (dbAdaptor.get() != null) {
            dbAdaptor.get().close();
            dbAdaptor.set(null);
        }
        if (sampleIndexDBAdaptor.get() != null) {
//            sampleIndexDBAdaptor.get().close();
            sampleIndexDBAdaptor.set(null);
        }
    }

    private HBaseCredentials buildCredentials(String table) throws StorageEngineException {
        StorageEtlConfiguration vStore = configuration.getStorageEngine(STORAGE_ENGINE_ID).getVariant();

        DatabaseCredentials db = vStore.getDatabase();
        String user = db.getUser();
        String pass = db.getPassword();
        List<String> hostList = db.getHosts();
        if (hostList != null && hostList.size() > 1) {
            throw new IllegalStateException("Expect only one server name");
        }
        String target = hostList != null && !hostList.isEmpty() ? hostList.get(0) : null;
        try {
            String server;
            Integer port;
            String zookeeperPath;
            if (target == null || target.isEmpty()) {
                Configuration conf = getHadoopConfiguration();
                server = conf.get(HConstants.ZOOKEEPER_QUORUM);
                port = 60000;
                zookeeperPath = conf.get(HConstants.ZOOKEEPER_ZNODE_PARENT);
            } else {
                URI uri;
                try {
                    uri = new URI(target);
                } catch (URISyntaxException e) {
                    try {
                        uri = new URI("hbase://" + target);
                    } catch (URISyntaxException e1) {
                        throw e;
                    }
                }
                server = uri.getHost();
                port = uri.getPort() > 0 ? uri.getPort() : 60000;
                // If just an IP or host name is provided, the URI parser will return empty host, and the content as "path". Avoid that
                if (server == null) {
                    server = uri.getPath();
                    zookeeperPath = null;
                } else {
                    zookeeperPath = uri.getPath();
                }
            }
            HBaseCredentials credentials;
            if (!StringUtils.isBlank(zookeeperPath)) {
                credentials = new HBaseCredentials(server, table, user, pass, port, zookeeperPath);
            } else {
                credentials = new HBaseCredentials(server, table, user, pass, port);
            }
            return credentials;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public VariantStorageMetadataManager getMetadataManager() throws StorageEngineException {
        return getDBAdaptor().getMetadataManager();
    }

    @Override
    protected List<VariantQueryExecutor> initVariantQueryExecutors() throws StorageEngineException {
        List<VariantQueryExecutor> executors = new ArrayList<>(6);

        executors.add(new SampleIndexCompoundHeterozygousQueryExecutor(
                getMetadataManager(), getStorageEngineId(), getOptions(), this, getSampleIndexDBAdaptor()));
        executors.add(new SamplesSearchIndexVariantQueryExecutor(
                getDBAdaptor(), getVariantSearchManager(), getStorageEngineId(), dbName, getConfiguration(), getOptions()));
        executors.add(new SampleIndexVariantQueryExecutor(
                getDBAdaptor(), getSampleIndexDBAdaptor(), getStorageEngineId(), getOptions()));
        executors.add(new SearchIndexVariantQueryExecutor(
                getDBAdaptor(), getVariantSearchManager(), getStorageEngineId(), dbName, getConfiguration(), getOptions()));
        executors.add(new HBaseColumnIntersectVariantQueryExecutor(
                getDBAdaptor(), getStorageEngineId(), getOptions()));
        executors.add(new DBAdaptorVariantQueryExecutor(
                getDBAdaptor(), getStorageEngineId(), getOptions()));

        return executors;
    }

    private Configuration getHadoopConfiguration() throws StorageEngineException {
        return getHadoopConfiguration(getOptions());
    }

    private Configuration getHadoopConfiguration(ObjectMap options) throws StorageEngineException {
        Configuration conf = this.conf == null ? HBaseConfiguration.create() : this.conf;
        // This is the only key needed to connect to HDFS:
        //   CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY = fs.defaultFS
        //

        if (conf.get(CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY) == null) {
            throw new StorageEngineException("Missing configuration parameter \""
                    + CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY + "\"");
        }

        options.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> conf.set(entry.getKey(), options.getString(entry.getKey())));
        return conf;
    }

    private MRExecutor getMRExecutor() {
        ObjectMap options = getOptions();
        if (options.containsKey(EXTERNAL_MR_EXECUTOR)) {
            Class<? extends MRExecutor> aClass;
            if (options.get(EXTERNAL_MR_EXECUTOR) instanceof Class) {
                aClass = options.get(EXTERNAL_MR_EXECUTOR, Class.class).asSubclass(MRExecutor.class);
            } else {
                try {
                    aClass = Class.forName(options.getString(EXTERNAL_MR_EXECUTOR)).asSubclass(MRExecutor.class);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            try {
                return aClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else if (mrExecutor == null) {
            return new ExternalMRExecutor(options);
        } else {
            return mrExecutor;
        }
    }

    /**
     * Get the archive table name given a StudyId.
     *
     * @param studyId Numerical study identifier
     * @return Table name
     */
    public String getArchiveTableName(int studyId) {
        return getTableNameGenerator().getArchiveTableName(studyId);
    }

    public String getVariantTableName() {
        return getTableNameGenerator().getVariantTableName();
    }

    private HBaseVariantTableNameGenerator getTableNameGenerator() {
        if (tableNameGenerator == null) {
            tableNameGenerator = new HBaseVariantTableNameGenerator(dbName, getOptions());
        }
        return tableNameGenerator;
    }

    public static String getJarWithDependencies(ObjectMap options) throws StorageEngineException {
        String jar = options.getString(OPENCGA_STORAGE_HADOOP_JAR_WITH_DEPENDENCIES, null);
        if (jar == null) {
            throw new StorageEngineException("Missing option " + OPENCGA_STORAGE_HADOOP_JAR_WITH_DEPENDENCIES);
        }
        if (!Paths.get(jar).isAbsolute()) {
            jar = System.getProperty("app.home", "") + "/" + jar;
        }
        return jar;
    }

    /**
     * Created on 23/04/18.
     *
     * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
     */
    public static class MyDeleteHBaseColumnMapper extends DeleteHBaseColumnDriver.DeleteHBaseColumnMapper {

        private byte[] columnFamily;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            super.setup(context);
            columnFamily = new GenomeHelper(context.getConfiguration()).getColumnFamily();
        }

        @Override
        protected void map(ImmutableBytesWritable key, Result result, Context context) throws IOException, InterruptedException {
            super.map(key, result, context);
    //        context.write(key, HadoopVariantSearchIndexUtils.addUnknownSyncStatus(new Put(result.getRow()), columnFamily));
        }
    }
}
