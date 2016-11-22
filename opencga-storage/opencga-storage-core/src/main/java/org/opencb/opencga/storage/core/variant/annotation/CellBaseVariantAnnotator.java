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

package org.opencb.opencga.storage.core.variant.annotation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.VariantAnnotation;
import org.opencb.cellbase.client.config.ClientConfiguration;
import org.opencb.cellbase.client.rest.CellBaseClient;
import org.opencb.cellbase.core.api.DBAdaptorFactory;
import org.opencb.cellbase.core.config.CellBaseConfiguration;
import org.opencb.cellbase.core.config.Databases;
import org.opencb.cellbase.core.config.Species;
import org.opencb.cellbase.core.config.SpeciesProperties;
import org.opencb.cellbase.core.variant.annotation.VariantAnnotationCalculator;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.QueryResponse;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.variant.io.json.mixin.VariantAnnotationMixin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

//import org.opencb.commons.utils.StringUtils;

//import org.opencb.cellbase.core.db.DBAdaptorFactory;
//import org.opencb.cellbase.core.db.api.variation.VariantAnnotationDBAdaptor;
//import org.opencb.cellbase.core.db.api.variation.VariationDBAdaptor;

/**
 * Created by jacobo on 9/01/15.
 */
public class CellBaseVariantAnnotator extends VariantAnnotator {

    private static final int TIMEOUT = 10000;
    private final JsonFactory factory;
    private final org.opencb.commons.datastore.core.QueryOptions queryOptions =
            new org.opencb.commons.datastore.core.QueryOptions("post", true).append("exclude", "expression");
    private VariantAnnotationCalculator variantAnnotationCalculator = null;
    private CellBaseClient cellBaseClient = null;
    private ObjectMapper jsonObjectMapper;

    protected static Logger logger = LoggerFactory.getLogger(CellBaseVariantAnnotator.class);

    public CellBaseVariantAnnotator(StorageConfiguration storageConfiguration, ObjectMap options) throws VariantAnnotatorException {
        this(storageConfiguration, options, true);
    }

    public CellBaseVariantAnnotator(StorageConfiguration storageConfiguration, ObjectMap options, boolean restConnection)
            throws VariantAnnotatorException {
        super(storageConfiguration, options);

        this.factory = new JsonFactory();
        this.jsonObjectMapper = new ObjectMapper(factory);
        jsonObjectMapper.addMixIn(VariantAnnotation.class, VariantAnnotationMixin.class);
        jsonObjectMapper.configure(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS, true);
        String species = options.getString(VariantAnnotationManager.SPECIES);
        String assembly = options.getString(VariantAnnotationManager.ASSEMBLY);
        String cellbaseVersion = storageConfiguration.getCellbase().getVersion();
        List<String> hosts = storageConfiguration.getCellbase().getHosts();
        if (hosts.isEmpty()) {
            throw new VariantAnnotatorException("Missing defaultValue \"CellBase Hosts\"");
        }

        checkNotNull(cellbaseVersion, "cellbase version");
        checkNotNull(species, "species");
        checkNotNull(assembly, "assembly");

        if (restConnection) {
            String cellbaseRest = hosts.get(0);
            checkNotNull(cellbaseRest, "cellbase hosts");
            ClientConfiguration clientConfiguration = storageConfiguration.getCellbase().toClientConfiguration();
            clientConfiguration.getRest().setTimeout(TIMEOUT);
            CellBaseClient cellBaseClient;
            cellBaseClient = new CellBaseClient(species, clientConfiguration);
            this.cellBaseClient = cellBaseClient;
        } else {
            CellBaseConfiguration cellBaseConfiguration = new CellBaseConfiguration();
            cellBaseConfiguration.setVersion(cellbaseVersion);
            // Database connection details
            Databases databases = new Databases();
            org.opencb.cellbase.core.config.DatabaseCredentials databaseCredentials
                    = new org.opencb.cellbase.core.config.DatabaseCredentials();
            String hostsString = StringUtils.join(storageConfiguration.getCellbase().getDatabase().getHosts(), ",");
            checkNotNull(hostsString, "cellbase database host");
            databaseCredentials.setHost(hostsString);
            databaseCredentials.setPassword(storageConfiguration.getCellbase().getDatabase().getPassword());
            databaseCredentials.setUser(storageConfiguration.getCellbase().getDatabase().getUser());
            databaseCredentials.setOptions(storageConfiguration.getCellbase().getDatabase().getOptions());
            databases.setMongodb(databaseCredentials);
            cellBaseConfiguration.setDatabases(databases);

            // Species details
            Species cellbaseSpecies = new Species();
            cellbaseSpecies.setId(species);
            // Assembly details
            Species.Assembly cellbaseAssembly = new Species.Assembly();
            cellbaseAssembly.setName(assembly);
            cellbaseSpecies.setAssemblies(Collections.singletonList(cellbaseAssembly));
            // The species is set within the vertebrates although it doesn't really matter, it just needs to be
            // set somewhere within the species section so that the mongoDBAdaptorFactory is able to find the object
            // matching the "species" and "assembly" provided
            SpeciesProperties speciesProperties = new SpeciesProperties();
            speciesProperties.setVertebrates(Collections.singletonList(cellbaseSpecies));
            cellBaseConfiguration.setSpecies(speciesProperties);

            DBAdaptorFactory dbAdaptorFactory
                    = new org.opencb.cellbase.lib.impl.MongoDBAdaptorFactory(cellBaseConfiguration);
            variantAnnotationCalculator =
                    new VariantAnnotationCalculator(species, assembly, dbAdaptorFactory);
        }
    }

    private static void checkNotNull(String value, String name) throws VariantAnnotatorException {
        if (value == null || value.isEmpty()) {
            throw new VariantAnnotatorException("Missing defaultValue: " + name);
        }
    }


    /////// CREATE ANNOTATION - AUX METHODS

    @Override
    public List<VariantAnnotation> annotate(List<Variant> variants) throws IOException {

        List<Variant> nonStructuralVariations = filterStructuralVariants(variants);

        if (cellBaseClient != null) {
            return getVariantAnnotationsREST(nonStructuralVariations);
        } else {
            return getVariantAnnotationsDbAdaptor(nonStructuralVariations);
        }
    }

    List<Variant> filterStructuralVariants(List<Variant> variants) {
        List<Variant> nonStructuralVariants = new ArrayList<>(variants.size());
        for (Variant variant : variants) {
            // If Variant is SV some work is needed
            if (variant.getAlternate().length() + variant.getReference().length() > Variant.SV_THRESHOLD * 2) { // TODO: Manage SV variants
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

    private List<VariantAnnotation> getVariantAnnotationsREST(List<Variant> variants) throws IOException {

        QueryResponse<VariantAnnotation> queryResponse
                = cellBaseClient.getVariationClient()
                .getAnnotations(variants.stream().map(Variant::toString).collect(Collectors.toList()),
                        queryOptions, true);

        return getVariantAnnotationList(variants, queryResponse.getResponse());
    }

    private List<VariantAnnotation> getVariantAnnotationList(List<Variant> variants, List<QueryResult<VariantAnnotation>> queryResults) {
        List<VariantAnnotation> variantAnnotationList = new ArrayList<>(variants.size());
        for (QueryResult<VariantAnnotation> queryResult : queryResults) {
            variantAnnotationList.addAll(queryResult.getResult());
        }
        return variantAnnotationList;
    }

    private List<VariantAnnotation> getVariantAnnotationsDbAdaptor(List<Variant> genomicVariantList) throws IOException {

        List<QueryResult<VariantAnnotation>> queryResultList = null;
        try {
            queryResultList = variantAnnotationCalculator.getAnnotationByVariantList(genomicVariantList, queryOptions);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return getVariantAnnotationList(genomicVariantList, queryResultList);

    }

    /////// LOAD ANNOTATION

}
