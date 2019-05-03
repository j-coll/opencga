package org.opencb.opencga.storage.core.variant;

import org.junit.Ignore;
import org.junit.Test;
import org.opencb.biodata.models.variant.VariantFileMetadata;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.storage.core.StoragePipelineResult;
import org.opencb.opencga.storage.core.metadata.models.StudyMetadata;
import org.opencb.opencga.storage.core.variant.annotation.VariantAnnotationManager;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opencb.opencga.core.common.UriUtils.dirName;
import static org.opencb.opencga.core.common.UriUtils.fileName;

/**
 * Created on 03/05/19.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
@Ignore
public abstract class VariantStorageEngineExternalIOManagerTest extends VariantStorageBaseTest {


    @Test
    public void basicIndex() throws Exception {
        clearDB(DB_NAME);
        StudyMetadata studyMetadata = newStudyMetadata();
//        StoragePipelineResult etlResult = runDefaultETL(smallInputUri, variantStorageEngine, studyMetadata,
//                new ObjectMap(VariantStorageEngine.Options.TRANSFORM_FORMAT.key(), "json"));

        StoragePipelineResult etlResult = runETL(variantStorageEngine,
                smallInputUri,
//                URI.create("test://localhost/").resolve(dirName(smallInputUri)).resolve(fileName(smallInputUri)),
//                outputUri,
//                URI.create("https://cellbase.blob.core.windows.net/test/"),
                URI.create("test://localhost/").resolve(dirName(outputUri)),
                new ObjectMap(VariantStorageEngine.Options.TRANSFORM_FORMAT.key(), "json")
                        .append(VariantStorageEngine.Options.STUDY.key(), STUDY_NAME)
                        .append(VariantStorageEngine.Options.CALCULATE_STATS.key(), true)
                        .append(VariantStorageEngine.Options.ANNOTATE.key(), true)
                        .append(VariantAnnotationManager.SPECIES, "hsapiens")
                        .append(VariantAnnotationManager.ASSEMBLY, "grch37"),
                true, true, true);

        assertTrue("Incorrect transform file extension " + etlResult.getTransformResult() + ". Expected 'variants.json.gz'",
                fileName(etlResult.getTransformResult()).endsWith("variants.json.gz"));
        VariantFileMetadata fileMetadata = variantStorageEngine.getVariantReaderUtils().readVariantFileMetadata(etlResult.getTransformResult());
        assertEquals(1, metadataManager.getIndexedFiles(studyMetadata.getId()).size());
//        checkTransformedVariants(etlResult.getTransformResult(), studyMetadata);
//        checkLoadedVariants(variantStorageEngine.getDBAdaptor(), studyMetadata, true, false, true, getExpectedNumLoadedVariants(fileMetadata));
    }


}
