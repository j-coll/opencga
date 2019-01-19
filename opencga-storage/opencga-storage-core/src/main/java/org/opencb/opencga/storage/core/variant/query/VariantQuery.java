package org.opencb.opencga.storage.core.variant.query;

import org.opencb.biodata.models.core.Region;
import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.commons.datastore.core.Query;

import java.util.List;

import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.QueryOperation.OR;

/**
 * Created by jacobo on 25/07/18.
 */
public class VariantQuery extends ParsedQuery {

    private final Query originalQuery;

    // Variant filters
    private List<Region> region;
    private List<String> gene;
    private List<String> id;
    private List<Variant> variant;
    private List<String> xref;

    //
    private List<VariantType> type;

    // Study filters
    private ParsedQuery.FilterList<String> study;
    private ParsedQuery.FilterList<List<Genotype>> genotype;


    // Annotation filters
    private ParsedQuery.FilterList<Float> proteinSubstitution;
    private ParsedQuery.FilterList<Float> conservation;
    private ParsedQuery.FilterList<Float> functionalScore;
    private ParsedQuery.FilterList<Float> populationFrequencyMaf;
    private ParsedQuery.FilterList<Float> populationFrequencyRef;
    private ParsedQuery.FilterList<Float> populationFrequencyAlt;
    private SimpleFilterList<Integer> consequenceTypeInt;
    private SimpleFilterList<String> consequenceType;

    public VariantQuery(Query originalQuery) {
        this.originalQuery = originalQuery;
    }


    public Query getOriginalQuery() {
        return originalQuery;
    }

    public List<Region> getRegion() {
        return region;
    }

    public VariantQuery setRegion(List<Region> region) {
        this.region = region;
        return this;
    }

    public List<String> getGene() {
        return gene;
    }

    public VariantQuery setGene(List<String> gene) {
        this.gene = gene;
        return this;
    }

    public List<String> getId() {
        return id;
    }

    public VariantQuery setId(List<String> id) {
        this.id = id;
        return this;
    }

    public List<Variant> getVariant() {
        return variant;
    }

    public VariantQuery setVariant(List<Variant> variant) {
        this.variant = variant;
        return this;
    }

    public List<String> getXref() {
        return xref;
    }

    public VariantQuery setXref(List<String> xref) {
        this.xref = xref;
        return this;
    }

    public FilterList<String> getStudy() {
        return study;
    }

    public VariantQuery setStudy(FilterList<String> study) {
        this.study = study;
        return this;
    }

    public FilterList<Float> getProteinSubstitution() {
        return proteinSubstitution;
    }

    public VariantQuery setProteinSubstitution(FilterList<Float> proteinSubstitution) {
        this.proteinSubstitution = proteinSubstitution;
        return this;
    }

    public FilterList<Float> getConservation() {
        return conservation;
    }

    public VariantQuery setConservation(FilterList<Float> conservation) {
        this.conservation = conservation;
        return this;
    }

    public FilterList<Float> getFunctionalScore() {
        return functionalScore;
    }

    public VariantQuery setFunctionalScore(FilterList<Float> functionalScore) {
        this.functionalScore = functionalScore;
        return this;
    }

    public FilterList<Float> getPopulationFrequencyMaf() {
        return populationFrequencyMaf;
    }

    public VariantQuery setPopulationFrequencyMaf(FilterList<Float> populationFrequencyMaf) {
        this.populationFrequencyMaf = populationFrequencyMaf;
        return this;
    }

    public FilterList<Float> getPopulationFrequencyRef() {
        return populationFrequencyRef;
    }

    public VariantQuery setPopulationFrequencyRef(FilterList<Float> populationFrequencyRef) {
        this.populationFrequencyRef = populationFrequencyRef;
        return this;
    }

    public FilterList<Float> getPopulationFrequencyAlt() {
        return populationFrequencyAlt;
    }

    public VariantQuery setPopulationFrequencyAlt(FilterList<Float> populationFrequencyAlt) {
        this.populationFrequencyAlt = populationFrequencyAlt;
        return this;
    }

    public SimpleFilterList<Integer> getConsequenceTypeInt() {
        return consequenceTypeInt;
    }

    public VariantQuery setConsequenceTypeInt(List<Integer> consequenceTypeInt) {
        this.consequenceTypeInt = new SimpleFilterList<>(consequenceTypeInt, OR);
        return this;
    }

    public SimpleFilterList<String> getConsequenceType() {
        return consequenceType;
    }

    public VariantQuery setConsequenceType(List<String> consequenceType) {
        this.consequenceType = new SimpleFilterList<>(consequenceType, OR);
        return this;
    }

    public FilterList<List<Genotype>> getGenotype() {
        return genotype;
    }

    public VariantQuery setGenotype(FilterList<List<Genotype>> genotype) {
        this.genotype = genotype;
        return this;
    }
}
