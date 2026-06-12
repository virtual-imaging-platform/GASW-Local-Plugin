/* Copyright CNRS-CREATIS
 *
 * Rafael Ferreira da Silva
 * rafael.silva@creatis.insa-lyon.fr
 * http://www.rafaelsilva.com
 *
 * This software is governed by the CeCILL  license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 */
package fr.insalyon.creatis.gasw.plugin.executor.local.execution;

import fr.insalyon.creatis.gasw.GaswConfiguration;
import fr.insalyon.creatis.gasw.GaswInput;
import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.GaswConstants;
import fr.insalyon.creatis.gasw.bean.Job;
import fr.insalyon.creatis.gasw.dao.DAOException;
import fr.insalyon.creatis.gasw.dao.JobDAO;
import fr.insalyon.creatis.gasw.execution.FailOver;
import fr.insalyon.creatis.gasw.execution.GaswStatus;
import fr.insalyon.creatis.gasw.execution.GaswSubmit;
import fr.insalyon.creatis.gasw.plugin.executor.local.LocalConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.Date;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import fr.insalyon.creatis.gasw.script.MoteurliteConfigGenerator;
import jakarta.annotation.PreDestroy;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocalSubmit extends GaswSubmit {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final LocalConfiguration localConfiguration;
    private final LocalMonitor localMonitor;
    private final JobDAO jobDAO;

    private final BlockingQueue<String> finishedJobs = new LinkedBlockingQueue<>();
    // Thread pool containing all invocation threads
    // Initialize a pool of threads with a maximum number of threads
    // When pool size exceeds this number and if there are more tasks to execute,
    // these tasks will be put into a queue and execute when a thread is available
    private final ExecutorService executorService;

    public LocalSubmit(GaswConfiguration config, FailOver failOver, MoteurliteConfigGenerator moteurliteConfigGenerator,
                       LocalConfiguration localConfiguration, LocalMonitor localMonitor, JobDAO jobDAO) {
        super(config, failOver, moteurliteConfigGenerator);
        this.localConfiguration = localConfiguration;
        this.localMonitor = localMonitor;
        this.jobDAO = jobDAO;
        executorService = Executors.newFixedThreadPool(this.localConfiguration.getNumberOfThreads());
    }

    @Override
    public String submit(GaswInput gaswInput) {
        try {
            super.submit(gaswInput);
            scriptName = generateScript(gaswInput);
            StringBuilder params = new StringBuilder();
            for (String p : gaswInput.getParameters()) {
                params.append(p);
                params.append(" ");
            }
            String fileName = scriptName.substring(0, scriptName.lastIndexOf("."));
            String command = FilenameUtils.getBaseName(gaswInput.getExecutableName());
            localMonitor.add(fileName, command, fileName, params.toString());

            executorService.execute(new Execution(fileName, scriptName, jobDAO));

            logger.info("Local Executor Job ID: {}", fileName);
            return fileName;
        } catch (GaswException ex) {
            logger.error("Error submitting job", ex);
            return null;
        }
    }

    public String pullFinishedJobID() {
        return finishedJobs.poll();
    }

    public boolean hasFinishedJobs() {
        return !finishedJobs.isEmpty();
    }

    /**
     * Terminates the thread pool.
     */
    @PreDestroy
    public void terminate() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    class Execution implements Runnable {

        private final String jobID;
        private final String scriptName;
        private final JobDAO jobDAO;

        public Execution(String jobID, String scriptName, JobDAO jobDAO) {
            this.jobID = jobID;
            this.scriptName = scriptName;
            this.jobDAO = jobDAO;
        }

        @Override
        public void run() {

            try {
                Job job = jobDAO.getJobByID(jobID);
                job.setStatus(GaswStatus.RUNNING);
                job.setDownload(new Date());
                jobDAO.update(job);

                File stdOut = new File(GaswConstants.OUT_ROOT, scriptName + ".out");
                File stdErr = new File(GaswConstants.ERR_ROOT, scriptName + ".err");
                stdOut.getParentFile().mkdirs();
                stdErr.getParentFile().mkdirs();


                ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", GaswConstants.SCRIPT_ROOT + "/" + scriptName);
                processBuilder.redirectOutput(stdOut);
                processBuilder.redirectError(stdErr);

                int exitValue = processBuilder.start().waitFor();
                finishedJobs.offer(jobID + "--" + exitValue);
            } catch (DAOException | InterruptedException | IOException ex) {
                logger.error("Error:", ex);
            }
        }
    }
}
