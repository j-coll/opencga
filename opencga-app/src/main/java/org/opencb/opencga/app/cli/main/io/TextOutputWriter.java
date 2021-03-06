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

package org.opencb.opencga.app.cli.main.io;

import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.DataResult;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.models.AbstractAclEntry;
import org.opencb.opencga.core.models.cohort.Cohort;
import org.opencb.opencga.core.models.common.Annotable;
import org.opencb.opencga.core.models.common.AnnotationSet;
import org.opencb.opencga.core.models.file.File;
import org.opencb.opencga.core.models.file.FileTree;
import org.opencb.opencga.core.models.individual.Individual;
import org.opencb.opencga.core.models.job.Job;
import org.opencb.opencga.core.models.project.Project;
import org.opencb.opencga.core.models.sample.Sample;
import org.opencb.opencga.core.models.study.Group;
import org.opencb.opencga.core.models.study.Study;
import org.opencb.opencga.core.models.study.Variable;
import org.opencb.opencga.core.models.study.VariableSet;
import org.opencb.opencga.core.models.user.User;
import org.opencb.opencga.core.response.RestResponse;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.opencb.opencga.core.common.IOUtils.humanReadableByteCount;

/**
 * Created by pfurio on 28/11/16.
 */
public class TextOutputWriter extends AbstractOutputWriter {

    private Table.PrinterType tableType;

    public TextOutputWriter() {
    }

    public TextOutputWriter(WriterConfiguration writerConfiguration) {
        this(writerConfiguration, Table.PrinterType.TSV);
    }

    public TextOutputWriter(WriterConfiguration writerConfiguration, Table.PrinterType tableType) {
        super(writerConfiguration);
        this.tableType = tableType;
    }

    @Override
    public void print(RestResponse queryResponse) {
        if (checkErrors(queryResponse)) {
            return;
        }

        if (queryResponse.getResponses().size() == 0 || ((DataResult) queryResponse.getResponses().get(0)).getNumResults() == 0) {
            if (queryResponse.first().getNumMatches() > 0) {
                // count
                ps.println(queryResponse.first().getNumMatches());
            } else {
                ps.println("No results found for the query.");
            }
            return;
        }

        ps.print(printMetadata(queryResponse));

        List<DataResult> queryResultList = queryResponse.getResponses();
        String[] split = queryResultList.get(0).getResultType().split("\\.");
        String clazz = split[split.length - 1];

        switch (clazz) {
            case "User":
                printUser(queryResponse.getResponses());
                break;
            case "Project":
                printProject(queryResponse.getResponses());
                break;
            case "Study":
                printStudy(queryResponse.getResponses());
                break;
            case "File":
                printFiles(queryResponse.getResponses());
                break;
            case "Sample":
                printSamples(queryResponse.getResponses());
                break;
            case "Cohort":
                printCohorts(queryResponse.getResponses());
                break;
            case "Individual":
                printIndividual(queryResponse.getResponses());
                break;
//            case "Family":
//                printFamily(queryResponse.getResponses());
//                break;
            case "Job":
                printJob(queryResponse.getResponses());
                break;
            case "VariableSet":
                printVariableSet(queryResponse.getResponses());
                break;
            case "AnnotationSet":
                printAnnotationSet(queryResponse.getResponses());
                break;
            case "FileTree":
                printTreeFile(queryResponse);
                break;
            default:
                System.err.println(ANSI_YELLOW + "Warning: " + clazz + " results not yet supported in text format. Using YAML format"
                        + ANSI_RESET);
                YamlOutputWriter yamlOutputWriter = new YamlOutputWriter(writerConfiguration);
                yamlOutputWriter.print(queryResponse);
                break;
        }

    }

    private String printMetadata(RestResponse queryResponse) {
        StringBuilder sb = new StringBuilder();
        if (writerConfiguration.isMetadata()) {
            int numResults = 0;
//            int totalResults = 0;
            int time = 0;

            List<DataResult> queryResultList = queryResponse.getResponses();
            for (DataResult queryResult : queryResultList) {
                numResults += queryResult.getNumResults();
//                totalResults += queryResult.getNumMatches();
                time += queryResult.getTime();
            }

            sb.append("## Date: ").append(TimeUtils.getTime()).append("\n")
                    .append("## Number of results: ").append(numResults)
                    .append(". Time: ").append(time).append(" ms\n");

            // TODO: Add query info
            sb.append("## Query: { ")
                    .append(queryResponse.getParams()
                            .entrySet().stream().map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining(", ")))
                    .append(" }\n");
        }
        return sb.toString();
    }

    private void printUser(List<DataResult<User>> queryResultList) {
        StringBuilder sb = new StringBuilder();
        for (DataResult<User> queryResult : queryResultList) {
            // Write header
            if (writerConfiguration.isHeader()) {
                sb.append("#(U)ID\tNAME\tE-MAIL\tORGANIZATION\tACCOUNT_TYPE\tSIZE\tQUOTA\n");
                sb.append("#(P)\tID\tNAME\tDESCRIPTION\n");
                sb.append("#(S)\t\tID\tNAME\tDESCRIPTION\t#GROUPS\tSIZE\n");
            }

            for (User user : queryResult.getResults()) {
                sb.append(String.format("%s%s\t%s\t%s\t%s\t%s\t%d\n", "",
                        StringUtils.defaultIfEmpty(user.getId(), "-"), StringUtils.defaultIfEmpty(user.getName(), "-"),
                        StringUtils.defaultIfEmpty(user.getEmail(), "-"), StringUtils.defaultIfEmpty(user.getOrganization(), "-"),
                        StringUtils.defaultIfEmpty(user.getAccount() != null ? user.getAccount().getType().name() : "-", "-"),
                        user.getQuota().getMaxDisk()));

                if (user.getProjects().size() > 0) {
                    for (Project project : user.getProjects()) {
                        sb.append(String.format("%s%s\t%s\t%s\n", " * ",
                                StringUtils.defaultIfEmpty(project.getId(), "-"), StringUtils.defaultIfEmpty(project.getName(), "-"),
                                StringUtils.defaultIfEmpty(project.getDescription(), "-")));

                        if (project.getStudies().size() > 0) {
                            for (Study study : project.getStudies()) {
                                sb.append(String.format("    - %s\t%s\t%s\t%s\t%d\n",
                                        StringUtils.defaultIfEmpty(study.getId(), "-"), StringUtils.defaultIfEmpty(study.getName(), "-"),
                                        StringUtils.defaultIfEmpty(study.getDescription(), "-"),
                                        study.getGroups() == null ? ""
                                                : study.getGroups().stream().map(Group::getId).collect(Collectors.joining(",")),
                                        study.getSize()));

                                if (study.getGroups() != null && study.getGroups().size() > 0) {
                                    sb.append("       Groups:\n");
                                    for (Group group : study.getGroups()) {
                                        printGroup(group, sb, "        + ");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ps.println(sb.toString());
    }

    private void printProject(List<DataResult<Project>> queryResultList) {
        StringBuilder sb = new StringBuilder();
        for (DataResult<Project> queryResult : queryResultList) {
            // Write header
            sb.append("#ID\tNAME\tORGANISM\tASSEMBLY\tDESCRIPTION\t#STUDIES\tSTATUS\n");

            for (Project project : queryResult.getResults()) {
                String organism = "NA";
                String assembly = "NA";
                if (project.getOrganism() != null) {
                    organism = StringUtils.isNotEmpty(project.getOrganism().getScientificName())
                            ? project.getOrganism().getScientificName()
                            : (StringUtils.isNotEmpty(project.getOrganism().getCommonName())
                            ? project.getOrganism().getCommonName() : "NA");
                    if (StringUtils.isNotEmpty(project.getOrganism().getAssembly())) {
                        assembly = project.getOrganism().getAssembly();
                    }
                }

                sb.append(String.format("%s\t%s\t%s\t%s\t%s\t%d\t%s\n", StringUtils.defaultIfEmpty(project.getId(), "-"),
                        StringUtils.defaultIfEmpty(project.getName(), ","),
                        organism, assembly, StringUtils.defaultIfEmpty(project.getDescription(), "-"),
                        project.getStudies() != null ? project.getStudies().size() : -1,
                        project.getInternal().getStatus() != null
                                ? StringUtils.defaultIfEmpty(project.getInternal().getStatus().getName(), "-")
                                : "-")
                );
            }
        }
        ps.println(sb.toString());
    }

    private void printStudy(List<DataResult<Study>> queryResultList) {
        StringBuilder sb = new StringBuilder();
        for (DataResult<Study> queryResult : queryResultList) {
            // Write header
            sb.append("#ID\tNAME\tDESCRIPTION\t#GROUPS\tSIZE\t#FILES\t#SAMPLES\t#COHORTS\t#INDIVIDUALS\t#JOBS\t")
                    .append("#VARIABLE_SETS\tSTATUS\n");

            for (Study study : queryResult.getResults()) {
                sb.append(String.format("%s\t%s\t%s\t%d\t%d\t%s\t%d\t%d\t%d\t%d\t%d\t%s\n",
                        StringUtils.defaultIfEmpty(study.getId(), "-"), StringUtils.defaultIfEmpty(study.getName(), "-"),
                        StringUtils.defaultIfEmpty(study.getDescription(), "-"),
                        study.getGroups() != null ? study.getGroups().size() : -1, study.getSize(),
                        study.getFiles() != null ? study.getFiles().size() : -1,
                        study.getSamples() != null ? study.getSamples().size() : -1,
                        study.getCohorts() != null ? study.getCohorts().size() : -1,
                        study.getIndividuals() != null ? study.getIndividuals().size() : -1,
                        study.getJobs() != null ? study.getJobs().size() : -1,
                        study.getVariableSets() != null ? study.getVariableSets().size() : -1,
                        study.getInternal() != null && study.getInternal().getStatus() != null
                                ? StringUtils.defaultIfEmpty(study.getInternal().getStatus().getName(), "-") : "-"));
            }
        }

        ps.println(sb.toString());
    }

    private void printGroup(Group group, StringBuilder sb, String prefix) {
        sb.append(String.format("%s%s\t%s\n", prefix, group.getId(), StringUtils.join(group.getUserIds(), ", ")));
    }

    private void printACL(AbstractAclEntry aclEntry, StringBuilder sb, String prefix) {
        sb.append(String.format("%s%s\t%s\n", prefix, aclEntry.getMember(), aclEntry.getPermissions().toString()));
    }

    private void printFiles(List<DataResult<File>> queryResultList) {
        Table<File> table = new Table<File>(tableType)
                .addColumn("ID", File::getId, 50)
                .addColumn("NAME", File::getName, 50)
                .addColumnEnum("TYPE", File::getType)
                .addColumnEnum("FORMAT", File::getFormat)
                .addColumnEnum("BIOFORMAT", File::getBioformat)
                .addColumn("DESCRIPTION", File::getDescription)
                .addColumn("CATALOG_PATH", File::getPath)
                .addColumn("FILE_SYSTEM_URI", file -> file.getUri().toString())
                .addColumn("STATUS", f -> f.getInternal().getStatus().getName())
                .addColumnNumber("SIZE", File::getSize)
                .addColumn("INDEX_STATUS", f -> f.getInternal().getIndex().getStatus().getName(), "NA")
                .addColumn("RELATED_FILES", f -> f.getRelatedFiles().stream().map(rf -> rf.getFile().getName()).collect(Collectors.joining(",")))
                .addColumn("SAMPLES", f -> f.getSamples().stream().map(Sample::getId).collect(Collectors.joining(",")));

        table.updateTable(queryResultList.stream().flatMap(r -> r.getResults().stream()).collect(Collectors.toList()));
        table.printTable();
    }

    private void printSamples(List<DataResult<Sample>> queryResultList) {
        Table<Sample> table = new Table<Sample>(tableType)
                .addColumn("ID", Sample::getId)
                .addColumn("DESCRIPTION", Sample::getDescription)
                .addColumn("STATUS", s -> s.getInternal().getStatus().getName())
                .addColumn("INDIVIDUAL_ID", Sample::getIndividualId);

        table.updateTable(queryResultList.stream().flatMap(r -> r.getResults().stream()).collect(Collectors.toList()));
        table.printTable();
    }

    private void printCohorts(List<DataResult<Cohort>> queryResultList) {
        Table<Cohort> table = new Table<Cohort>(tableType)
                .addColumn("ID", Cohort::getId)
                .addColumnEnum("TYPE", Cohort::getType)
                .addColumn("DESCRIPTION", Cohort::getDescription)
                .addColumn("STATUS", c -> c.getInternal().getStatus().getName())
                .addColumnNumber("TOTAL_SAMPLES", c -> c.getSamples().size())
                .addColumn("SAMPLES", c -> c.getSamples().stream().map(Sample::getId).collect(Collectors.joining(",")));

        table.updateTable(queryResultList.stream().flatMap(r -> r.getResults().stream()).collect(Collectors.toList()));
        table.printTable();
    }

    private void printIndividual(List<DataResult<Individual>> queryResultList) {
        Table<Individual> table = new Table<Individual>(tableType)
                .addColumn("ID", Individual::getId)
                .addColumn("NAME", Individual::getId)
                .addColumnEnum("SEX", Individual::getSex)
                .addColumnEnum("KARYOTYPIC_SEX", Individual::getKaryotypicSex)
                .addColumn("ETHNICITY", Individual::getEthnicity)
                .addColumn("POPULATION", i -> i.getPopulation().getName(), "NA")
                .addColumn("SUBPOPULATION", i -> i.getPopulation().getSubpopulation(), "NA")
                .addColumnEnum("LIFE_STATUS", Individual::getLifeStatus)
                .addColumn("STATUS", i -> i.getInternal().getStatus().getName())
                .addColumn("FATHER_ID", i -> i.getFather().getId())
                .addColumn("MOTHER_ID", i -> i.getMother().getId())
                .addColumn("CREATION_DATE", Individual::getCreationDate);

        table.updateTable(queryResultList.stream().flatMap(r -> r.getResults().stream()).collect(Collectors.toList()));
        table.printTable();
    }

//    private void printFamily(List<DataResult<Family>> queryResultList) {
//        StringBuilder sb = new StringBuilder();
//        for (DataResult<Family> queryResult : queryResultList) {
//            // Write header
//            if (writerConfiguration.isHeader()) {
//                sb.append("#NAME\tID\tMOTHER\tFATHER\tMEMBER\tSTATUS\tCREATION_DATE\n");
//            }
//
//            for (Family family : queryResult.getResults()) {
//                String mother = (family.getMother() != null && StringUtils.isNotEmpty(family.getMother().getName()))
//                        ? family.getMother().getName() + "(" + family.getMother().getId() + ")"
//                        : "NA";
//                String father = (family.getFather() != null && StringUtils.isNotEmpty(family.getFather().getName()))
//                        ? family.getFather().getName() + "(" + family.getFather().getId() + ")"
//                        : "NA";
//                String children = family.getChildren() != null
//                        ? StringUtils.join(
//                                family.getChildren().stream()
//                                    .filter(Objects::nonNull)
//                                    .filter(individual -> StringUtils.isNotEmpty(individual.getName()))
//                                    .map(individual -> individual.getName() + "(" + individual.getId() + ")")
//                                    .collect(Collectors.toList()), ", ")
//                        : "NA";
//                sb.append(String.format("%s\t%d\t%s\t%s\t%s\t%s\t%s\n",
//                        family.getName(), family.getId(), mother, father, children,
//                        family.getStatus().getName(), family.getCreationDate()));
//            }
//        }
//
//        ps.println(sb.toString());
//    }

    private void printJob(List<DataResult<Job>> queryResultList) {
        Table<Job> table = new Table<Job>(tableType)
                .addColumn("ID", Job::getId, 50)
                .addColumn("TOOL_ID", j -> j.getTool().getId())
                .addColumn("CREATION_DATE", Job::getCreationDate)
                .addColumn("STATUS", j -> j.getInternal().getStatus().getName())
                .addColumn("INPUT", j -> j.getInput().stream().map(File::getName).collect(Collectors.joining(",")))
                .addColumn("OUTPUT", j -> j.getOutput().stream().map(File::getName).collect(Collectors.joining(",")))
                .addColumn("OUTPUT_DIRECTORY", j -> j.getOutDir().getPath());

        table.updateTable(queryResultList.stream().flatMap(r->r.getResults().stream()).collect(Collectors.toList()));
        table.printTable();
    }

    private void printVariableSet(List<DataResult<VariableSet>> queryResultList) {
        StringBuilder sb = new StringBuilder();
        for (DataResult<VariableSet> queryResult : queryResultList) {
            // Write header
            if (writerConfiguration.isHeader()) {
                sb.append("#ID\tNAME\tDESCRIPTION\tVARIABLES\n");
            }

            for (VariableSet variableSet : queryResult.getResults()) {
                sb.append(String.format("%s\t%s\t%s\t%s\n", StringUtils.defaultIfEmpty(variableSet.getId(), "-"),
                        StringUtils.defaultIfEmpty(variableSet.getName(), "-"),
                        StringUtils.defaultIfEmpty(variableSet.getDescription(), "-"),
                        variableSet.getVariables().stream().map(Variable::getId).collect(Collectors.joining(", "))));
            }
        }

        ps.println(sb.toString());
    }

    private void printAnnotationSet(List<DataResult<AnnotationSet>> queryResultList) {
        StringBuilder sb = new StringBuilder();
        for (DataResult<AnnotationSet> queryResult : queryResultList) {
            for (AnnotationSet annotationSet : queryResult.getResults()) {
                // Write header
                if (writerConfiguration.isHeader()) {
                    sb.append("#KEY\tVALUE\n");
                }

                for (Map.Entry<String, Object> annotation : annotationSet.getAnnotations().entrySet()) {
                    sb.append(String.format("%s\t%s\n", annotation.getKey(), annotation.getValue()));
                }
            }
        }

        ps.println(sb.toString());
    }

    private void printTreeFile(RestResponse<FileTree> queryResponse) {
        StringBuilder sb = new StringBuilder();
        for (DataResult<FileTree> fileTreeQueryResult : queryResponse.getResponses()) {
            printRecursiveTree(fileTreeQueryResult.getResults(), sb, "");
        }
        ps.println(sb.toString());
    }

    private void printRecursiveTree(List<FileTree> fileTreeList, StringBuilder sb, String indent) {
        if (fileTreeList == null || fileTreeList.size() == 0) {
            return;
        }

        for (Iterator<FileTree> iterator = fileTreeList.iterator(); iterator.hasNext(); ) {
            FileTree fileTree = iterator.next();
            File file = fileTree.getFile();

            sb.append(String.format("%s %s  [%s, %s, %s]\n",
                    indent.isEmpty() ? "" : indent + (iterator.hasNext() ? "├──" : "└──"),
                    file.getType() == File.Type.FILE ? file.getName() : file.getName() + "/",
                    file.getName(),
                    file.getInternal() != null && file.getInternal().getStatus() != null ? file.getInternal().getStatus().getName() : "",
                    humanReadableByteCount(file.getSize(), false)));

            if (file.getType() == File.Type.DIRECTORY) {
                printRecursiveTree(fileTree.getChildren(), sb, indent + (iterator.hasNext()? "│   " : "    "));
            }
        }
    }


    private String getId(Annotable annotable) {
        return getId(annotable, "-");
    }

    private String getId(Annotable annotable, String defaultStr) {
        return annotable != null ? StringUtils.defaultIfEmpty(annotable.getId(), defaultStr) : defaultStr;
    }
}
