package org.opencb.opencga.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.catalog.CatalogManager;
import org.opencb.opencga.catalog.beans.File;
import org.opencb.opencga.catalog.beans.Job;
import org.opencb.opencga.catalog.db.CatalogManagerException;
import org.opencb.opencga.catalog.io.CatalogIOManagerException;
import org.opencb.opencga.lib.execution.ExecutionManagerFactory;
import org.opencb.opencga.lib.common.Config;
import org.opencb.opencga.analysis.beans.Analysis;
import org.opencb.opencga.analysis.beans.Execution;
import org.opencb.opencga.analysis.beans.Option;
import org.opencb.opencga.lib.common.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class AnalysisJobExecuter {

    public static final String ANALYSIS_JOB_EXECUTOR = "OPENCGA.ANALYSIS.JOB.EXECUTOR";
    protected static Logger logger = LoggerFactory.getLogger(AnalysisJobExecuter.class);
    protected final Properties analysisProperties;
    protected final String home;
    protected String analysisName;
    protected String executionName;
    protected Path analysisRootPath;
    protected Path analysisPath;
    protected Path manifestFile;
    protected Path resultsFile;
    protected String sessionId;
    protected Analysis analysis;
    protected Execution execution;

    protected static ObjectMapper jsonObjectMapper  = new ObjectMapper();

    private AnalysisJobExecuter() throws  IOException, AnalysisExecutionException {
        home = Config.getGcsaHome();
        analysisProperties = Config.getAnalysisProperties();
        executionName = null;
    }

    public AnalysisJobExecuter(String analysisStr, String execution) throws  IOException, AnalysisExecutionException {
        this(analysisStr, execution, "system");
    }

    @Deprecated
    public AnalysisJobExecuter(String analysisStr, String execution, String analysisOwner) throws IOException,  AnalysisExecutionException {
        this();
        if (analysisOwner.equals("system")) {
            this.analysisRootPath = Paths.get(analysisProperties.getProperty("OPENCGA.ANALYSIS.BINARIES.PATH"));
        }
        else {
            this.analysisRootPath = Paths.get(home, "accounts", analysisOwner);
        }
        this.analysisName = analysisStr;
        if (analysisName.contains(".")) {
            executionName = analysisName.split("\\.")[1];
            analysisName = analysisName.split("\\.")[0];
        } else {
            executionName = execution;
        }

        load();
    }

    public AnalysisJobExecuter(Path analysisRootPath, String analysisName, String executionName) throws IOException,  AnalysisExecutionException {
        this();
        this.analysisRootPath = analysisRootPath;
        this.analysisName = analysisName;
        this.executionName = executionName;

        load();
    }

    private void load()  throws IOException,  AnalysisExecutionException {

        analysisPath = Paths.get(home).resolve(analysisRootPath).resolve(analysisName);
        manifestFile = analysisPath.resolve(Paths.get("manifest.json"));
        resultsFile = analysisPath.resolve(Paths.get("results.js"));

        analysis = getAnalysis();
        execution = getExecution();
    }

    @Deprecated
    public String execute(String jobName, int jobId, String jobFolder, String commandLine) throws AnalysisExecutionException, IOException {
        logger.debug("AnalysisJobExecuter: execute, 'jobName': " + jobName + ", 'jobFolder': " + jobFolder);
        logger.debug("AnalysisJobExecuter: execute, command line: " + commandLine);

        return executeCommandLine(commandLine, jobName, jobId, jobFolder, analysisName);
    }

    public static String execute(Job job) throws AnalysisExecutionException, IOException {
        logger.debug("AnalysisJobExecuter: execute, job: {}", job);

        return executeCommandLine(job.getCommandLine(), job.getName(),
                job.getId(), job.getTmpOutDirUri().getPath(), job.getToolName());
    }

    private boolean checkRequiredParams(Map<String, List<String>> params, List<Option> validParams) {
        for (Option param : validParams) {
            if (param.isRequired() && !params.containsKey(param.getName())) {
                System.out.println("Missing param: " + param);
                return false;
            }
        }
        return true;
    }

    private Map<String, List<String>> removeUnknownParams(Map<String, List<String>> params, List<Option> validOptions) {
        Set<String> validKeyParams = new HashSet<String>();
        for (Option param : validOptions) {
            validKeyParams.add(param.getName());
        }

        Map<String, List<String>> paramsCopy = new HashMap<String, List<String>>(params);
        for (String param : params.keySet()) {
            if (!validKeyParams.contains(param)) {
                paramsCopy.remove(param);
            }
        }

        return paramsCopy;
    }

    public String createCommandLine(Map<String, List<String>> params)
            throws AnalysisExecutionException {
        return createCommandLine(execution.getExecutable(), params);
    }

    public String createCommandLine(String executable, Map<String, List<String>> params)
            throws AnalysisExecutionException {
        logger.debug("params received in createCommandLine: " + params);
        String binaryPath = analysisPath.resolve(executable).toString();

        // Check required params
        List<Option> validParams = execution.getValidParams();
        validParams.addAll(analysis.getGlobalParams());
        validParams.add(new Option(execution.getOutputParam(), "Outdir", false));
        if (checkRequiredParams(params, validParams)) {
            params = new HashMap<String, List<String>>(removeUnknownParams(params, validParams));
        } else {
            throw new AnalysisExecutionException("ERROR: missing some required params.");
        }

        StringBuilder cmdLine = new StringBuilder();
        cmdLine.append(binaryPath);

        if (params.containsKey("tool")) {
            String tool = params.get("tool").get(0);
            cmdLine.append(" --tool ").append(tool);
            params.remove("tool");
        }

        for (String key : params.keySet()) {
            // Removing renato param
            if (!key.equals("renato")) {
                if (key.length() == 1) {
                    cmdLine.append(" -").append(key);
                } else {
                    cmdLine.append(" --").append(key);
                }
                if (params.get(key) != null) {
                    String paramsArray = params.get(key).toString();
                    String paramValue = paramsArray.substring(1, paramsArray.length() - 1).replaceAll("\\s", "");
                    cmdLine.append(" ").append(paramValue);
                }
            }
        }
        return cmdLine.toString();
    }

    public QueryResult<Job> createJob(Map<String, List<String>> params,
                            CatalogManager catalogManager, int studyId, String jobName, String description, File outDir, List<Integer> inputFiles, String sessionId)
            throws AnalysisExecutionException, CatalogManagerException, CatalogIOManagerException {
        return createJob(execution.getExecutable(), params, catalogManager, studyId, jobName, description, outDir, inputFiles, sessionId);
    }
    public QueryResult<Job> createJob(String executable, Map<String, List<String>> params,
                            CatalogManager catalogManager, int studyId, String jobName, String description, File outDir, List<Integer> inputFiles, String sessionId)
            throws AnalysisExecutionException, CatalogManagerException, CatalogIOManagerException {

        // Create temporal Outdir
        String randomString = "J_" + StringUtils.randomString(10);
        URI temporalOutDirUri = catalogManager.createJobOutDir(studyId, randomString, sessionId);
        params.put(getExecution().getOutputParam(), Arrays.asList(temporalOutDirUri.getPath()));

        // Create commandLine
        String commandLine = createCommandLine(executable, params);
        System.out.println(commandLine);

        // Create job in CatalogManager
        Map<String, Object> resourceManagerAttributes = new HashMap<>();
//        resourceManagerAttributes.put(Job.JOB_SCHEDULER_NAME, randomString);

        QueryResult<Job> jobQueryResult = catalogManager.createJob(studyId, jobName, analysisName, description, commandLine, temporalOutDirUri,
                outDir.getId(), inputFiles, resourceManagerAttributes, sessionId);
        return jobQueryResult;
    }


    private static String executeCommandLine(String commandLine, String jobName, int jobId, String jobFolder, String analysisName)
            throws AnalysisExecutionException, IOException {
        // read execution param
        String jobExecutor = getAnalysisExecutionManagerName();
        String jobExecutorId;

//        // local execution
//        if (jobExecutor == null || jobExecutor.trim().equalsIgnoreCase("LOCAL")) {
//            logger.debug("AnalysisJobExecuter: execute, running by SingleProcess");
//
//            Command com = new Command(commandLine);
//            SingleProcess sp = new SingleProcess(com);
//            sp.getRunnableProcess().run();
//        }
//        // sge execution
//        else {
            logger.debug("AnalysisJobExecuter: execute, running by ExecutionManager {}", jobExecutor);

            try {
                jobExecutorId = ExecutionManagerFactory.getFactory().getExecutionManager(jobExecutor).
                        queueJob(analysisName, jobName, "", jobFolder, commandLine, null, "job." + jobId);
            } catch (Exception e) {
                logger.error(e.toString());
                throw new AnalysisExecutionException("ERROR: ExeecutionManager '" + jobExecutor + "' failed. ", e);
            }
//        }
        return jobExecutorId;
    }

    public static String getAnalysisExecutionManagerName() {
        return Config.getAnalysisProperties().getProperty(ANALYSIS_JOB_EXECUTOR);
    }

    public Analysis getAnalysis() throws IOException, AnalysisExecutionException {
        if (analysis == null) {
            analysis = jsonObjectMapper.readValue(manifestFile.toFile(), Analysis.class);
//            analysis = gson.fromJson(IOUtils.toString(manifestFile.toFile()), Analysis.class);
        }
        return analysis;
    }

    public Execution getExecution() throws AnalysisExecutionException {
        if (execution == null) {
            if (executionName == null || executionName.isEmpty()) {
                execution = analysis.getExecutions().get(0);
            } else {
                for (Execution exe : analysis.getExecutions()) {
                    if (exe.getId().equalsIgnoreCase(executionName)) {
                        execution = exe;
                        break;
                    }
                }
            }
        }
        return execution;
    }

    public String getExamplePath(String fileName) {
        return analysisPath.resolve("examples").resolve(fileName).toString();
    }

    public String help(String baseUrl) {
        if (!Files.exists(manifestFile)) {
            return "Manifest for " + analysisName + " not found.";
        }

        String execName = "";
        if (executionName != null)
            execName = "." + executionName;
        StringBuilder sb = new StringBuilder();
        sb.append("Analysis: " + analysis.getName() + "\n");
        sb.append("Description: " + analysis.getDescription() + "\n");
        sb.append("Version: " + analysis.getVersion() + "\n\n");
        sb.append("Author: " + analysis.getAuthor().getName() + "\n");
        sb.append("Email: " + analysis.getAuthor().getEmail() + "\n");
        if (!analysis.getWebsite().equals(""))
            sb.append("Website: " + analysis.getWebsite() + "\n");
        if (!analysis.getPublication().equals(""))
            sb.append("Publication: " + analysis.getPublication() + "\n");
        sb.append("\nUsage: \n");
        sb.append(baseUrl + "analysis/" + analysisName + execName + "/{action}?{params}\n\n");
        sb.append("\twhere: \n");
        sb.append("\t\t{action} = [run, help, params, test, status]\n");
        sb.append("\t\t{params} = " + baseUrl + "analysis/" + analysisName + execName + "/params\n");
        return sb.toString();
    }

    public String params() {
        if (!Files.exists(manifestFile)) {
            return "Manifest for " + analysisName + " not found.";
        }

        if (execution == null) {
            return "ERROR: Executable not found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Valid params for " + analysis.getName() + ":\n\n");
        for (Option param : execution.getValidParams()) {
            String required = "";
            if (param.isRequired())
                required = "*";
            sb.append("\t" + param.getName() + ": " + param.getDescription() + " " + required + "\n");
        }
        sb.append("\n\t*: required parameters.\n");
        return sb.toString();
    }

    public String test(String jobName, int jobId, String jobFolder) throws AnalysisExecutionException, IOException {
        // TODO test

        if (!Files.exists(manifestFile)) {
            return "Manifest for " + analysisName + " not found.";
        }

        if (execution == null) {
            return "ERROR: Executable not found.";
        }

        executeCommandLine(execution.getTestCmd(), jobName, jobId, jobFolder, analysisName);

        return String.valueOf(jobName);
    }

    public String getResult() throws AnalysisExecutionException {
        return execution.getResult();
    }

    public InputStream getResultInputStream() throws AnalysisExecutionException, IOException {
        System.out.println(resultsFile.toAbsolutePath().toString());
        if (!Files.exists(resultsFile)) {
            resultsFile = analysisPath.resolve(Paths.get("results.js"));
        }

        if (Files.exists(resultsFile)) {
            return Files.newInputStream(resultsFile);
        }
        throw new AnalysisExecutionException("result.js not found.");
    }
}
