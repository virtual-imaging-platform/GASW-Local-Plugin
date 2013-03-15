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
import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.bean.Job;
import fr.insalyon.creatis.gasw.dao.DAOException;
import fr.insalyon.creatis.gasw.execution.GaswMonitor;
import fr.insalyon.creatis.gasw.execution.GaswStatus;
import fr.insalyon.creatis.gasw.plugin.executor.local.LocalConstants;
import grool.proxy.Proxy;
import java.util.Date;
import org.apache.log4j.Logger;

/**
 *
 * @author Rafael Ferreira da Silva
 */
public class LocalMonitor extends GaswMonitor {

    private static final Logger logger = Logger.getLogger("fr.insalyon.creatis.gasw");
    private static LocalMonitor instance;
    private boolean stop;

    public synchronized static LocalMonitor getInstance() {
        if (instance == null) {
            instance = new LocalMonitor();
            instance.start();
        }
        return instance;
    }

    private LocalMonitor() {

        super();
        stop = false;
    }

    @Override
    public void run() {

        while (!stop) {
            try {
                while (LocalSubmit.hasFinishedJobs()) {

                    String[] s = LocalSubmit.pullFinishedJobID().split("--");
                    Job job = jobDAO.getJobByID(s[0]);
                    job.setExitCode(new Integer(s[1]));

                    if (job.getExitCode() == 0) {
                        job.setStatus(GaswStatus.COMPLETED);
                    } else {
                        job.setStatus(GaswStatus.ERROR);
                    }
                    jobDAO.update(job);
                    new LocalOutputParser(job.getId(), null).start();
                }

                Thread.sleep(GaswConfiguration.getInstance().getDefaultSleeptime());

            } catch (GaswException ex) {
                // do nothing
            } catch (DAOException ex) {
                logger.error(ex);
            } catch (InterruptedException ex) {
                logger.error(ex);
            }
        }
    }

    @Override
    public synchronized void add(String jobID, String symbolicName, String fileName,
            String parameters, Proxy userProxy) throws GaswException {

        logger.info("Adding job: " + jobID);
        Job job = new Job(jobID, GaswConfiguration.getInstance().getSimulationID(),
                GaswStatus.QUEUED, symbolicName, fileName, parameters,
                LocalConstants.EXECUTOR_NAME);
        add(job);

        // Queued Time
        try {
            job.setQueued(new Date());
            jobDAO.update(job);
        } catch (DAOException ex) {
            // do nothing
        }
    }

    public synchronized void terminate() {

        stop = true;
        instance = null;
    }

    public static void finish() {
        if (instance != null) {
            instance.terminate();
        }
    }

    @Override
    protected void kill(String jobID) {
    }

    @Override
    protected void reschedule(String jobID) {
    }

    @Override
    protected void replicate(String jobID) {
    }

    @Override
    protected void killReplicas(int invocationID) {
    }
}
