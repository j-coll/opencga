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

package org.opencb.opencga.storage.core.variant.annotation.annotators;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.commons.lang3.StringUtils;
import org.opencb.biodata.models.clinical.interpretation.VariantClassification;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.AdditionalAttribute;
import org.opencb.biodata.models.variant.avro.DrugResponseClassification;
import org.opencb.biodata.models.variant.avro.VariantAnnotation;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.metadata.ProjectMetadata;
import org.opencb.opencga.storage.core.variant.annotation.VariantAnnotatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.opencb.opencga.storage.core.variant.adaptors.VariantField.AdditionalAttributes.GROUP_NAME;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantField.AdditionalAttributes.VARIANT_ID;

/**
 * Created by jacobo on 9/01/15.
 */
public abstract class AbstractCellBaseVariantAnnotator extends VariantAnnotator {

    public static final String ANNOTATOR_CELLBASE_USE_CACHE = "annotator.cellbase.use_cache";
    public static final String ANNOTATOR_CELLBASE_INCLUDE = "annotator.cellbase.include";
    public static final String ANNOTATOR_CELLBASE_EXCLUDE = "annotator.cellbase.exclude";
    // Imprecise variants supported by cellbase (REST only)
    public static final String ANNOTATOR_CELLBASE_IMPRECISE_VARIANTS = "annotator.cellbase.imprecise_variants";
    public static final int CELLBASE_VARIANT_THRESHOLD = 5000;

    protected static Logger logger = LoggerFactory.getLogger(AbstractCellBaseVariantAnnotator.class);
    protected final String species;
    protected final String assembly;
    protected final String cellbaseVersion;
    protected final QueryOptions queryOptions;
    protected final boolean impreciseVariants;

    public AbstractCellBaseVariantAnnotator(StorageConfiguration storageConfiguration, ProjectMetadata projectMetadata, ObjectMap params)
            throws VariantAnnotatorException {
        super(storageConfiguration, projectMetadata, params);
//        species = toCellBaseSpeciesName(params.getString(VariantAnnotationManager.SPECIES));
//        assembly = params.getString(VariantAnnotationManager.ASSEMBLY);
        species = projectMetadata.getSpecies();
        assembly = projectMetadata.getAssembly();
        cellbaseVersion = storageConfiguration.getCellbase().getVersion();

        queryOptions = new QueryOptions();
        if (StringUtils.isNotEmpty(params.getString(ANNOTATOR_CELLBASE_INCLUDE))) {
            queryOptions.put(QueryOptions.INCLUDE, params.getString(ANNOTATOR_CELLBASE_INCLUDE));
        } else if (StringUtils.isNotEmpty(params.getString(ANNOTATOR_CELLBASE_EXCLUDE))) {
            queryOptions.put(QueryOptions.EXCLUDE, params.getString(ANNOTATOR_CELLBASE_EXCLUDE));
        }
        if (!params.getBoolean(ANNOTATOR_CELLBASE_USE_CACHE)) {
            queryOptions.append("useCache", false);
        }
//        impreciseVariants = params.getBoolean(ANNOTATOR_CELLBASE_IMPRECISE_VARIANTS, true);
        impreciseVariants = true;

        checkNotNull(cellbaseVersion, "cellbase version");
        checkNotNull(species, "species");
        checkNotNull(assembly, "assembly");

    }

    protected static void checkNotNull(String value, String name) throws VariantAnnotatorException {
        if (value == null || value.isEmpty()) {
            throw new VariantAnnotatorException("Missing defaultValue: " + name);
        }
    }

    public static String toCellBaseSpeciesName(String scientificName) {
        if (scientificName != null && scientificName.contains(" ")) {
            String[] split = scientificName.split(" ", 2);
            scientificName = (split[0].charAt(0) + split[1]).toLowerCase();
        }
        return scientificName;
    }


    @Override
    public final List<VariantAnnotation> annotate(List<Variant> variants) throws VariantAnnotatorException {
        List<Variant> nonStructuralVariations = filterStructuralVariants(variants);
        return getVariantAnnotationList(variants, annotateFiltered(nonStructuralVariations));
    }

    protected abstract List<QueryResult<VariantAnnotation>> annotateFiltered(List<Variant> variants) throws VariantAnnotatorException;

    private List<Variant> filterStructuralVariants(List<Variant> variants) {
        List<Variant> nonStructuralVariants = new ArrayList<>(variants.size());
        for (Variant variant : variants) {
            // If Variant is SV some work is needed
            // TODO:Manage larger SV variants
            if (variant.getAlternate().length() + variant.getReference().length() > CELLBASE_VARIANT_THRESHOLD) {
//                logger.info("Skip variant! {}", genomicVariant);
                logger.info("Skip variant! {}", variant.getChromosome() + ":" + variant.getStart() + ":"
                        + (variant.getReference().length() > 10
                        ? variant.getReference().substring(0, 10) + "...[" + variant.getReference().length() + "]"
                        : variant.getReference()) + ":"
                        + (variant.getAlternate().length() > 10
                        ? variant.getAlternate().substring(0, 10) + "...[" + variant.getAlternate().length() + "]"
                        : variant.getAlternate())
                );
                logger.debug("Skip variant! {}", variant);
            } else {
                nonStructuralVariants.add(variant);
            }
        }
        return nonStructuralVariants;
    }

    protected List<VariantAnnotation> getVariantAnnotationList(List<Variant> variants, List<QueryResult<VariantAnnotation>> queryResults) {
        List<VariantAnnotation> variantAnnotationList = new ArrayList<>(variants.size());
        Iterator<Variant> iterator = variants.iterator();
        if (queryResults != null) {
            for (QueryResult<VariantAnnotation> queryResult : queryResults) {
                // If the QueryResult is empty, assume that the variant was skipped
                // Check that the skipped variant matches with the expected variant
                if (queryResult.getResult().isEmpty()) {
                    Variant variant = iterator.next();
                    if (variant.toString().equals(queryResult.getId()) || variant.toStringSimple().equals(queryResult.getId())) {
                        logger.warn("Skip annotation for variant " + variant);
                    } else {
                        Variant variantId = new Variant(queryResult.getId());
                        if (!variant.getChromosome().equals(variantId.getChromosome())
                                || !variant.getStart().equals(variantId.getStart())
                                || !variant.getReference().equals(variantId.getReference())
                                || !variant.getAlternate().equals(variantId.getAlternate())) {
                            throw unexpectedVariantOrderException(variant, variantId);
                        } else {
                            logger.warn("Skip annotation for variant " + variant);
                        }
                    }
                }
                for (VariantAnnotation variantAnnotation : queryResult.getResult()) {
                    Variant variant = iterator.next();
                    if (!variant.getChromosome().equals(variantAnnotation.getChromosome())
                            || !variant.getStart().equals(variantAnnotation.getStart())
                            || !variant.getReference().equals(variantAnnotation.getReference())
                            || !variant.getAlternate().equals(variantAnnotation.getAlternate())) {
                        throw unexpectedVariantOrderException(variant, variantAnnotation.getChromosome() + ':'
                                + variantAnnotation.getStart() + ':'
                                + variantAnnotation.getReference() + ':'
                                + variantAnnotation.getAlternate());
                    }
                    if (variant.isSV()) {
                        // Variant annotation class does not have information about Structural Variations.
                        // Store the original Variant.toString as an additional attribute.
                        AdditionalAttribute additionalAttribute =
                                new AdditionalAttribute(Collections.singletonMap(VARIANT_ID.key(), variant.toString()));
                        if (variantAnnotation.getAdditionalAttributes() == null) {
                            variantAnnotation
                                    .setAdditionalAttributes(Collections.singletonMap(GROUP_NAME.key(), additionalAttribute));
                        } else {
                            variantAnnotation.getAdditionalAttributes().put(GROUP_NAME.key(), additionalAttribute);
                        }
                    }
                    variantAnnotationList.add(variantAnnotation);
                }
            }
        }
        return variantAnnotationList;
    }

    static RuntimeException unexpectedVariantOrderException(Object expected, Object actual) {
        return new IllegalArgumentException("Variants not in the expected order! "
                + "Expected '" + expected + "', " + "but got '" + actual + "'.");
    }

    public static class DrugResponseClassificationDeserializer extends JsonDeserializer<DrugResponseClassification> {

        @Override
        public DrugResponseClassification deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            String value = jsonParser.getValueAsString();
            if (value == null) {
                return null;
            } else {
                try {
                    return DrugResponseClassification.valueOf(value);
                } catch (IllegalArgumentException e) {
                    return null;
//                    switch (value.toLowerCase()) {
//                        case "responsive":
//                            return DrugResponseClassification.
//
//                    }
                }
            }
        }
    }

}
