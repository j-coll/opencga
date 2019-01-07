package org.opencb.opencga.storage.hadoop.variant.index.sample;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.Put;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.opencga.storage.core.metadata.StudyConfigurationManager;
import org.opencb.opencga.storage.hadoop.utils.HBaseManager;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.HadoopVariantStorageEngine;
import org.opencb.opencga.storage.hadoop.variant.index.annotation.AnnotationIndexDBAdaptor;
import org.opencb.opencga.storage.hadoop.variant.index.sample.SampleIndexConverter;
import org.opencb.opencga.storage.hadoop.variant.index.sample.SampleIndexDBAdaptor;
import org.opencb.opencga.storage.hadoop.variant.index.sample.SampleIndexDBLoader;
import org.opencb.opencga.storage.hadoop.variant.utils.HBaseVariantTableNameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Created by jacobo on 04/01/19.
 */
public class SampleIndexAnnotationLoader {

    private final HBaseManager hBaseManager;
    private final HBaseVariantTableNameGenerator tableNameGenerator;
    private final AnnotationIndexDBAdaptor annotationIndexDBAdaptor;
    private final SampleIndexDBAdaptor sampleDBAdaptor;
    private final byte[] family;
    private Logger logger = LoggerFactory.getLogger(SampleIndexAnnotationLoader.class);

    public SampleIndexAnnotationLoader(GenomeHelper helper, HBaseManager hBaseManager,
                                       HBaseVariantTableNameGenerator tableNameGenerator, StudyConfigurationManager studyConfigurationManager) {
        this.hBaseManager = hBaseManager;
        this.tableNameGenerator = tableNameGenerator;
        this.annotationIndexDBAdaptor = new AnnotationIndexDBAdaptor(hBaseManager, tableNameGenerator.getAnnotationIndexTableName());
        this.sampleDBAdaptor = new SampleIndexDBAdaptor(helper, hBaseManager, tableNameGenerator, studyConfigurationManager);
        family = helper.getColumnFamily();
    }

    public void updateSampleAnnotation(int studyId, List<Integer> samples) throws IOException {
        String sampleIndexTableName = tableNameGenerator.getSampleIndexTableName(studyId);
        Map<Integer, Iterator<Map<String, List<Variant>>>> sampleIterators = new HashMap<>(samples.size());

        for (Integer sample : samples) {
            sampleIterators.put(sample, sampleDBAdaptor.rawIterator(studyId, sample));
        }

        BufferedMutator mutator = hBaseManager.getConnection().getBufferedMutator(TableName.valueOf(sampleIndexTableName));

        String chromosome = "";
        int start = -1;
        int end = -1;
        List<Pair<Variant, Byte>> annotationMasks = null;
        do {
            for (Map.Entry<Integer, Iterator<Map<String, List<Variant>>>> sampleIteratorPair : sampleIterators.entrySet()) {
                Iterator<Map<String, List<Variant>>> sampleIterator = sampleIteratorPair.getValue();
                Integer sampleId = sampleIteratorPair.getKey();
                if (sampleIterator.hasNext()) {
                    Map<String, List<Variant>> next = sampleIterator.next();

                    Variant firstVariant = next.values().iterator().next().get(0);
                    if (annotationMasks == null
                            || !chromosome.equals(firstVariant.getChromosome())
                            || firstVariant.getStart() < start
                            || firstVariant.getStart() > end) {
                        chromosome = firstVariant.getChromosome();
                        start = firstVariant.getStart() - firstVariant.getStart() % SampleIndexDBLoader.BATCH_SIZE;
                        end = start + SampleIndexDBLoader.BATCH_SIZE;
                        annotationMasks = annotationIndexDBAdaptor.get(chromosome, start, end);
                    }

                    byte[] rk = SampleIndexConverter.toRowKey(sampleId, chromosome, start);
                    Put put = new Put(rk);

                    for (Map.Entry<String, List<Variant>> entry : next.entrySet()) {
                        String gt = entry.getKey();
                        List<Variant> variantsToAnnotate = entry.getValue();
                        if (!isAnnotatedGenotype(gt)) {
                            continue;
                        }

                        ListIterator<Pair<Variant, Byte>> iterator = annotationMasks.listIterator();
                        byte[] annotations = new byte[variantsToAnnotate.size()];
                        int i = 0;
                        int missingVariants = 0;
                        // Assume both lists are ordered, and "variantsToAnnotate" is fully contained in "annotationMasks"
                        for (Variant variantToAnnotate : variantsToAnnotate) {
                            while (iterator.hasNext()) {
                                Pair<Variant, Byte> annotationPair = iterator.next();
                                if (annotationPair.getKey().sameGenomicVariant(variantToAnnotate)) {
                                    annotations[i] = annotationPair.getValue();
                                    i++;
                                    break;
                                } else if (annotationPair.getKey().getStart() > variantToAnnotate.getStart()) {
                                    logger.error("Missing variant to annotate " + variantToAnnotate);
                                    iterator.previous();
                                    annotations[i] = (byte) 0xFF;
                                    i++;
                                    missingVariants++;
                                    break;
                                }
                            }
                        }
//                        if (missingVariants > 0) {
//                            // TODO: What if a variant is not annotated?
//                            throw new IllegalStateException("Error annotating batch. " + missingVariants + " missing variants");
//                        }

                        put.addColumn(family, SampleIndexConverter.toAnnotationColumn(gt), annotations);
                    }
                    mutator.mutate(put);
                }
            }

            // Remove exhausted iterators
            sampleIterators.entrySet().removeIf(e -> !e.getValue().hasNext());
        } while (!sampleIterators.isEmpty());

        mutator.close();
    }

    public boolean isAnnotatedGenotype(String gt) {
        return gt.contains("1");
    }


}
