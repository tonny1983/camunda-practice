package cc.tonny.practice.camunda;

import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.repositoryService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(FrameworkRunner.class)
@CreateDS(name = "testDS", partitions = {
        @CreatePartition(name = "test", suffix = "dc=eorionsolution,dc=com")
})
@CreateLdapServer(transports = {@CreateTransport(protocol = "LDAP", address = "localhost", port = 33389)})
@ApplyLdifFiles({"ldif/init.ldif"})
@Deployment(resources = {"bpmn/process-with-candidate-group.bpmn", "bpmn/process-with-candidate-user.bpmn"})
public class ProcessEngineWithLDAPTest extends AbstractLdapTestUnit {
    @Rule
    public ProcessEngineRule rule = new ProcessEngineRule("/camunda-ldap.cfg.xml");

    @Test
    public void shouldGetProcessDefinitionForUserWhoInCandidateGroup() {
        // confirm the required process definition exists
        var count = repositoryService().createProcessDefinitionQuery().processDefinitionName("ProcessWithCandidateGroup").count();
        assertEquals(1L, count);

        // a user in the candidate group should find the definition by "startableByUser"
        // HOWEVER, the result is 0 not 1!
        count = repositoryService().createProcessDefinitionQuery().processDefinitionName("ProcessWithCandidateGroup").startableByUser("user").count();
        assertEquals(1L, count);
    }

    @Test
    public void shouldGetProcessDefinitionForUserWhoInCandidateUser() {
        // confirm the required process definition exists
        var count = repositoryService().createProcessDefinitionQuery().processDefinitionName("ProcessWithCandidateUser").count();
        assertEquals(1L, count);

        // a user is in the candidate user should find the definition by "startableByUser"
        count = repositoryService().createProcessDefinitionQuery().processDefinitionName("ProcessWithCandidateUser").startableByUser("user").count();
        assertEquals(1L, count);
    }
}
