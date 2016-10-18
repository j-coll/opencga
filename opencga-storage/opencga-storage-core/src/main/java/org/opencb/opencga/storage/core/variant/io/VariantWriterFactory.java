package org.opencb.opencga.storage.core.variant.io;

import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.VariantAvro;
import org.opencb.biodata.tools.variant.stats.writer.VariantStatsPopulationFrequencyExporter;
import org.opencb.biodata.tools.variant.stats.writer.VariantStatsTsvExporter;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.io.DataWriter;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.opencb.opencga.storage.core.variant.adaptors.VariantSourceDBAdaptor;
import org.opencb.opencga.storage.core.variant.io.avro.VariantAvroWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor.VariantQueryParams.RETURNED_SAMPLES;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor.VariantQueryParams.RETURNED_STUDIES;

/**
 * Created on 17/10/16.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class VariantWriterFactory {
    public enum VariantOutputFormat {
        VCF(false),
        JSON,
        AVRO,
        STATS(false),
        CELLBASE;

        private final boolean multiStudy;

        VariantOutputFormat() {
            this.multiStudy = true;
        }

        VariantOutputFormat(boolean multiStudy) {
            this.multiStudy = multiStudy;
        }

        public boolean isMultiStudyOutput() {
            return multiStudy;
        }

        public static boolean isGzip(String value) {
            return value.endsWith(".gz");
        }

        public static boolean isSnappy(String value) {
            return value.endsWith(".snappy");
        }

        public static VariantOutputFormat safeValueOf(String value) {
            try {
                return create(value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public static VariantOutputFormat create(String value) {
            int index = value.indexOf(".");
            if (index >= 0) {
                value = value.substring(0, index);
            }
            return VariantOutputFormat.valueOf(value.toUpperCase());
        }

    }

    public DataWriter<Variant> createVariantsDataWriter(String outputFormat, OutputStream outputStream,
                                 Query query, QueryOptions queryOptions,
                                 StudyConfiguration studyConfiguration,
                                 VariantDBAdaptor dbAdaptor) {

        DataWriter<Variant> exporter;
        switch (VariantOutputFormat.create(outputFormat)) {
            case VCF:
//                StudyConfigurationManager studyConfigurationManager = variantDBAdaptor.getStudyConfigurationManager();
//                Map<Long, List<Sample>> samplesMetadata = variantFetcher.getSamplesMetadata(studyId, query, queryOptions, sessionId);
//                QueryResult<StudyConfiguration> studyConfigurationResult = studyConfigurationManager.getStudyConfiguration(
//                        query.getAsStringList(RETURNED_STUDIES.key()).get(0), null);
//                studyConfiguration = variantFetcher
//                        .getStudyConfiguration(query.getAsIntegerList(RETURNED_STUDIES.key()).get(0), null, sessionId);

                if (studyConfiguration != null) {
                    // Samples to be returned
                    if (query.containsKey(RETURNED_SAMPLES.key())) {
                        queryOptions.put(RETURNED_SAMPLES.key(), query.get(RETURNED_SAMPLES.key()));
                    }


//                    if (cliOptions.annotations != null) {
//                        queryOptions.add("annotations", cliOptions.annotations);
//                    }

//                    long studyId = variantFetcher.getMainStudyId(query, sessionId);
                    VariantSourceDBAdaptor sourceDBAdaptor = dbAdaptor.getVariantSourceDBAdaptor();
                    exporter = new VariantVcfExporter(studyConfiguration, sourceDBAdaptor, outputStream, queryOptions);
                } else {
                    throw new IllegalArgumentException("No study found named " + query.getAsStringList(RETURNED_STUDIES.key()).get(0));
                }
                break;
            case JSON:
                // we know that it is JSON, otherwise we have not reached this point
                exporter = batch -> {
                    batch.forEach(variant -> {
                        try {
                            outputStream.write(variant.toJson().getBytes());
                            outputStream.write('\n');
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    return true;
                };

                break;
            case AVRO:
                String codecName = "";
                if (VariantWriterFactory.VariantOutputFormat.isGzip(outputFormat)) {
                    codecName = "gzip";
                }
                if (outputFormat.endsWith("snappy")) {
                    codecName = "snappy";
                }
                exporter = new VariantAvroWriter(VariantAvro.getClassSchema(), codecName, outputStream);

                break;
            case STATS:
//                studyConfiguration = variantFetcher
//                        .getStudyConfiguration(query.getAsIntegerList(RETURNED_STUDIES.key()).get(0), null, sessionId);
                List<String> cohorts = new ArrayList<>(studyConfiguration.getCohortIds().keySet());
                cohorts.sort(String::compareTo);

                exporter = new VariantStatsTsvExporter(outputStream, studyConfiguration.getStudyName(), cohorts);

                break;
            case CELLBASE:
                exporter = new VariantStatsPopulationFrequencyExporter(outputStream);
                break;
            default:
                throw new IllegalArgumentException("Unknown output format " + outputFormat);
        }

        return exporter;
    }
}
