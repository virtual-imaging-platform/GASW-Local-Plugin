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
import fr.insalyon.creatis.gasw.dao.JobDAO;
import fr.insalyon.creatis.gasw.execution.GaswMonitor;
import fr.insalyon.creatis.gasw.execution.GaswParsingContext;
import fr.insalyon.creatis.gasw.execution.GaswStatus;
import fr.insalyon.creatis.gasw.plugin.ListenerPlugin;
import fr.insalyon.creatis.gasw.plugin.executor.local.LocalConstants;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalMonitor extends GaswMonitor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final GaswConfiguration gaswConfiguration;
    private final JobDAO jobDAO;
    private final LocalSubmit localSubmit;
    private final LocalOutputParser localOutputParser;
    private final AtomicBoolean stop = new AtomicBoolean(false);

    public LocalMonitor(GaswConfiguration config, JobDAO jobDAO, List<ListenerPlugin> listenerPlugins,
                        LocalSubmit localSubmit, LocalOutputParser localOutputParser) {
        super(config, jobDAO, listenerPlugins);
        this.gaswConfiguration = config;
        this.jobDAO = jobDAO;
        this.localSubmit = localSubmit;
        this.localOutputParser = localOutputParser;
    }

    @Scheduled(fixedDelayString = "${gasw.default.sleep-time}", timeUnit = TimeUnit.SECONDS)
    public void monitorJobs() {
        if (stop.get()) return;
        try {
            while (localSubmit.hasFinishedJobs()) {
                String[] s = localSubmit.pullFinishedJobID().split("--");
                Job job = jobDAO.getJobByID(s[0]);
                job.setExitCode(Integer.parseInt(s[1]));

                if (job.getExitCode() == 0) {
                    job.setStatus(GaswStatus.COMPLETED);
                } else {
                    job.setStatus(GaswStatus.ERROR);
                }
                jobDAO.update(job);
                localOutputParser.run(new GaswParsingContext(job));
            }
        } catch (DAOException | IOException ex) {
            logger.error("Error monitoring jobs:", ex);
        }
    }

    @Override
    @Transactional
    public void add(String jobID, String symbolicName, String fileName,
            String parameters) throws GaswException {

        logger.info("Adding job: {}", jobID);
        Job job = new Job(jobID, gaswConfiguration.getSimulationID(),
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

    @Override
    public void start() {}

    @Override
    public void terminate() {
        stop.set(true);
    }

    @Override
    protected void kill(Job job) {
    }

    @Override
    protected void reschedule(Job job) {
    }

    @Override
    protected void replicate(Job job) {
    }

    @Override
    protected void killReplicas(Job job) {
    }

    @Override
    protected void resume(Job job) {
    }
}
