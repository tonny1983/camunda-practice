package cc.tonny.practice.camunda;

import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;
import static org.junit.Assert.assertEquals;

@Deployment(resources = {"bpmn/process-without-candidate.bpmn", "bpmn/process-with-candidate-group.bpmn"})
public class ProcessEngineUsingDBTest {
    @Rule
    public ProcessEngineRule rule = new ProcessEngineRule("/camunda-db.cfg.xml");

    private static boolean initFlag = false;
    private static String userId;

    @Before
    public void setup() {
        if (initFlag)
            return;

        var user = identityService().newGroup("user");
        identityService().saveGroup(user);
        var staff = identityService().newUser("staff");
        identityService().saveUser(staff);
        identityService().createMembership(staff.getId(), user.getId());
        userId = staff.getId();
        initFlag = true;
    }

    @Test
    public void shouldGetProcessDefinitionForEveryoneWhenItWithoutCandidate() {
        // confirm the required process definition exists
        var count = repositoryService().createProcessDefinitionQuery().processDefinitionName("ProcessWithoutCandidate").count();
        assertEquals(1L, count);

        // any users should find the definition by "startableByUser"
        // HOWEVER, the result is 0 not 1!
        count = repositoryService().createProcessDefinitionQuery().processDefinitionName("ProcessWithoutCandidate").startableByUser(userId).count();
        assertEquals(1L, count);
    }

    @Test
    public void shouldGetProcessDefinitionForUserWhoInCandidateGroup() {
        // confirm the required process definition exists
        var count = repositoryService().createProcessDefinitionQuery().processDefinitionName("ProcessWithCandidateGroup").count();
        assertEquals(1L, count);

        // a user in the candidate group should find the definition by "startableByUser"
        count = repositoryService().createProcessDefinitionQuery().processDefinitionName("ProcessWithCandidateGroup").startableByUser(userId).count();
        assertEquals(1L, count);
    }

}
