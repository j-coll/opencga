package org.opencb.opencga.lib.execution;

import org.opencb.opencga.lib.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.*;

/**
 * Created by jacobo on 5/12/14.
 */
public abstract class ExecutionManager {

    public final static String QUEUES               = "OPENCGA.EXEC.QUEUES";
    public final static String DEFAULT_QUEUE        = "OPENCGA.EXEC.QUEUE.DEFAULT";
    public final static String QUEUE_NAME           = "OPENCGA.EXEC.QUEUE.";    //OPENCGA.EXEC.QUEUE.<QUEUE_NAME>.TOOLS

    protected static Logger logger = LoggerFactory.getLogger(ExecutionManager.class);
    protected static Properties analysisProperties = Config.getAnalysisProperties();


    /**
     *
     *
     * @param toolName      Tool name
     * @param jobName       Job name identifier
     * @param outDir        LogFiles outdir
     * @param commandLine   CommandLine
     * @param queue         Queue. If null, uses default.
     * @param logFilePrefix LogFiles prefix identifier
     * @return              jobExecutionId
     * @throws Exception
     */
    public abstract String queueJob(String toolName, String jobName, URI outDir, String commandLine, String queue, String logFilePrefix)
            throws Exception;

    /**
     *
     *
     * @param toolName      Tool name
     * @param jobName       Job name identifier
     * @param hostName
     * @param outDir        LogFiles outdir
     * @param commandLine   CommandLine
     * @param queue         Queue. If null, uses default.
     * @param logFilePrefix LogFiles prefix identifier
     * @return              jobExecutionId
     * @throws Exception
     */
    public abstract String queueJob(String toolName, String jobName, String hostName, String outDir, String commandLine, String queue, String logFilePrefix)
            throws Exception;

    public abstract ExecutionJobStatus status(String jobExecutionId) throws Exception;

    protected static String getDefaultQueue() {
        if (analysisProperties.containsKey(DEFAULT_QUEUE)) {
            return analysisProperties.getProperty(DEFAULT_QUEUE);
        } else {
            List<String> queueList = getQueueList();
            if(!queueList.isEmpty()) {
                return queueList.get(0);
            } else {
                return null;
            }
        }
    }

    protected static List<String> getQueueList() {
        if (analysisProperties.containsKey(QUEUES)) {
            return Arrays.asList(analysisProperties.getProperty(QUEUES).split(","));
        } else {
            return new ArrayList<>();
        }
    }


    protected static String getQueue(String toolName) throws Exception {
        String defaultQueue = getDefaultQueue();
        logger.debug("SgeManager: default queue: " + defaultQueue);

        // get all available queues
        List<String> queueList = getQueueList();
        logger.debug("SgeManager: available queues: " + queueList);

        // search corresponding queue
        String selectedQueue = defaultQueue;
        for (String queue : queueList) {
            if (!queue.equalsIgnoreCase(defaultQueue)) {
                if (getQueueTools(queue).contains(toolName)) {
                    selectedQueue = queue;
                    break;
                }
            }
        }
        logger.info("SgeManager: selected queue for tool '" + toolName + "': " + selectedQueue);
        return selectedQueue;
    }

    /**
     * Get available tools to be executed over a queue
     *
     * Reads the csv property: OPENCGA.EXEC.QUEUE.<QUEUE_NAME>.TOOLS
     *
     * @param queueName Queue name
     * @return          Tools list
     */
    protected static List<String> getQueueTools(String queueName) {
        String queueProperty = QUEUE_NAME + queueName.toUpperCase() + ".TOOLS";
        return Arrays.asList(analysisProperties.getProperty(queueProperty, "").split(","));
    }


    protected String executeCommand(String commandLine) throws IOException {
        String stringResult;
        Process p = Runtime.getRuntime().exec(commandLine);
        StringBuilder stdOut = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));

        logger.info("Executing: {} ", commandLine);

        String aux = br.readLine();
        if(aux != null) {
            stdOut.append(aux);
        }
        while ((aux = br.readLine()) != null) {
            stdOut.append("\n").append(aux);
        }
        stringResult = stdOut.toString();
        br.close();
        return stringResult;
    }


}
