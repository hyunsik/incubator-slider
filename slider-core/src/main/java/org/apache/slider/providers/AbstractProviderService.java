/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.slider.providers;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.service.Service;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.client.api.AMRMClient;
import org.apache.slider.api.ClusterDescription;
import org.apache.slider.common.SliderKeys;
import org.apache.slider.common.tools.ConfigHelper;
import org.apache.slider.common.tools.SliderFileSystem;
import org.apache.slider.common.tools.SliderUtils;
import org.apache.slider.core.conf.AggregateConf;
import org.apache.slider.core.exceptions.BadCommandArgumentsException;
import org.apache.slider.core.exceptions.SliderException;
import org.apache.slider.core.main.ExitCodeProvider;
import org.apache.slider.core.registry.info.RegisteredEndpoint;
import org.apache.slider.core.registry.info.ServiceInstanceData;
import org.apache.slider.server.appmaster.actions.QueueAccess;
import org.apache.slider.server.appmaster.state.ContainerReleaseSelector;
import org.apache.slider.server.appmaster.state.MostRecentContainerReleaseSelector;
import org.apache.slider.server.appmaster.state.StateAccessForProviders;
import org.apache.slider.server.appmaster.web.rest.agent.AgentRestOperations;
import org.apache.slider.server.services.registry.RegistryViewForProviders;
import org.apache.slider.server.services.workflow.ForkedProcessService;
import org.apache.slider.server.services.workflow.ServiceParent;
import org.apache.slider.server.services.workflow.WorkflowSequenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The base class for provider services. It lets the implementations
 * add sequences of operations, and propagates service failures
 * upstream
 */
public abstract class AbstractProviderService
    extends WorkflowSequenceService
    implements
    ProviderCore,
    SliderKeys,
    ProviderService {
  private static final Logger log =
    LoggerFactory.getLogger(AbstractProviderService.class);
  protected StateAccessForProviders amState;
  protected AgentRestOperations restOps;
  protected RegistryViewForProviders registry;
  protected ServiceInstanceData registryInstanceData;
  protected URL amWebAPI;
  protected QueueAccess queueAccess;

  public AbstractProviderService(String name) {
    super(name);
  }

  @Override
  public Configuration getConf() {
    return getConfig();
  }

  public StateAccessForProviders getAmState() {
    return amState;
  }

  public QueueAccess getQueueAccess() {
    return queueAccess;
  }

  public void setAmState(StateAccessForProviders amState) {
    this.amState = amState;
  }

  @Override
  public void bind(StateAccessForProviders stateAccessor,
      RegistryViewForProviders reg,
      QueueAccess queueAccess,
      List<Container> liveContainers) {
    this.amState = stateAccessor;
    this.registry = reg;
    this.queueAccess = queueAccess;
  }

  @Override
  public AgentRestOperations getAgentRestOperations() {
    return restOps;
  }

  @Override
  public void notifyContainerCompleted(ContainerId containerId) {
  }

  public void setAgentRestOperations(AgentRestOperations agentRestOperations) {
    this.restOps = agentRestOperations;
  }

  /**
   * Load a specific XML configuration file for the provider config
   * @param confDir configuration directory
   * @param siteXMLFilename provider-specific filename
   * @return a configuration to be included in status
   * @throws BadCommandArgumentsException argument problems
   * @throws IOException IO problems
   */
  protected Configuration loadProviderConfigurationInformation(File confDir,
                                                               String siteXMLFilename)
    throws BadCommandArgumentsException, IOException {
    Configuration siteConf;
    File siteXML = new File(confDir, siteXMLFilename);
    if (!siteXML.exists()) {
      throw new BadCommandArgumentsException(
        "Configuration directory %s doesn't contain %s - listing is %s",
        confDir, siteXMLFilename, SliderUtils.listDir(confDir));
    }

    //now read it in
    siteConf = ConfigHelper.loadConfFromFile(siteXML);
    log.info("{} file is at {}", siteXMLFilename, siteXML);
    log.info(ConfigHelper.dumpConfigToString(siteConf));
    return siteConf;
  }

  /**
   * No-op implementation of this method.
   */
  @Override
  public void initializeApplicationConfiguration(
      AggregateConf instanceDefinition, SliderFileSystem fileSystem)
      throws IOException, SliderException {
  }

  /**
   * No-op implementation of this method.
   *
   * {@inheritDoc}
   */
  @Override
  public void validateApplicationConfiguration(AggregateConf instance,
                                               File confDir,
                                               boolean secure) throws
      IOException,
      SliderException {

  }

  /**
   * Scan through the roles and see if it is supported.
   * @param role role to look for
   * @return true if the role is known about -and therefore
   * that a launcher thread can be deployed to launch it
   */
  @Override
  public boolean isSupportedRole(String role) {
    Collection<ProviderRole> roles = getRoles();
    for (ProviderRole providedRole : roles) {
      if (providedRole.name.equals(role)) {
        return true;
      }
    }
    return false;
  }
  
  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Override // ExitCodeProvider
  public int getExitCode() {
    Throwable cause = getFailureCause();
    if (cause != null) {
      //failed for some reason
      if (cause instanceof ExitCodeProvider) {
        return ((ExitCodeProvider) cause).getExitCode();
      }
    }
    ForkedProcessService lastProc = latestProcess();
    if (lastProc == null || !lastProc.isProcessTerminated()) {
      return 0;
    } else {
      return lastProc.getExitCode();
    }
  }

  /**
   * Return the latest forked process service that ran
   * @return the forkes service
   */
  protected ForkedProcessService latestProcess() {
    Service current = getActiveService();
    Service prev = getPreviousService();

    Service latest = current != null ? current : prev;
    if (latest instanceof ForkedProcessService) {
      return (ForkedProcessService) latest;
    } else {
      //its a composite object, so look inside it for a process
      if (latest instanceof ServiceParent) {
        return getFPSFromParentService((ServiceParent) latest);
      } else {
        //no match
        return null;
      }
    }
  }


  /**
   * Given a parent service, find the one that is a forked process
   * @param serviceParent parent
   * @return the forked process service or null if there is none
   */
  protected ForkedProcessService getFPSFromParentService(ServiceParent serviceParent) {
    List<Service> services = serviceParent.getServices();
    for (Service s : services) {
      if (s instanceof ForkedProcessService) {
        return (ForkedProcessService) s;
      }
    }
    return null;
  }

  /**
   * if we are already running, start this service
   */
  protected void maybeStartCommandSequence() {
    if (isInState(STATE.STARTED)) {
      startNextService();
    }
  }

  /**
   * Create a new forked process service with the given
   * name, environment and command list -then add it as a child
   * for execution in the sequence.
   *
   * @param name command name
   * @param env environment
   * @param commands command line
   * @throws IOException
   * @throws SliderException
   */
  protected ForkedProcessService queueCommand(String name,
                              Map<String, String> env,
                              List<String> commands) throws
                                                     IOException,
      SliderException {
    ForkedProcessService process = buildProcess(name, env, commands);
    //register the service for lifecycle management; when this service
    //is terminated, so is the master process
    addService(process);
    return process;
  }

  public ForkedProcessService buildProcess(String name,
                                           Map<String, String> env,
                                           List<String> commands) throws
                                                                  IOException,
      SliderException {
    ForkedProcessService process;
    process = new ForkedProcessService(name);
    process.init(getConfig());
    process.build(env, commands);
    return process;
  }

  /*
   * Build the provider status, can be empty
   * @return the provider status - map of entries to add to the info section
   */
  @Override
  public Map<String, String> buildProviderStatus() {
    return new HashMap<String, String>();
  }

  /*
  Build the monitor details. The base implementation includes all the external URL endpoints
  in the external view
   */
  @Override
  public Map<String, String> buildMonitorDetails(ClusterDescription clusterDesc) {
    Map<String, String> details = new LinkedHashMap<String, String>();

    // add in all the 
    buildEndpointDetails(details);

    return details;
  }
  
  protected String getInfoAvoidingNull(ClusterDescription clusterDesc, String key) {
    String value = clusterDesc.getInfo(key);

    return null == value ? "N/A" : value;
  }

  @Override
  public void buildEndpointDetails(Map<String, String> details) {
      ServiceInstanceData self = registry.getSelfRegistration();
    buildEndpointDetails(details, self);
  }

  public static void buildEndpointDetails(Map<String, String> details,
      ServiceInstanceData self) {
    Map<String, RegisteredEndpoint> endpoints =
        self.getRegistryView(true).endpoints;
    for (Map.Entry<String, RegisteredEndpoint> endpoint : endpoints.entrySet()) {
      RegisteredEndpoint val = endpoint.getValue();
      if (val.type.equals(RegisteredEndpoint.TYPE_URL)) {
          details.put(val.description, val.address);
      }
    }
  }
  @Override
  public void applyInitialRegistryDefinitions(URL unsecureWebAPI,
      URL secureWebAPI,
      ServiceInstanceData registryInstanceData) throws IOException {

      this.amWebAPI = unsecureWebAPI;
    this.registryInstanceData = registryInstanceData;
  }

  /**
   * {@inheritDoc}
   * 
   * 
   * @return The base implementation returns the most recent containers first.
   */
  @Override
  public ContainerReleaseSelector createContainerReleaseSelector() {
    return new MostRecentContainerReleaseSelector();
  }

  @Override
  public void releaseAssignedContainer(ContainerId containerId) {
    // no-op
  }

  @Override
  public void addContainerRequest(AMRMClient.ContainerRequest req) {
    // no-op
  }

  /**
   * No-op implementation of this method.
   */
  @Override
  public void rebuildContainerDetails(List<Container> liveContainers,
      String applicationId, Map<Integer, ProviderRole> providerRoles) {
  }
}
