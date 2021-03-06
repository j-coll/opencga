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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.VariantAnnotation;
import org.opencb.cellbase.core.api.DBAdaptorFactory;
import org.opencb.cellbase.core.common.GitRepositoryState;
import org.opencb.cellbase.core.config.*;
import org.opencb.cellbase.core.variant.annotation.VariantAnnotationCalculator;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.metadata.models.ProjectMetadata;
import org.opencb.opencga.storage.core.variant.annotation.VariantAnnotatorException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Created on 23/11/16.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class CellBaseDirectVariantAnnotator extends AbstractCellBaseVariantAnnotator {

    private VariantAnnotationCalculator variantAnnotationCalculator = null;
    private final DBAdaptorFactory dbAdaptorFactory;

    public CellBaseDirectVariantAnnotator(StorageConfiguration storageConfiguration, ProjectMetadata projectMetadata, ObjectMap options)
            throws VariantAnnotatorException {
        super(storageConfiguration, projectMetadata, options);

        CellBaseConfiguration cellBaseConfiguration = new CellBaseConfiguration();
        cellBaseConfiguration.setVersion(cellbaseVersion);

        // Database connection details
        Databases databases = new Databases();
        DatabaseCredentials databaseCredentials = new DatabaseCredentials();
        List<String> hosts = storageConfiguration.getCellbase().getDatabase().getHosts();
        String hostsString = StringUtils.join(hosts, ",");
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

        dbAdaptorFactory = new org.opencb.cellbase.lib.impl.MongoDBAdaptorFactory(cellBaseConfiguration);
        variantAnnotationCalculator =
                new VariantAnnotationCalculator(species, assembly, dbAdaptorFactory);

        logger.info("Annotating with Cellbase dbAdaptor. host '{}', version '{}', species '{}', assembly '{}'",
                hostsString, cellbaseVersion, species, assembly);

    }

    @Override
    protected List<QueryResult<VariantAnnotation>> annotateFiltered(List<Variant> variants) throws VariantAnnotatorException {

        List<QueryResult<VariantAnnotation>> queryResultList;
        try {
            queryResultList = variantAnnotationCalculator.getAnnotationByVariantList(variants, queryOptions);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VariantAnnotatorException("Unable to calculate annotation", e);
        } catch (ExecutionException e) {
            throw new VariantAnnotatorException("Unable to calculate annotation", e);
        }

        return queryResultList;

    }

    @Override
    public ProjectMetadata.VariantAnnotatorProgram getVariantAnnotatorProgram() {
        return new ProjectMetadata.VariantAnnotatorProgram(
                "CellBase (OpenCB)",
                GitRepositoryState.get().getBuildVersion(), GitRepositoryState.get().getCommitId()
        );
    }

    @Override
    public List<ObjectMap> getVariantAnnotatorSourceVersion() throws IOException {
        QueryResult queryResult = dbAdaptorFactory.getMetaDBAdaptor(species, assembly).nativeGet(new Query(), new QueryOptions());

        List<ObjectMap> objectMaps = new ArrayList<>(queryResult.getResult().size());
        ObjectMapper objectMapper = new ObjectMapper();
        for (Object o : queryResult.getResult()) {
            ObjectMap objectMap = objectMapper.readValue(objectMapper.writeValueAsString(o), ObjectMap.class);
            objectMaps.add(objectMap);
        }

        return objectMaps;
    }

}
