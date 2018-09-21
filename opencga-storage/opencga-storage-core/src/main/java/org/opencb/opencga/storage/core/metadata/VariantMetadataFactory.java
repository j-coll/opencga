package org.opencb.opencga.storage.core.metadata;

import org.opencb.biodata.models.variant.metadata.VariantMetadata;
import org.opencb.biodata.models.variant.metadata.VariantStudyMetadata;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.metadata.adaptors.VariantFileMetadataDBAdaptor;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates a VariantMetadata from other metadata information stored in the database.
 *
 * Created on 17/08/17.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class VariantMetadataFactory {

    protected final StudyConfigurationManager scm;

    public VariantMetadataFactory(StudyConfigurationManager studyConfigurationManager) {
        scm = studyConfigurationManager;
    }

    public VariantMetadata makeVariantMetadata() throws StorageEngineException {
        return makeVariantMetadata(new Query(), new QueryOptions());
    }

    public VariantMetadata makeVariantMetadata(Query query, QueryOptions queryOptions) throws StorageEngineException {
        VariantQueryUtils.SelectVariantElements selectElements = VariantQueryUtils.parseSelectElements(query, queryOptions, scm);
        Map<Integer, List<Integer>> returnedSamples = selectElements.getSamples();
        Map<Integer, List<Integer>> returnedFiles = selectElements.getFiles();

        ProjectMetadata projectMetadata = scm.getProjectMetadata().first();

        List<StudyConfiguration> studyConfigurations = new ArrayList<>(selectElements.getStudies().size());

        for (Integer studyId : selectElements.getStudies()) {
            studyConfigurations.add(scm.getStudyConfiguration(studyId, QueryOptions.empty()).first());
        }

        return makeVariantMetadata(studyConfigurations, projectMetadata, returnedSamples, returnedFiles, queryOptions);
    }

    protected VariantMetadata makeVariantMetadata(List<StudyConfiguration> studyConfigurations,
                                                  ProjectMetadata projectMetadata, Map<Integer, List<Integer>> returnedSamples,
                                                  Map<Integer, List<Integer>> returnedFiles, QueryOptions queryOptions)
            throws StorageEngineException {
        VariantMetadata metadata = new VariantMetadataConverter()
                .toVariantMetadata(studyConfigurations, projectMetadata, returnedSamples, returnedFiles);

        Map<String, StudyConfiguration> studyConfigurationMap = studyConfigurations.stream()
                .collect(Collectors.toMap(StudyConfiguration::getStudyName, Function.identity()));

        for (VariantStudyMetadata studyMetadata : metadata.getStudies()) {
            StudyConfiguration studyConfiguration = studyConfigurationMap.get(studyMetadata.getId());
            List<Integer> fileIds = studyMetadata.getFiles().stream()
                    .map(fileMetadata -> {
                        Integer fileId = studyConfiguration.getFileIds().get(fileMetadata.getId());
                        if (fileId == null) {
                            fileId = studyConfiguration.getFileIds().get(fileMetadata.getPath());
                        }
                        return fileId;
                    }).collect(Collectors.toList());
            if (fileIds != null && !fileIds.isEmpty()) {
                Query query = new Query()
                        .append(VariantFileMetadataDBAdaptor.VariantFileMetadataQueryParam.STUDY_ID.key(), studyConfiguration.getStudyId())
                        .append(VariantFileMetadataDBAdaptor.VariantFileMetadataQueryParam.FILE_ID.key(), fileIds);
                scm.variantFileMetadataIterator(query, new QueryOptions()).forEachRemaining(fileMetadata -> {
                    studyMetadata.getFiles().removeIf(file -> file.getId().equals(fileMetadata.getId()));
                    studyMetadata.getFiles().add(fileMetadata.getImpl());
                });
            }
        }
/*
2018-09-21 10:41:41,639 INFO [htable-pool3-t14] org.apache.hadoop.hbase.client.AsyncProcess: #3, table=opencga_gel_GEL_variants, attempt=23/35 failed=148ops, last exception: org.apache.hadoop.hbase.RegionTooBusyException: org.apache.hadoo
p.hbase.RegionTooBusyException: Above memstore limit, regionName=opencga_gel_GEL_variants,3\x00\x05]I\xC7,1537525912460.749d5df771185cf17224e6670b566b2a., server=gelhdp011.hdp.cor00004.cni.ukcloud.com,16020,1536828899416, memstoreSize=540
243712, blockingMemStoreSize=536870912

 */
        return metadata;
    }

}
