package fr.insalyon.creatis.gasw.plugin.executor.local;

import fr.insalyon.creatis.gasw.GaswConfiguration;
import fr.insalyon.creatis.gasw.bean.Job;
import fr.insalyon.creatis.gasw.dao.JobDAO;
import fr.insalyon.creatis.gasw.execution.GaswStatus;
import fr.insalyon.creatis.gasw.plugin.ListenerPlugin;
import fr.insalyon.creatis.gasw.plugin.executor.local.execution.LocalMonitor;
import fr.insalyon.creatis.gasw.plugin.executor.local.execution.LocalOutputParser;
import fr.insalyon.creatis.gasw.plugin.executor.local.execution.LocalSubmit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LocalMonitorTest {

    @Mock
    private GaswConfiguration gaswConfiguration;

    @Mock
    private JobDAO jobDAO;

    @Mock
    private LocalSubmit localSubmit;

    @Mock
    private LocalOutputParser localOutputParser;

    private LocalMonitor localMonitor;

    @BeforeEach
    public void setUp() {
        List<ListenerPlugin> listenerPlugins = Collections.emptyList();
        localMonitor = new LocalMonitor(gaswConfiguration, jobDAO, listenerPlugins, localSubmit, localOutputParser);
    }

    @Nested
    @DisplayName("monitorJobs()")
    class MonitorJobs {

        @ParameterizedTest(name = "exitCode={0} -> status={1}")
        @DisplayName("marks finished jobs with the correct status and runs the output parser")
        @CsvSource({
                "0, COMPLETED",
                "1, ERROR"
        })
        void finishedJob_isMarkedAndParsed(int exitCode, GaswStatus expectedStatus) throws Exception {
            Job job = mock(Job.class);

            when(localSubmit.hasFinishedJobs()).thenReturn(true, false);
            when(localSubmit.pullFinishedJobID()).thenReturn("job1--" + exitCode);
            when(jobDAO.getJobByID("job1")).thenReturn(job);
            when(job.getExitCode()).thenReturn(exitCode);

            localMonitor.monitorJobs();

            verify(job).setExitCode(exitCode);
            verify(job).setStatus(expectedStatus);
            verify(jobDAO).update(job);
            verify(localOutputParser).run(any());
        }

        @Test
        @DisplayName("processes every finished job present in a single pass")
        void multipleFinishedJobs_areAllProcessed() throws Exception {
            Job job1 = mock(Job.class);
            Job job2 = mock(Job.class);
            when(localSubmit.hasFinishedJobs()).thenReturn(true, true, false);
            when(localSubmit.pullFinishedJobID()).thenReturn("job1--0", "job2--0");
            when(jobDAO.getJobByID("job1")).thenReturn(job1);
            when(jobDAO.getJobByID("job2")).thenReturn(job2);

            localMonitor.monitorJobs();

            verify(jobDAO).update(job1);
            verify(jobDAO).update(job2);
            verify(localOutputParser, times(2)).run(any());
        }

        @ParameterizedTest(name = "terminate_triggered={0}")
        @DisplayName("does nothing when monitoring is stopped or has no finished jobs")
        @ValueSource(booleans = {true, false})
        void doesNothing_whenStoppedOrNoJobs(boolean terminated) {
            if (terminated) {
                localMonitor.terminate();
            } else {
                when(localSubmit.hasFinishedJobs()).thenReturn(false);
            }

            localMonitor.monitorJobs();

            verifyNoInteractions(jobDAO, localOutputParser);
            if (terminated) {
                verifyNoInteractions(localSubmit);
            }
        }
    }
}