package cc.tonny.practice.integration;

import lombok.extern.java.Log;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpResponse;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;

@Deployment(resources = {"bpmn/integration.bpmn"})
@Log
public class IntegrationBPMNTest {

    @Rule
    public ProcessEngineRule rule = new ProcessEngineRule("/camunda-db.cfg.xml");

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, 19000);

    private MockServerClient mockServerClient;

    @Test
    public void dummyTest() {
        var count = repositoryService().createProcessDefinitionQuery().processDefinitionKey("business-process").count();
        assertEquals(1L, count);
        count = repositoryService().createProcessDefinitionQuery().processDefinitionKey("integration-process").count();
        assertEquals(1L, count);
    }

    @Test
    public void testBusinessProcess() {
        mockServerClient.when(request("/data/"))
                .respond(HttpResponse.response().withStatusCode(200));

        var instance = runtimeService().startProcessInstanceByKey("business-process", "DEMO0001");
        var task = taskService().createTaskQuery().processInstanceId(instance.getProcessInstanceId()).singleResult();
        taskService().complete(task.getId());
        assertThat(instance).hasPassed("send-data", "before-result-task")
        .isWaitingAt("receive-message")
        .isNotEnded();
    }

    @Test
    public void testIntegrationProcess() {
        mockServerClient.when(request("/batch/"))
                .respond(HttpResponse.response().withStatusCode(200));
        mockServerClient.when(request("/result/"))
                .respond(HttpResponse.response().withStatusCode(404));
        var jobQuery = managementService().createJobQuery();
        assertEquals(1, jobQuery.count());

        var zoneId = ZoneId.systemDefault();
        var date = LocalDateTime.of(2099, Month.JULY, 1, 8, 31, 0);
        var zdt = date.atZone(zoneId);
        var current = Date.from(zdt.toInstant());
        ClockUtil.setCurrentTime(current);
        executeAllJobs();

        var instance = runtimeService().createProcessInstanceQuery().singleResult();
        assertThat(instance).hasPassed("send-batch-data")
                .isWaitingAt("wait-result")
                .isNotEnded();

        ClockUtil.setCurrentTime(new Date(current.getTime() +  90 * 1000));
        waitForJobExecutorToProcessAllJobs(5000L);
        assertThat(instance).hasPassed("send-batch-data", "wait-result", "receive-data")
                .isWaitingAt("wait-result")
                .isNotEnded();
    }

    @Test
    public void testAllTwoProcesses() {
        mockServerClient.when(request("/data/"))
                .respond(HttpResponse.response().withStatusCode(200));
        mockServerClient.when(request("/batch/"))
                .respond(HttpResponse.response().withStatusCode(200));
        mockServerClient.when(request("/result/"))
                .respond(HttpResponse.response("[{\"DEMO0001\":\"OK\"}, {\"DEMO0002\":\"NG\"}]").withStatusCode(200));

        var businessInstance1 = runtimeService().startProcessInstanceByKey("business-process", "DEMO0001");
        var task = taskService().createTaskQuery().processInstanceId(businessInstance1.getProcessInstanceId()).singleResult();
        taskService().complete(task.getId());
        assertThat(businessInstance1).hasPassed("send-data", "before-result-task")
                .isWaitingAt("receive-message")
                .isNotEnded();

        var businessInstance2 = runtimeService().startProcessInstanceByKey("business-process", "DEMO0002");
        task = taskService().createTaskQuery().processInstanceId(businessInstance2.getProcessInstanceId()).singleResult();
        taskService().complete(task.getId());
        assertThat(businessInstance2).hasPassed("send-data", "before-result-task")
                .isWaitingAt("receive-message")
                .isNotEnded();

        var count = runtimeService().createProcessInstanceQuery().processDefinitionKey("integration-process").count();
        assertEquals(0L, count);

        var zoneId = ZoneId.systemDefault();
        var date = LocalDateTime.of(2099, Month.JULY, 1, 8, 31, 0);
        var zdt = date.atZone(zoneId);
        var current = Date.from(zdt.toInstant());
        ClockUtil.setCurrentTime(current);
        executeAllJobs();

        var integrationInstance = runtimeService().createProcessInstanceQuery().processDefinitionKey("integration-process").singleResult();
        assertThat(integrationInstance).hasPassed("send-batch-data")
                .isWaitingAt("wait-result")
                .isNotEnded();

        ClockUtil.setCurrentTime(new Date(current.getTime() +  90 * 1000));
        waitForJobExecutorToProcessAllJobs(5000L);

        assertThat(businessInstance1).hasPassed("send-data", "before-result-task", "receive-message", "ok-script-task")
                .isEnded();
        assertThat(businessInstance2).hasPassed("send-data", "before-result-task", "receive-message", "notok-script-task")
                .isEnded();
        assertThat(integrationInstance).hasPassed("send-batch-data", "wait-result", "receive-data", "send-result-message")
                .isEnded();
    }

    private void executeAllJobs() {
        var nextJobId = getNextExecutableJobId();

        while (nextJobId != null) {
            try {
                managementService().executeJob(nextJobId);
            } catch (Throwable t) { /* ignore */
            }
            nextJobId = getNextExecutableJobId();
        }

    }

    private String getNextExecutableJobId() {
        var jobs = managementService().createJobQuery().executable().listPage(0, 1);
        if (jobs.size() == 1) {
            return jobs.get(0).getId();
        } else {
            return null;
        }
    }

    private void waitForJobExecutorToProcessAllJobs(long maxMillisToWait) {
        ProcessEngineConfigurationImpl processEngineConfiguration = ( (ProcessEngineImpl)processEngine()).getProcessEngineConfiguration();
        var jobExecutor = processEngineConfiguration.getJobExecutor();
        jobExecutor.start();
        long intervalMillis = 1000;

        int jobExecutorWaitTime = jobExecutor.getWaitTimeInMillis() * 2;
        if(maxMillisToWait < jobExecutorWaitTime) {
            maxMillisToWait = jobExecutorWaitTime;
        }

        try {
            var timer = new Timer();
            InterruptTask task = new InterruptTask(Thread.currentThread());
            timer.schedule(task, maxMillisToWait);
            boolean areJobsAvailable = true;
            try {
                while (areJobsAvailable && !task.isTimeLimitExceeded()) {
                    Thread.sleep(intervalMillis);
                    try {
                        areJobsAvailable = areJobsAvailable();
                    } catch(Throwable t) {
                        // Ignore, possible that exception occurs due to locking/updating of table on MSSQL when
                        // isolation level doesn't allow READ of the table
                    }
                }
            } catch (InterruptedException e) {
            } finally {
                timer.cancel();
            }
            if (areJobsAvailable) {
                throw new ProcessEngineException("time limit of " + maxMillisToWait + " was exceeded");
            }

        } finally {
            jobExecutor.shutdown();
        }
    }

    private boolean areJobsAvailable() {
        var list = managementService().createJobQuery().list();
        for (var job : list) {
            if (!job.isSuspended() && job.getRetries() > 0 && (job.getDuedate() == null || ClockUtil.getCurrentTime().after(job.getDuedate()))) {
                return true;
            }
        }
        return false;
    }

    private static class InterruptTask extends TimerTask {
        protected boolean timeLimitExceeded = false;
        protected Thread thread;
        public InterruptTask(Thread thread) {
            this.thread = thread;
        }
        public boolean isTimeLimitExceeded() {
            return timeLimitExceeded;
        }
        @Override
        public void run() {
            timeLimitExceeded = true;
            thread.interrupt();
        }
    }
}
