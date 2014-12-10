package org.opencb.opencga.lib.execution;

import org.opencb.datastore.core.ObjectMap;
import org.opencb.opencga.lib.common.Config;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by jacobo on 5/12/14.
 */
public class SlurmExecutionManager extends ExecutionManager {

    private static final Map<String, String> stateDic;

    static {
        //%t    Job state, compact form:
        //PD (pending), R (running), CA (cancelled), CF(configuring), CG (completing), CD (completed), F (failed), TO (timeout), NF (node failure) and SE (special exit state).

        //%T    Job state, extended form:
        //PENDING, RUNNING, SUSPENDED, CANCELLED, COMPLETING, COMPLETED, CONFIGURING, FAILED, TIMEOUT, PREEMPTED, NODE_FAIL and SPECIAL_EXIT

        stateDic = new HashMap<>();
        stateDic.put("PENDING",         ExecutionJobStatus.QUEUED);
        stateDic.put("CONFIGURING",     ExecutionJobStatus.QUEUED);

        stateDic.put("COMPLETING",      ExecutionJobStatus.RUNNING);
        stateDic.put("RUNNING",         ExecutionJobStatus.RUNNING);

        stateDic.put("COMPLETED",       ExecutionJobStatus.FINISHED);
        stateDic.put("FAILED",          ExecutionJobStatus.EXECUTION_ERROR);
        stateDic.put("TIMEOUT",         ExecutionJobStatus.ERROR);
        stateDic.put("NODE_FAIL",       ExecutionJobStatus.QUEUE_ERROR);

        stateDic.put("CANCELED",        ExecutionJobStatus.FINISHED);
        stateDic.put("SPECIAL_EXIT",    ExecutionJobStatus.UNKNOWN);
        stateDic.put("SUSPENDED",       ExecutionJobStatus.UNKNOWN);
        stateDic.put("PREEMPTED",       ExecutionJobStatus.UNKNOWN);
    }

    public static final String SUBMISSION_SCRIPT = "OPENCGA.EXEC.SLURM.SUBMISSION_SCRIPT";

    private final String slurmSubmissionScript;

    public SlurmExecutionManager() {
        String submissionScript = analysisProperties.getProperty(SUBMISSION_SCRIPT, "");
        if(submissionScript == null || submissionScript.isEmpty() || Files.exists(Paths.get(submissionScript))) {
            submissionScript = Paths.get(Config.getGcsaHome()).resolve("bin/slurm/submission.sh").toString();
        }
        this.slurmSubmissionScript = submissionScript;

    }

    @Override
    public String queueJob(String toolName, String jobName, URI outDir, String commandLine, String queue, String logFilePrefix) throws Exception {
        return queueJob(toolName, jobName, outDir.getHost(), outDir.getPath(), commandLine, queue, logFilePrefix);
    }

    @Override
    public String queueJob(String toolName, String jobName, String hostName, String outDir, String commandLine, String queue, String logFilePrefix) throws Exception {

        String slurmJobName = toolName.replace(" ","_") + "_" + jobName.replace(" ","_");

        logFilePrefix = logFilePrefix == null || logFilePrefix.isEmpty()? "" : "." + logFilePrefix;
        hostName = hostName == null || hostName.isEmpty()? "" : hostName + ":";
        queue = queue == null || queue.isEmpty()? getQueue(toolName) : queue;

        String outFile = hostName + Paths.get(outDir, "out" + logFilePrefix + ".log").toString();
        String errFile = hostName + Paths.get(outDir, "err" + logFilePrefix + ".log").toString();

        String sbatch = "sbatch " +
                " --output "     + outFile +
                " --error "      + errFile +
                " --partition "  + queue   +
                " --job-name "   + slurmJobName + " " +
                slurmSubmissionScript + " " + commandLine;


        String executionJobId = null;
        try {
            String result = executeCommand(sbatch);
            String[] split = result.split(" ");
            executionJobId = split[split.length - 1].trim();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return executionJobId;
    }

    @Override
    public ExecutionJobStatus status(String jobExecutionId) {
        ExecutionJobStatus executionJobStatus = new ExecutionJobStatus();

        String sacct = "sacct " +
//                " --noheader " +
                " --format jobid,JobIDRaw,jobname,partition,maxvmsize,maxvmsizenode,maxvmsizetask,avevmsize," +
                      "maxrss,maxrssnode,maxrsstask,averss,maxpages,maxpagesnode,maxpagestask,avepages,mincpu," +
                      "mincpunode,mincputask,avecpu,ntasks,alloccpus,elapsed,state,exitcode" +
                      ",maxdiskread," +
                      "maxdiskreadnode,maxdiskreadtask,avediskread,maxdiskwrite,maxdiskwritenode,maxdiskwritetask," +
                      "avediskwrite,allocgres,reqgres " +
                " --parsable2 " +
                " --jobs " + jobExecutionId;
        String result = "";
        try {
            result = executeCommand(sacct);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!result.isEmpty()) {
            String[] lines = result.split("\n");
            String[] header = lines[0].split("\\|", -1);

            executionJobStatus.setAttributes(buildObjectMap(header, lines[1]));

            List<ObjectMap> jobSteps = new LinkedList<>();
            for(int i = 2; i < lines.length; i++) {
                jobSteps.add(buildObjectMap(header, lines[i]));
            }
            executionJobStatus.getAttributes().put("jobSteps", jobSteps);

            String slurmStatus = executionJobStatus.getAttributes().getString("State");
            String status;
            System.out.println(slurmStatus);
            if(stateDic.containsKey(slurmStatus)) {
                status = stateDic.get(slurmStatus);
            } else {
                status = ExecutionJobStatus.UNKNOWN;
            }
            executionJobStatus.setStatus(status);
        }
        return executionJobStatus;


//        String commandLine = "squeue "
//                + " --noheader "
//                + " --format \"%i|%T\""
//                + " --jobs " + jobExecutionId;
//        String result = "";
//        try {
//            result = executeCommand(commandLine);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        if (!result.isEmpty()) {
//            String[] split = result.split("\\|");
//            if (!split[0].equals(jobExecutionId)) {
//                throw new RuntimeException("squeue returned unexpected job");
//            }
//            if(stateDic.containsKey(split[1])) {
//                executionJobStatus.setStatus(stateDic.get(split[1]));
//            }
//            return executionJobStatus;
//        } else {
//
//        }
    }

    private ObjectMap buildObjectMap(String[] header, String line) {
        ObjectMap attributes = new ObjectMap(header.length);
        String[] values = line.split("\\|", -1);

        if(header.length != values.length) {
            throw new RuntimeException("sacct returned unexpected values"); //TODO: Replace exception
        }

        for (int i = 0; i < header.length; i++) {
            if(!values[i].isEmpty()) {    //skip missing values
                continue;
            }
            attributes.put(header[i],values[i]);
        }
        return attributes;
    }
}
