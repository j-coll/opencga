package org.opencb.opencga.storage.core.variant.query;

import org.apache.commons.lang3.tuple.Pair;
import org.opencb.biodata.models.core.Region;
import org.opencb.biodata.models.feature.Genotype;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.metadata.StudyConfigurationManager;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryException;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam.*;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.*;
import static org.opencb.opencga.storage.core.variant.query.ParsedQuery.*;

/**
 * Created by jacobo on 25/07/18.
 */
public class VariantQueryParser {

    private final StudyConfigurationManager studyConfigurationManager;

    public VariantQueryParser(StudyConfigurationManager studyConfigurationManager) {
        this.studyConfigurationManager = studyConfigurationManager;
    }


    public VariantQuery parseQuery(Query query, QueryOptions options) {
        VariantQuery variantQuery = new VariantQuery(query);

        parseRegionFilters(query, variantQuery);

        parseVariantFilters(query, variantQuery);
        parseStudyFilters(query, variantQuery);
        parseAnnotationFilters(query, variantQuery);

        return variantQuery;
    }

    private void parseRegionFilters(Query query, VariantQuery variantQuery) {
        if (isValidParam(query, REGION)) {
            String value = query.getString(REGION.key());
            List<Region> regions = Region.parseRegions(value);
            regions = mergeRegions(regions);
            variantQuery.setRegion(regions);
        }

        VariantQueryXref variantQueryXref = parseXrefs(query);
        if (!variantQueryXref.getGenes().isEmpty()) {
            variantQuery.setGene(variantQueryXref.getGenes());
        }
        if (!variantQueryXref.getVariants().isEmpty()) {
            variantQuery.setVariant(variantQueryXref.getVariants());
        }
        if (!variantQueryXref.getIds().isEmpty()) {
            variantQuery.setId(variantQueryXref.getIds());
        }
        if (!variantQueryXref.getOtherXrefs().isEmpty()) {
            variantQuery.setXref(variantQueryXref.getOtherXrefs());
        }
    }

    private void parseVariantFilters(Query query, VariantQuery variantQuery) {

    }

    private void parseStudyFilters(Query query, VariantQuery variantQuery) {

        final StudyConfiguration defaultStudyConfiguration = getDefaultStudyConfiguration(query, null, studyConfigurationManager);

        if (isValidParam(query, STUDY)) {
            String value = query.getString(STUDY.key());
            Pair<QueryOperation, List<String>> pair = splitValue(value);
//            for (String s : pair.getValue()) {
//                studyConfigurationManager.getStudyConfiguration()
//            }
        }


        List<Filter<List<Genotype>>> filters = null;
        QueryOperation operator = null;
        if (isValidParam(query, GENOTYPE)) {
            HashMap<Object, List<String>> map = new HashMap<>();
            parseGenotypeFilter(query.getString(GENOTYPE.key()), map);
            for (Map.Entry<Object, List<String>> entry : map.entrySet()) {
                filters.add(new Filter<List<Genotype>>(entry.getKey().toString(), Operation.EQ, entry.getValue().stream()
                        .map(s->new org.opencb.biodata.models.feature.Genotype(s)).collect(Collectors.toList())));
            }
        }
        if (filters != null) {
            variantQuery.setGenotype(new FilterList<>(filters, operator));
        }

    }

    private void parseAnnotationFilters(Query query, VariantQuery variantQuery) {

        if (isValidParam(query, ANNOT_POPULATION_ALTERNATE_FREQUENCY)) {
            Pair<QueryOperation, List<String>> pair = splitValue(query.getString(ANNOT_POPULATION_ALTERNATE_FREQUENCY.key()));
            List<Filter<Float>> filters = new ArrayList<>(pair.getValue().size());
            for (String value : pair.getValue()) {
                filters.add(parsePopulationFrequencyFilter(value, ANNOT_POPULATION_ALTERNATE_FREQUENCY));
            }
            variantQuery.setPopulationFrequencyAlt(new FilterList<>(filters, pair.getKey()));
        }
        if (isValidParam(query, ANNOT_POPULATION_REFERENCE_FREQUENCY)) {
            Pair<QueryOperation, List<String>> pair = splitValue(query.getString(ANNOT_POPULATION_REFERENCE_FREQUENCY.key()));
            List<Filter<Float>> filters = new ArrayList<>(pair.getValue().size());
            for (String value : pair.getValue()) {
                filters.add(parsePopulationFrequencyFilter(value, ANNOT_POPULATION_REFERENCE_FREQUENCY));
            }
            variantQuery.setPopulationFrequencyRef(new FilterList<>(filters, pair.getKey()));
        }
        if (isValidParam(query, ANNOT_POPULATION_MINOR_ALLELE_FREQUENCY)) {
            Pair<QueryOperation, List<String>> pair = splitValue(query.getString(ANNOT_POPULATION_MINOR_ALLELE_FREQUENCY.key()));
            List<Filter<Float>> filters = new ArrayList<>(pair.getValue().size());
            for (String value : pair.getValue()) {
                filters.add(parsePopulationFrequencyFilter(value, ANNOT_POPULATION_MINOR_ALLELE_FREQUENCY));
            }
            variantQuery.setPopulationFrequencyMaf(new FilterList<>(filters, pair.getKey()));
        }

        if (isValidParam(query, ANNOT_CONSEQUENCE_TYPE)) {
            String value = query.getString(ANNOT_CONSEQUENCE_TYPE.key());
            Pair<QueryOperation, List<String>> pair = splitValue(value);

            List<Integer> intValues = pair.getValue().stream()
                    .map(VariantQueryUtils::parseConsequenceType)
                    .collect(Collectors.toList());
            variantQuery.setConsequenceTypeInt(intValues);

            List<String> values = intValues.stream()
                    .map(soAccession -> String.format("SO:%07d\n", soAccession))
                    .collect(Collectors.toList());
            variantQuery.setConsequenceType(values);
        }
    }

    private Filter<Float> parsePopulationFrequencyFilter(String value, VariantQueryParam queryParam) {
        String[] split = splitOperator(value);

        if (split.length != 3) {
            throw VariantQueryException.malformedParam(queryParam, value);
        }

        if (split[0] == null || !split[0].contains(":")) {
            throw VariantQueryException.malformedParam(queryParam, value,
                    "Missing population frequency study");
        }
        // TODO: Check study/population exists?

        return new Filter<>(
                split[0],
                parseOperation(split[1]),
                parseNumber(split[2], queryParam, value).floatValue());
    }

    private Operation parseOperation(String value) {
        switch (value) {
            case "":
            case "=":
            case "==":
                return Operation.EQ;
            case "!":
            case "!=":
            case "!==":
                return Operation.NEQ;
            case "<":
                return Operation.LT;
            case "<<":
                return Operation.LT_NULL;
            case "<=":
                return Operation.LTE;
            case "<<=":
                return Operation.LTE_NULL;
            case ">":
                return Operation.GT;
            case ">>":
                return Operation.GT_NULL;
            case ">=":
                return Operation.GTE;
            case ">>=":
                return Operation.GTE_NULL;
            case "~":
            case "~=":
                return Operation.REGEX;
            default:
                throw new VariantQueryException("Unknown operator " + value);
        }
    }

    private Number parseNumber(String value, VariantQueryParam param, String rawValue) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw VariantQueryException.malformedParam(param, rawValue, e.getMessage());
        }
    }

}
