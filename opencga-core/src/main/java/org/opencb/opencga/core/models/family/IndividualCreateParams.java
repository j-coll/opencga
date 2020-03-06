package org.opencb.opencga.core.models.family;

import org.apache.commons.lang3.StringUtils;
import org.opencb.biodata.models.commons.Disorder;
import org.opencb.biodata.models.commons.Phenotype;
import org.opencb.biodata.models.pedigree.IndividualProperty;
import org.opencb.biodata.models.pedigree.Multiples;
import org.opencb.opencga.core.models.common.AnnotationSet;
import org.opencb.opencga.core.models.common.CustomStatus;
import org.opencb.opencga.core.models.common.CustomStatusParams;
import org.opencb.opencga.core.models.individual.Individual;
import org.opencb.opencga.core.models.individual.IndividualPopulation;
import org.opencb.opencga.core.models.individual.Location;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class IndividualCreateParams {

    private String id;
    private String name;

    private String father;
    private String mother;
    private Multiples multiples;
    private Location location;

    private IndividualProperty.Sex sex;
    private String ethnicity;
    private Boolean parentalConsanguinity;
    private IndividualPopulation population;
    private String dateOfBirth;
    private IndividualProperty.KaryotypicSex karyotypicSex;
    private IndividualProperty.LifeStatus lifeStatus;
    private List<AnnotationSet> annotationSets;
    private List<Phenotype> phenotypes;
    private List<Disorder> disorders;
    private CustomStatusParams status;
    private Map<String, Object> attributes;

    public IndividualCreateParams() {
    }

    public IndividualCreateParams(String id, String name, String father, String mother, Multiples multiples, Location location,
                                  IndividualProperty.Sex sex, String ethnicity, Boolean parentalConsanguinity,
                                  IndividualPopulation population, String dateOfBirth, IndividualProperty.KaryotypicSex karyotypicSex,
                                  IndividualProperty.LifeStatus lifeStatus, List<AnnotationSet> annotationSets, List<Phenotype> phenotypes,
                                  List<Disorder> disorders, CustomStatusParams status, Map<String, Object> attributes) {
        this.id = id;
        this.name = name;
        this.father = father;
        this.mother = mother;
        this.multiples = multiples;
        this.location = location;
        this.sex = sex;
        this.ethnicity = ethnicity;
        this.parentalConsanguinity = parentalConsanguinity;
        this.population = population;
        this.dateOfBirth = dateOfBirth;
        this.karyotypicSex = karyotypicSex;
        this.lifeStatus = lifeStatus;
        this.annotationSets = annotationSets;
        this.phenotypes = phenotypes;
        this.disorders = disorders;
        this.status = status;
        this.attributes = attributes;
    }

    public static IndividualCreateParams of(Individual individual) {
        return new IndividualCreateParams(individual.getId(), individual.getName(),
                individual.getFather() != null ? individual.getFather().getId() : null,
                individual.getMother() != null ? individual.getMother().getId() : null,
                individual.getMultiples(), individual.getLocation(),
                individual.getSex(), individual.getEthnicity(), individual.isParentalConsanguinity(), individual.getPopulation(),
                individual.getDateOfBirth(), individual.getKaryotypicSex(), individual.getLifeStatus(),
                individual.getAnnotationSets(), individual.getPhenotypes(), individual.getDisorders(),
                CustomStatusParams.of(individual.getStatus()), individual.getAttributes());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IndividualCreateParams{");
        sb.append("id='").append(id).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", father='").append(father).append('\'');
        sb.append(", mother='").append(mother).append('\'');
        sb.append(", multiples=").append(multiples);
        sb.append(", location=").append(location);
        sb.append(", sex=").append(sex);
        sb.append(", ethnicity='").append(ethnicity).append('\'');
        sb.append(", parentalConsanguinity=").append(parentalConsanguinity);
        sb.append(", population=").append(population);
        sb.append(", dateOfBirth='").append(dateOfBirth).append('\'');
        sb.append(", karyotypicSex=").append(karyotypicSex);
        sb.append(", lifeStatus=").append(lifeStatus);
        sb.append(", annotationSets=").append(annotationSets);
        sb.append(", phenotypes=").append(phenotypes);
        sb.append(", disorders=").append(disorders);
        sb.append(", status=").append(status);
        sb.append(", attributes=").append(attributes);
        sb.append('}');
        return sb.toString();
    }

    public Individual toIndividual() {
        String individualId = StringUtils.isEmpty(id) ? name : id;
        String individualName = StringUtils.isEmpty(name) ? individualId : name;
        return new Individual(individualId, individualName, new Individual().setId(father), new Individual().setId(mother), multiples,
                location, sex, karyotypicSex, ethnicity, population, dateOfBirth, 1, 1, "", lifeStatus, phenotypes, disorders,
                Collections.emptyList(), parentalConsanguinity != null ? parentalConsanguinity : false, annotationSets,
                status != null ? status.toCustomStatus() : new CustomStatus(), null, attributes);
    }

    public String getId() {
        return id;
    }

    public IndividualCreateParams setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public IndividualCreateParams setName(String name) {
        this.name = name;
        return this;
    }

    public String getFather() {
        return father;
    }

    public IndividualCreateParams setFather(String father) {
        this.father = father;
        return this;
    }

    public String getMother() {
        return mother;
    }

    public IndividualCreateParams setMother(String mother) {
        this.mother = mother;
        return this;
    }

    public Multiples getMultiples() {
        return multiples;
    }

    public IndividualCreateParams setMultiples(Multiples multiples) {
        this.multiples = multiples;
        return this;
    }

    public Location getLocation() {
        return location;
    }

    public IndividualCreateParams setLocation(Location location) {
        this.location = location;
        return this;
    }

    public IndividualProperty.Sex getSex() {
        return sex;
    }

    public IndividualCreateParams setSex(IndividualProperty.Sex sex) {
        this.sex = sex;
        return this;
    }

    public String getEthnicity() {
        return ethnicity;
    }

    public IndividualCreateParams setEthnicity(String ethnicity) {
        this.ethnicity = ethnicity;
        return this;
    }

    public Boolean getParentalConsanguinity() {
        return parentalConsanguinity;
    }

    public IndividualCreateParams setParentalConsanguinity(Boolean parentalConsanguinity) {
        this.parentalConsanguinity = parentalConsanguinity;
        return this;
    }

    public IndividualPopulation getPopulation() {
        return population;
    }

    public IndividualCreateParams setPopulation(IndividualPopulation population) {
        this.population = population;
        return this;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public IndividualCreateParams setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        return this;
    }

    public IndividualProperty.KaryotypicSex getKaryotypicSex() {
        return karyotypicSex;
    }

    public IndividualCreateParams setKaryotypicSex(IndividualProperty.KaryotypicSex karyotypicSex) {
        this.karyotypicSex = karyotypicSex;
        return this;
    }

    public IndividualProperty.LifeStatus getLifeStatus() {
        return lifeStatus;
    }

    public IndividualCreateParams setLifeStatus(IndividualProperty.LifeStatus lifeStatus) {
        this.lifeStatus = lifeStatus;
        return this;
    }

    public List<AnnotationSet> getAnnotationSets() {
        return annotationSets;
    }

    public IndividualCreateParams setAnnotationSets(List<AnnotationSet> annotationSets) {
        this.annotationSets = annotationSets;
        return this;
    }

    public List<Phenotype> getPhenotypes() {
        return phenotypes;
    }

    public IndividualCreateParams setPhenotypes(List<Phenotype> phenotypes) {
        this.phenotypes = phenotypes;
        return this;
    }

    public List<Disorder> getDisorders() {
        return disorders;
    }

    public IndividualCreateParams setDisorders(List<Disorder> disorders) {
        this.disorders = disorders;
        return this;
    }

    public CustomStatusParams getStatus() {
        return status;
    }

    public IndividualCreateParams setStatus(CustomStatusParams status) {
        this.status = status;
        return this;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public IndividualCreateParams setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }
}
