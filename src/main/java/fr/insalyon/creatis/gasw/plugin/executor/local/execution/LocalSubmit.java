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

import fr.insalyon.creatis.gasw.GaswConstants;
import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.GaswInput;
import fr.insalyon.creatis.gasw.GaswUtil;
import fr.insalyon.creatis.gasw.bean.Job;
import fr.insalyon.creatis.gasw.dao.DAOException;
import fr.insalyon.creatis.gasw.dao.DAOFactory;
import fr.insalyon.creatis.gasw.dao.JobDAO;
import fr.insalyon.creatis.gasw.execution.GaswStatus;
import fr.insalyon.creatis.gasw.execution.GaswSubmit;
import fr.insalyon.creatis.gasw.plugin.executor.local.LocalConfiguration;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.log4j.Logger;

/**
 *
 * @author Rafael Ferreira da Silva, Tram Truong Huu
 */
public class LocalSubmit extends GaswSubmit {

    private static final Logger logger = Logger.getLogger("fr.insalyon.creatis.gasw");
    private static List<String> finishedJobs = new ArrayList<String>();
    // Thread pool containing all invocation threads
    // Initialize a pool of threads with a maximum number of threads
    // When pool size exceeds this number and if there are more tasks to execute, 
    // these tasks will be put into a queue and execute when a thread is availableF
    private volatile static ExecutorService executionThreadPool;

    public LocalSubmit(GaswInput gaswInput,
            LocalMinorStatusServiceGenerator minorStatusServiceGenerator) throws GaswException {

        super(gaswInput, minorStatusServiceGenerator);

        if (executionThreadPool == null) {
            executionThreadPool = Executors.newFixedThreadPool(LocalConfiguration.getInstance().getNumberOfThreads());
        }

        scriptName = generateScript();
    }

    @Override
    public String submit() throws GaswException {

        StringBuilder params = new StringBuilder();
        for (String p : gaswInput.getParameters()) {
            params.append(p);
            params.append(" ");
        }
        String fileName = scriptName.substring(0, scriptName.lastIndexOf("."));
        LocalMonitor.getInstance().add(fileName, gaswInput.getExecutableName(),
                fileName, params.toString());

        executionThreadPool.execute(new Execution(fileName));

        logger.info("Local Executor Job ID: " + fileName);
        return fileName;
    }

    class Execution implements Runnable {

        private String jobID;

        public Execution(String jobID) {
            this.jobID = jobID;
        }

        @Override
        public void run() {

            try {
                JobDAO jobDAO = DAOFactory.getDAOFactory().getJobDAO();
                Job job = jobDAO.getJobByID(jobID);
                job.setStatus(GaswStatus.RUNNING);
                job.setDownload(new Date());
                jobDAO.update(job);

                Process process = GaswUtil.getProcess(logger, false,
                        "/bin/sh", GaswConstants.SCRIPT_ROOT + "/" + scriptName);

                StringWriter infos = new StringWriter();
                StringWriter errors = new StringWriter();
                StreamBoozer seInfo = new StreamBoozer(process.getInputStream(), new PrintWriter(infos, true));
                StreamBoozer seError = new StreamBoozer(process.getErrorStream(), new PrintWriter(errors, true));
                seInfo.start();
                seError.start();

                process.waitFor();

                int exitValue = process.exitValue();

                File stdOutDir = new File(GaswConstants.OUT_ROOT);
                if (!stdOutDir.exists()) {
                    stdOutDir.mkdirs();
                }
                File stdOut = new File(stdOutDir, scriptName + ".out");
                BufferedWriter out = new BufferedWriter(new FileWriter(stdOut));
                out.write(infos.toString());
                out.close();

                File stdErrDir = new File(GaswConstants.ERR_ROOT);
                if (!stdErrDir.exists()) {
                    stdErrDir.mkdirs();
                }

                File stdErr = new File(stdErrDir, scriptName + ".err");
                BufferedWriter err = new BufferedWriter(new FileWriter(stdErr));
                err.write(errors.toString());
                err.close();



                synchronized (this) {
                    finishedJobs.add(jobID + "--" + exitValue);
                }

            } catch (DAOException ex) {
                // do nothing
            } catch (InterruptedException ex) {
                logger.error(ex);
            } catch (IOException ex) {
                logger.error(ex);
            }
        }

        class StreamBoozer extends Thread {

            private InputStream in;
            private PrintWriter pw;

            StreamBoozer(InputStream in, PrintWriter pw) {
                this.in = in;
                this.pw = pw;
            }

            @Override
            public void run() {
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(in));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        pw.println(line);
                    }
                } catch (Exception ex) {
                    logger.error(ex);
                } finally {
                    try {
                        br.close();
                    } catch (IOException ex) {
                        logger.error(ex);
                    }
                }
            }
        }
    }

    public synchronized static String pullFinishedJobID() {
        String jobID = finishedJobs.get(0);
        finishedJobs.remove(jobID);
        return jobID;
    }

    public synchronized static boolean hasFinishedJobs() {
        if (finishedJobs.size() > 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Terminates the thread pool.
     */
    public static void terminate() {

        if (executionThreadPool != null) {
            executionThreadPool.shutdown();
        }
    }
}
