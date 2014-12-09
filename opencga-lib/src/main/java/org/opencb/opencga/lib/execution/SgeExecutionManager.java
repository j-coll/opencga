package org.opencb.opencga.lib.execution;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;

public class SgeExecutionManager extends ExecutionManager {

    private static SgeExecutionManager sgeExecutionManager;
    private static final Map<String, String> stateDic;
    static {
        stateDic = new HashMap<>();
        stateDic.put("r", ExecutionJobStatus.RUNNING);
        stateDic.put("t", ExecutionJobStatus.TRANSFERRED);
        stateDic.put("qw", ExecutionJobStatus.QUEUED);
        stateDic.put("Eqw", ExecutionJobStatus.ERROR);
    }

    SgeExecutionManager() {
        super();
    }

    public static SgeExecutionManager getSgeExecutionManager() {
        if(sgeExecutionManager == null) {
            sgeExecutionManager = new SgeExecutionManager();
        }
        return sgeExecutionManager;
    }

//    public static void queueJob(String toolName, String wumJobName, int wumUserId, String outdir, String commandLine)
//            throws Exception {
//        queueJob(toolName, wumJobName, wumUserId, outdir, commandLine, getQueue(toolName));
//    }
//
//    public static void queueJob(String toolName, String wumJobName, int wumUserId, String outdir, String commandLine, String queue)
//            throws Exception {
//        queueJob(toolName, wumJobName, wumUserId, outdir, commandLine, queue, "");
//    }

//    public void queueJob(String toolName, String wumJobName, int wumUserId, URI outdir, String commandLine, String queue, String logFileId)
//            throws Exception {
//        if (outdir.getScheme() != null && !outdir.getScheme().equals("file")) {
//            throw new IOException("Unsupported outdir for QueueJob");
//        }
//        queueJob(toolName, wumJobName, wumUserId, outdir.getPath(), commandLine, queue, logFileId);
//    }
//
    @Override
    public String queueJob(String toolName, String jobName, URI outdir, String commandLine, String queue, String logFilePrefix) throws Exception {
        return queueJob(toolName, jobName, outdir.getHost(), outdir.getPath(), commandLine, queue, logFilePrefix);
    }

    @Override
    public String queueJob(String toolName, String jobName, String hostName, String outDir, String commandLine, String queue, String logFilePrefix)
            throws Exception {
        logFilePrefix = logFilePrefix == null || logFilePrefix.isEmpty()? "" : "." + logFilePrefix;
        hostName = hostName == null || hostName.isEmpty()? "" : hostName + ":";
        queue = queue == null || queue.isEmpty()? getQueue(toolName) : queue;


        String outFile = hostName + Paths.get(outDir, "sge_out" + logFilePrefix + ".log").toString();
        String errFile = hostName + Paths.get(outDir, "sge_err" + logFilePrefix + ".log").toString();
                // init sge job
        String sgeJobName = getSgeJobName(toolName, jobName);
        String sgeCommandLine = "qsub -V " +
                        " -N " + sgeJobName +   //The name of the job. The name should follow the  "name" definition in sge_types(1).
                        " -o " + outFile +      //The path used for the standard output stream of the job
                        " -e " + errFile +      //The path used for the standard error stream of the job
                        " -q " + queue +
                        " -b " + " y " +
                        " -terse " +            //display only the job-id of the job
                        commandLine;

        logger.info("SgeManager: Enqueuing job: " + sgeCommandLine);

        // thrown command to shell
        String jobExecutionId = executeCommand(sgeCommandLine);

        return jobExecutionId;
    }


    private static String getSgeJobName(String toolName, String jobName) {
        return toolName.replace(" ", "_") + "_" + jobName.replace(" ", "_");
    }

    /**
     *
     * @param jobExecutionId    Integer identifier
     */
    @Override
    public ExecutionJobStatus status(String jobExecutionId) throws Exception {
        ExecutionJobStatus executionJobStatus = new ExecutionJobStatus();
        executionJobStatus.setStatus(getQstat(jobExecutionId));

        if (executionJobStatus.getStatus().equals(ExecutionJobStatus.UNKNOWN)) {
            String command = "qacct -j " + jobExecutionId;
            String out = executeCommand(command);
            for (String line : out.split("\n")) {
                String[] split = line.split(" ", 2);
                if(split.length != 2) {
                    continue;
                }
                executionJobStatus.getAttributes().put(split[0].trim(), split[1].trim());
            }
            if (executionJobStatus.getAttributes().containsKey("exit_status") && executionJobStatus.getAttributes().containsKey("failed")) {
                if (!executionJobStatus.getAttributes().getString("failed", "").equals("0")) {
                    executionJobStatus.setStatus(ExecutionJobStatus.QUEUE_ERROR);
                } else if (executionJobStatus.getAttributes().getString("exit_status", "").equals("0")) {
                    executionJobStatus.setStatus(ExecutionJobStatus.FINISHED);
                } else {
                    executionJobStatus.setStatus(ExecutionJobStatus.EXECUTION_ERROR);
                }
            }
        }
        return executionJobStatus;
    }

    protected String getQstat(String jobExecutionId) throws Exception {
        String xml = null;
        String status = ExecutionJobStatus.UNKNOWN;
        try {
            xml = executeCommand("qstat -xml");
        } catch (IOException e) {
            logger.error(e.toString());
            throw new Exception("ERROR: can't get status for job " + jobExecutionId + ".", e);
        }

        if (xml != null) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(new InputSource(new StringReader(xml)));
                doc.getDocumentElement().normalize();
                NodeList nodeLst = doc.getElementsByTagName("job_list");

                for (int s = 0; s < nodeLst.getLength(); s++) {
                    Node fstNode = nodeLst.item(s);

                    if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element fstElmnt = (Element) fstNode;
                        NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("JB_job_number");//JB_name
                        Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
                        NodeList fstNm = fstNmElmnt.getChildNodes();
                        String jobName = ((Node) fstNm.item(0)).getNodeValue();
                        if (jobName.contains(jobExecutionId)) {
                            NodeList lstNmElmntLst = fstElmnt.getElementsByTagName("state");
                            Element lstNmElmnt = (Element) lstNmElmntLst.item(0);
                            NodeList lstNm = lstNmElmnt.getChildNodes();
                            status = ((Node) lstNm.item(0)).getNodeValue();
                            status = stateDic.get(status);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error(e.toString());
                throw new Exception("ERROR: can't get status for job " + jobExecutionId + ".");
            }
        }

        return status;
    }

    protected String executeCommand(String commandLine) throws IOException {
        String stringResult;
        Process p = Runtime.getRuntime().exec(commandLine);
        StringBuilder stdOut = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));

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
