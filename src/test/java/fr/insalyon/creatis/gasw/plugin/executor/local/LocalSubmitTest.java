package fr.insalyon.creatis.gasw.plugin.executor.local;

import fr.insalyon.creatis.gasw.GaswConfiguration;
import fr.insalyon.creatis.gasw.dao.JobDAO;
import fr.insalyon.creatis.gasw.execution.FailOver;
import fr.insalyon.creatis.gasw.plugin.executor.local.execution.LocalSubmit;
import fr.insalyon.creatis.gasw.script.MoteurliteConfigGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LocalSubmitTest {

    @Mock
    private GaswConfiguration gaswConfiguration;

    @Mock
    private FailOver failOver;

    @Mock
    private MoteurliteConfigGenerator moteurliteConfigGenerator;

    @Mock
    private LocalConfiguration localConfiguration;

    @Mock
    private JobDAO jobDAO;

    private LocalSubmit localSubmit;

    @BeforeEach
    public void setUp() {
        when(localConfiguration.getNumberOfThreads()).thenReturn(2);
        localSubmit = new LocalSubmit(gaswConfiguration, failOver, moteurliteConfigGenerator,
                localConfiguration, jobDAO);
    }

    @Test
    @DisplayName("finished jobs are enqueued and consumed correctly")
    void finishedJobQueue_isConsumedWhenPolled() {
        localSubmit.addFinishedJobID("job-1--0");
        localSubmit.addFinishedJobID("job-2--0");

        assertTrue(localSubmit.hasFinishedJobs());
        assertEquals("job-1--0", localSubmit.pullFinishedJobID());
        assertEquals("job-2--0", localSubmit.pullFinishedJobID());
        assertFalse(localSubmit.hasFinishedJobs());
        assertNull(localSubmit.pullFinishedJobID());
    }
}