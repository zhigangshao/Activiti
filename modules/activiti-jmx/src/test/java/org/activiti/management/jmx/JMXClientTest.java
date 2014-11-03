/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.management.jmx;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.junit.Test;



/**
 * @author Saeid Mirzaei
 */

public class JMXClientTest {
  
  @SuppressWarnings("unchecked")
  @Test
  public void testJmxClient() throws IOException, InterruptedException, MalformedObjectNameException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException, IntrospectionException {
    JMXServiceURL url = 
            new JMXServiceURL("service:jmx:rmi://DEACN458:10111/jndi/rmi://" + Utils.getHostName() + ":1099/jmxrmi/activiti");
   
    
    ProcessEngineConfiguration processEngineConfig = ProcessEngineConfiguration.createProcessEngineConfigurationFromResource("activiti.cfg.xml");
    ProcessEngine processEngine = processEngineConfig.buildProcessEngine();
    
    
    // wait for jmx server to come up
    Thread.sleep(500);
    JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
    
    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
    
    
    ObjectName mbeanName = new ObjectName("org.activiti.jmx.Mbeans:type=Deployments");
    
    
    Thread.sleep(500);
    
    // no process deployed yet
    @SuppressWarnings("unchecked")
    List<List<String>> deployments = (List<List<String>>) mbsc.getAttribute(mbeanName, "Deployments");
    assertEquals(0, deployments.size());
    
    
    // deploy process remotely
    
    URL fileName = Thread.currentThread().getContextClassLoader().getResource( "org/activiti/management/jmx/trivialProcess.bpmn");
    mbsc.invoke(mbeanName, "deployProcessDefinition", new String[]{"trivialProcess.bpmn", fileName.getFile()}, new String[]{String.class.getName(), String.class.getName()});


    // one process is there now, test remote deployments
    deployments = (List<List<String>>) mbsc.getAttribute(mbeanName, "Deployments");
    assertNotNull(deployments);
    assertEquals(1, deployments.size());
    assertEquals(3, deployments.get(0).size());
    String firstDeploymentId = deployments.get(0).get(0);
    
    
    // test remote process definition
    List<List<String>> pdList = (List<List<String>>) mbsc.getAttribute(mbeanName, "ProcessDefinitions");
    assertNotNull(pdList);
    assertEquals(1, pdList.size());
    assertEquals(5, pdList.get(0).size());
    assertNotNull(pdList.get(0).get(0));
    assertEquals("My process", pdList.get(0).get(1));
    assertEquals("1", pdList.get(0).get(2));  // version
    assertEquals("false", pdList.get(0).get(3));  // not suspended
    assertEquals("This process to test JMX", pdList.get(0).get(4));
    
    // redeploy the same process
    mbsc.invoke(mbeanName, "deployProcessDefinition", new String[]{"trivialProcess.bpmn", fileName.getFile()}, new String[]{String.class.getName(), String.class.getName()});

   
    // now there should be two deployments
    deployments = (List<List<String>>) mbsc.getAttribute(mbeanName, "Deployments");
    assertNotNull(deployments);
    assertEquals(2, deployments.size());
    assertEquals(3, deployments.get(0).size());
    assertEquals(3, deployments.get(1).size());
    
    // there should be two process definitions, one with version equals to two
    pdList = (List<List<String>>) mbsc.getAttribute(mbeanName, "ProcessDefinitions");
    assertNotNull(pdList);
    assertEquals(2, pdList.size());
    assertEquals(5, pdList.get(0).size());
    assertEquals(5, pdList.get(1).size());
    
    // check there is one with version= = 1 and another one with version == 2, other attributed are the same
    String pidV2 = null;
    String pidV1 = null;
    if (pdList.get(0).get(2).equals("1") && pdList.get(1).get(2).equals("2")) {
      pidV2 = pdList.get(1).get(0);
      pidV1 = pdList.get(0).get(0);
    } else if  (pdList.get(1).get(2).equals("1") && pdList.get(0).get(2).equals("2")) {
      pidV2 = pdList.get(0).get(0);
      pidV1 = pdList.get(1).get(0);
      
    } else 
      fail("there should one process definition with version == 1 and another one with version == 2. It is not the case");
    
    assertNotNull(pdList.get(0).get(0));
    assertNotNull(pdList.get(1).get(0));
    assertEquals("My process", pdList.get(0).get(1));
    assertEquals("My process", pdList.get(1).get(1));
    assertEquals("false", pdList.get(0).get(3));  // not suspended
    assertEquals("false", pdList.get(1).get(3));  // not suspended
    assertEquals("This process to test JMX", pdList.get(0).get(4));
    assertEquals("This process to test JMX", pdList.get(1).get(4));
    
    //suspend the one with version == 2    
    mbsc.invoke(mbeanName, "suspendProcessDefinitionById", new String[]{pidV2}, new String[]{String.class.getName()});
    RepositoryService repositoryService = processEngine.getRepositoryService();
    
    // test if it is realy suspended and not the other one
    assertTrue(repositoryService.getProcessDefinition(pidV2).isSuspended());
    assertFalse(repositoryService.getProcessDefinition(pidV1).isSuspended());
    
    // test if it is reported as suspended and not the other one
    List<String> pd = (List<String>) mbsc.invoke(mbeanName, "getProcessDefinitionById", new String[]{pidV2}, new String[]{String.class.getName()});
    assertNotNull(pd);
    assertEquals(5, pd.size());
    assertEquals("true", pd.get(3));
    

    pd = (List<String>) mbsc.invoke(mbeanName, "getProcessDefinitionById", new String[]{pidV1}, new String[]{String.class.getName()});
    assertNotNull(pd);
    assertEquals(5, pd.size());
    assertEquals("false", pd.get(3));
    
    
    // now reactivate the same suspended process 
    mbsc.invoke(mbeanName, "activatedProcessDefinitionById", new String[]{pidV2}, new String[]{String.class.getName()});

    // test if both processes are active again
    assertFalse(repositoryService.getProcessDefinition(pidV2).isSuspended());
    assertFalse(repositoryService.getProcessDefinition(pidV1).isSuspended());
    
    // test if they are properly reported as activated
    
    pd = (List<String>) mbsc.invoke(mbeanName, "getProcessDefinitionById", new String[]{pidV2}, new String[]{String.class.getName()});
    assertNotNull(pd);
    assertEquals(5, pd.size());
    assertEquals("false", pd.get(3));
    

    pd = (List<String>) mbsc.invoke(mbeanName, "getProcessDefinitionById", new String[]{pidV1}, new String[]{String.class.getName()});
    assertNotNull(pd);
    assertEquals(5, pd.size());
    assertEquals("false", pd.get(3));
    


    // now undeploy the one with version == 1
    mbsc.invoke(mbeanName, "deleteDeployment", new String[]{firstDeploymentId}, new String[]{String.class.getName()});
    
    // now there should be only one deployment and only one process definition with version 2, first check it with API
    assertEquals(1, repositoryService.createDeploymentQuery().count());
    
    assertThat(repositoryService.createDeploymentQuery().singleResult().getId(), not(firstDeploymentId));


    // check if it is also affected in returned results.
    
    deployments = (List<List<String>>) mbsc.getAttribute(mbeanName, "Deployments");
    assertNotNull(deployments);
    assertEquals(1, deployments.size());
    assertEquals(3, deployments.get(0).size());
    assertThat(deployments.get(0).get(0), not(firstDeploymentId));
    
  }

}
