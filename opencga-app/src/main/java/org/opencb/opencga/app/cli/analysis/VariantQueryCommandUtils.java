/*
 * Copyright 2015-2016 OpenCB
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

package org.opencb.opencga.app.cli.analysis;

import com.beust.jcommander.ParameterException;
import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.utils.FileUtils;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.opencb.opencga.storage.core.variant.io.VariantVcfExporter;
import org.opencb.opencga.storage.core.variant.io.VariantWriterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static org.opencb.opencga.storage.core.variant.io.VariantWriterFactory.VariantOutputFormat.*;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor.VariantQueryParams.*;

/**
 * Created by imedina on 30/12/15.
 */
public class VariantQueryCommandUtils {

    private static Logger logger = LoggerFactory.getLogger("org.opencb.opencga.storage.app.cli.client.VariantQueryCommandUtils");

    public static Query parseQuery(AnalysisCliOptionsParser.QueryVariantCommandOptions queryVariantsOptions, Map<Long, String> studyIds)
            throws Exception {
        Query query = new Query();

        /*
         * Parse Variant parameters
         */
        if (queryVariantsOptions.region != null && !queryVariantsOptions.region.isEmpty()) {
            query.put(REGION.key(), queryVariantsOptions.region);
        } else if (queryVariantsOptions.regionFile != null && !queryVariantsOptions.regionFile.isEmpty()) {
            Path gffPath = Paths.get(queryVariantsOptions.regionFile);
            FileUtils.checkFile(gffPath);
            String regionsFromFile = Files.readAllLines(gffPath).stream().map(line -> {
                String[] array = line.split("\t");
                return new String(array[0].replace("chr", "") + ":" + array[3] + "-" + array[4]);
            }).collect(Collectors.joining(","));
            query.put(REGION.key(), regionsFromFile);
        }

        addParam(query, ID, queryVariantsOptions.id);
        addParam(query, GENE, queryVariantsOptions.gene);
        addParam(query, TYPE, queryVariantsOptions.type);


        List studies = new LinkedList<>();
        if (StringUtils.isNotEmpty(queryVariantsOptions.study)) {
            query.put(STUDIES.key(), queryVariantsOptions.study);
            for (String study : queryVariantsOptions.study.split(",|;")) {
                if (!study.startsWith("!")) {
                    studies.add(study);
                }
            }
        } else {
            studies = new ArrayList<>(studyIds.keySet());
        }

        // If the studies to be returned is empty then we return the studies being queried
        if (StringUtils.isNotEmpty(queryVariantsOptions.returnStudy)) {
//            query.put(RETURNED_STUDIES.key(), Arrays.asList(queryVariantsOptions.returnStudy.split(",")));
            List<String> list = new ArrayList<>();
            Collections.addAll(list, queryVariantsOptions.returnStudy.split(","));
            query.put(RETURNED_STUDIES.key(), list);
        } else {
            if (!studies.isEmpty()) {
                query.put(RETURNED_STUDIES.key(), studies);
            }
        }

        addParam(query, FILES, queryVariantsOptions.file);
        addParam(query, GENOTYPE, queryVariantsOptions.sampleGenotype);
        if (queryVariantsOptions.returnSample != null) {
            if (queryVariantsOptions.returnSample.isEmpty() || queryVariantsOptions.returnSample.equals(".")) {
                query.put(RETURNED_SAMPLES.key(), Collections.emptyList());
            } else {
                query.put(RETURNED_SAMPLES.key(), queryVariantsOptions.returnSample);
            }
        }
        addParam(query, UNKNOWN_GENOTYPE, queryVariantsOptions.unknownGenotype);


        /**
         * Annotation parameters
         */
        addParam(query, ANNOT_CONSEQUENCE_TYPE, queryVariantsOptions.consequenceType);
        addParam(query, ANNOT_BIOTYPE, queryVariantsOptions.biotype);
        addParam(query, ANNOT_POPULATION_ALTERNATE_FREQUENCY, queryVariantsOptions.populationFreqs);
        addParam(query, ANNOT_POPULATION_MINOR_ALLELE_FREQUENCY, queryVariantsOptions.populationMaf);
        addParam(query, ANNOT_CONSERVATION, queryVariantsOptions.conservation);
        addParam(query, ANNOT_TRANSCRIPTION_FLAGS, queryVariantsOptions.flags);
        addParam(query, ANNOT_GENE_TRAITS_ID, queryVariantsOptions.geneTraitId);
        addParam(query, ANNOT_GENE_TRAITS_NAME, queryVariantsOptions.geneTraitName);
        addParam(query, ANNOT_HPO, queryVariantsOptions.hpo);
        addParam(query, ANNOT_GO, queryVariantsOptions.go);
        addParam(query, ANNOT_EXPRESSION, queryVariantsOptions.expression);
        addParam(query, ANNOT_PROTEIN_KEYWORDS, queryVariantsOptions.proteinKeywords);
        addParam(query, ANNOT_DRUG, queryVariantsOptions.drugs);

        if (StringUtils.isNoneEmpty(queryVariantsOptions.proteinSubstitution)) {
            query.put(ANNOT_PROTEIN_SUBSTITUTION.key(), queryVariantsOptions.proteinSubstitution);
        }


        /*
         * Stats parameters
         */
        if (queryVariantsOptions.stats != null && !queryVariantsOptions.stats.isEmpty()) {
            Set<String> acceptedStatKeys = new HashSet<>(Arrays.asList(STATS_MAF.key(),
                    STATS_MGF.key(),
                    MISSING_ALLELES.key(),
                    MISSING_GENOTYPES.key()));

            for (String stat : queryVariantsOptions.stats.split(",")) {
                int index = stat.indexOf("<");
                index = index >= 0 ? index : stat.indexOf("!");
                index = index >= 0 ? index : stat.indexOf("~");
                index = index >= 0 ? index : stat.indexOf("<");
                index = index >= 0 ? index : stat.indexOf(">");
                index = index >= 0 ? index : stat.indexOf("=");
                if (index < 0) {
                    throw new UnsupportedOperationException("Unknown stat filter operation: " + stat);
                }
                String name = stat.substring(0, index);
                String cond = stat.substring(index);

                if (acceptedStatKeys.contains(name)) {
                    query.put(name, cond);
                } else {
                    throw new UnsupportedOperationException("Unknown stat filter name: " + name);
                }
                logger.info("Parsed stat filter: {} {}", name, cond);
            }
        }

        addParam(query, STATS_MAF, queryVariantsOptions.maf);
        addParam(query, STATS_MGF, queryVariantsOptions.mgf);
        addParam(query, MISSING_ALLELES, queryVariantsOptions.missingAlleleCount);
        addParam(query, MISSING_GENOTYPES, queryVariantsOptions.missingGenotypeCount);


        boolean returnVariants = !queryVariantsOptions.count && StringUtils.isEmpty(queryVariantsOptions.groupBy)
                && StringUtils.isEmpty(queryVariantsOptions.rank);


        VariantWriterFactory.VariantOutputFormat of = VCF;
        if (StringUtils.isNotEmpty(queryVariantsOptions.outputFormat)) {
            of = VariantWriterFactory.VariantOutputFormat.safeValueOf(queryVariantsOptions.outputFormat);
            if (of == null) {
                throw variantFormatNotSupported(queryVariantsOptions.outputFormat);
            }
        }

        if (returnVariants && !of.isMultiStudyOutput()) {
            int returnedStudiesSize = query.getAsStringList(RETURNED_STUDIES.key()).size();
            if (returnedStudiesSize == 0 && studies.size() == 1) {
                query.put(RETURNED_STUDIES.key(), studies.get(0));
            } else if (returnedStudiesSize == 0 && studyIds.size() != 1 //If there are no returned studies, and there are more than one study
                    || returnedStudiesSize > 1) {     // Or is required more than one returned study
                throw new Exception("Only one study is allowed when returning " + of + ", please use '--return-study' to select the returned "
                        + "study. Available studies: " + studyIds);
            } else {
                if (returnedStudiesSize == 0) {    //If there were no returned studies, set the study existing one
                    query.put(RETURNED_STUDIES.key(), studyIds.get(0));
                }
            }
        }

        return query;
    }

    public static QueryOptions parseQueryOptions(AnalysisCliOptionsParser.QueryVariantCommandOptions queryVariantsOptions) {
        QueryOptions queryOptions = new QueryOptions(new HashMap<>(queryVariantsOptions.commonOptions.params));

        if (StringUtils.isNotEmpty(queryVariantsOptions.include)) {
            queryOptions.add(QueryOptions.INCLUDE, queryVariantsOptions.include);
        }

        if (StringUtils.isNotEmpty(queryVariantsOptions.exclude)) {
            queryOptions.add(QueryOptions.EXCLUDE, queryVariantsOptions.exclude + ",_id");
        }
//        else {
//            queryOptions.put("exclude", "_id");
//        }

        if (queryVariantsOptions.skip > 0) {
            queryOptions.add(QueryOptions.SKIP, queryVariantsOptions.skip);
        }

        if (queryVariantsOptions.limit > 0) {
            queryOptions.add(QueryOptions.LIMIT, queryVariantsOptions.limit);
        }

        if (queryVariantsOptions.count) {
            queryOptions.add("count", true);
        }

        if (queryVariantsOptions.sort) {
            queryOptions.add(QueryOptions.SORT, true);
        }

        return queryOptions;
    }

    public static OutputStream getOutputStream(AnalysisCliOptionsParser.QueryVariantCommandOptions queryVariantsOptions) throws IOException {
        /*
         * Output parameters
         */
        boolean gzip = true;
        VariantWriterFactory.VariantOutputFormat outputFormat;
        if (StringUtils.isNotEmpty(queryVariantsOptions.outputFormat)) {
            outputFormat = VariantWriterFactory.VariantOutputFormat.safeValueOf(queryVariantsOptions.outputFormat);
            if (outputFormat == null) {
                throw variantFormatNotSupported(queryVariantsOptions.outputFormat);
            } else {
                gzip = VariantWriterFactory.VariantOutputFormat.isGzip(queryVariantsOptions.outputFormat);
            }
        } else {
            outputFormat = VCF;
        }

        // output format has priority over output name
        OutputStream outputStream;
        if (isStandardOutput(queryVariantsOptions)) {
            // Unclosable OutputStream
            outputStream = new VariantVcfExporter.UnclosableOutputStream(System.out);
        } else {
            if (gzip && !queryVariantsOptions.output.endsWith(".gz")) {
                queryVariantsOptions.output += ".gz";
            }
            outputStream = new FileOutputStream(queryVariantsOptions.output);
            logger.debug("writing to %s", queryVariantsOptions.output);
        }

        // If compressed a GZip output stream is used
        if (gzip && outputFormat != AVRO) {
            outputStream = new GZIPOutputStream(outputStream);
        }

        logger.debug("using %s output stream", gzip ? "gzipped" : "plain");

        return outputStream;
    }

    public static boolean isStandardOutput(AnalysisCliOptionsParser.QueryVariantCommandOptions queryVariantsOptions) {
        return queryVariantsOptions.output == null || queryVariantsOptions.output.isEmpty();
    }

    public static ParameterException variantFormatNotSupported(String outputFormat) {
        logger.error("Format '{}' not supported", outputFormat);
        return new ParameterException("Format '" + outputFormat + "' not supported");
    }

    private static void addParam(Query query, VariantDBAdaptor.VariantQueryParams key, String value) {
        if (StringUtils.isNotEmpty(value)) {
            query.put(key.key(), value);
        }
    }

}
