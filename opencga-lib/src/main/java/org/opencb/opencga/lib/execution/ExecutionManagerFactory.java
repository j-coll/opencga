package org.opencb.opencga.lib.execution;

import org.opencb.opencga.lib.common.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by jacobo on 5/12/14.
 */
public class ExecutionManagerFactory {
    private static ExecutionManagerFactory executionManagerFactory = null;

    public final static String MANAGERS             = "OPENCGA.EXEC.MANAGERS";
    public final static String MANAGER_DEFAULT      = "OPENCGA.EXEC.MANAGER.DEFAULT";

    public final static String SGE = "sge";
    public final static String SLURM = "slurm";
    public final static String LOCAL = "local";
    public final static String LSF = "lsf";

    private final Properties analysisProperties;
    private final Map<String, ExecutionManager> managerMap;

    public static ExecutionManagerFactory getFactory() {
        if(executionManagerFactory == null) {
            executionManagerFactory = new ExecutionManagerFactory();
        }
        return executionManagerFactory;
    }

    private ExecutionManagerFactory() {
        analysisProperties = Config.getAnalysisProperties();
        managerMap = new HashMap<>();
    }

    public ExecutionManager getExecutionManager() {
        return getExecutionManager(getDefaultManager());
    }

    public ExecutionManager getExecutionManager(String name) {
        if(name == null || name.isEmpty()) {
            name = getDefaultManager();
        }
        name = name.toLowerCase();
        if(!managerMap.containsKey(name)) {
            if (!isAvailableManager(name)) {
                throw new UnsupportedOperationException("Unsupported ExecutionManager " + name);    //TODO: Custom exception?
            }
            switch (name) {
                case SGE:
                    managerMap.put(name, new SgeExecutionManager());
                    break;
                case LOCAL:
                    managerMap.put(name, new LocalThreadExecutionManager());
                    break;
                case SLURM:
                    managerMap.put(name, new SlurmExecutionManager());
                    break;
                case LSF:
                    break;
            }
        }
        return managerMap.get(name);

    }

    protected boolean isAvailableManager(String name) {
        if(name == null || name.isEmpty()) {
            return false;
        }
        for (String s : getAvailableManagers()) {
            if(name.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    public String[] getAvailableManagers(){
        return analysisProperties.getProperty(MANAGERS, LOCAL).split(",");
    }

    private String getDefaultManager() {
        return analysisProperties.getProperty(MANAGER_DEFAULT, LOCAL);
    }
}
