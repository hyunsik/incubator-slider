package org.apache.slider.providers.tajo;/*
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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.slider.api.InternalKeys;
import org.apache.slider.api.OptionKeys;
import org.apache.slider.common.SliderKeys;
import org.apache.slider.common.SliderXmlConfKeys;
import org.apache.slider.common.tools.ConfigHelper;
import org.apache.slider.common.tools.SliderFileSystem;
import org.apache.slider.common.tools.SliderUtils;
import org.apache.slider.core.conf.AggregateConf;
import org.apache.slider.core.conf.ConfTreeOperations;
import org.apache.slider.core.conf.MapOperations;
import org.apache.slider.core.exceptions.BadCommandArgumentsException;
import org.apache.slider.core.exceptions.BadConfigException;
import org.apache.slider.core.exceptions.SliderException;
import org.apache.slider.core.launch.AbstractLauncher;
import org.apache.slider.core.zk.ZookeeperUtils;
import org.apache.slider.providers.AbstractClientProvider;
import org.apache.slider.providers.ProviderRole;
import org.apache.slider.providers.ProviderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * This class implements  the client-side aspects
 * of an Tajo Cluster
 */
public class TajoClientProvider extends AbstractClientProvider implements
    TajoKeys, SliderKeys,
    TajoConfigFileOptions {

  
  protected static final Logger log =
    LoggerFactory.getLogger(TajoClientProvider.class);
  protected static final String NAME = "tajo";
  private static final String INSTANCE_RESOURCE_BASE = "/org/apache/slider/providers/tajo/instance/";


  protected TajoClientProvider(Configuration conf) {
    super(conf);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public List<ProviderRole> getRoles() {
    return TajoRoles.getRoles();
  }


  @Override
  public void prepareInstanceConfiguration(AggregateConf aggregateConf) throws
      SliderException,
                                                              IOException {
    String resourceTemplate = INSTANCE_RESOURCE_BASE + "resources.json";
    String appConfTemplate = INSTANCE_RESOURCE_BASE + "appconf.json";
    mergeTemplates(aggregateConf, null, resourceTemplate, appConfTemplate);
  }

  /**
   * Build the hdfs-site.xml file
   * This the configuration used by HBase directly
   * @param instanceDescription this is the cluster specification used to define this
   * @return a map of the dynamic bindings for this Slider instance
   */
  public Map<String, String> buildSiteConfFromInstance(
    AggregateConf instanceDescription)
    throws BadConfigException {


    ConfTreeOperations appconf =
      instanceDescription.getAppConfOperations();

    MapOperations globalAppOptions = appconf.getGlobalOptions();
    MapOperations globalInstanceOptions = instanceDescription.getInternalOperations().getGlobalOptions();
    MapOperations master = appconf.getMandatoryComponent(TajoKeys.ROLE_MASTER);

    MapOperations worker = appconf.getMandatoryComponent(TajoKeys.ROLE_WORKER);
    
    Map<String, String> sitexml = new HashMap<String, String>();

    //map all cluster-wide site. options
    providerUtils.propagateSiteOptions(globalAppOptions, sitexml);
/*
  //this is where we'd do app-indepdenent keytabs

    String keytab =
      clusterSpec.getOption(OptionKeys.OPTION_KEYTAB_LOCATION, "");
    
*/


    sitexml.put(KEY_HBASE_ROOTDIR,
        globalInstanceOptions.getMandatoryOption(
            InternalKeys.INTERNAL_DATA_DIR_PATH)
    );
    providerUtils.propagateOption(globalAppOptions, OptionKeys.ZOOKEEPER_PATH,
                                  sitexml, KEY_ZNODE_PARENT);
    String quorum =
      globalAppOptions.getMandatoryOption(OptionKeys.ZOOKEEPER_QUORUM);
    sitexml.put(KEY_ZOOKEEPER_QUORUM, ZookeeperUtils.convertToHostsOnlyList(quorum));
    //and assume first port works everywhere
    sitexml.put(KEY_ZOOKEEPER_PORT,
      Integer.toString(ZookeeperUtils.getFirstPort(quorum, HBASE_ZK_PORT)));

    return sitexml;
  }

  @Override //Client
  public void preflightValidateClusterConfiguration(SliderFileSystem sliderFileSystem,
                                                    String clustername,
                                                    Configuration configuration,
                                                    AggregateConf instanceDefinition,
                                                    Path clusterDirPath,
                                                    Path generatedConfDirPath,
                                                    boolean secure) throws
      SliderException,
                                                                    IOException {
    super.preflightValidateClusterConfiguration(sliderFileSystem, clustername,
                                                configuration,
                                                instanceDefinition,
                                                clusterDirPath,
                                                generatedConfDirPath, secure);

    Path templatePath = new Path(generatedConfDirPath, TajoKeys.SITE_XML);
    //load the HBase site file or fail
    Configuration siteConf = ConfigHelper.loadConfiguration(sliderFileSystem.getFileSystem(),
                                                            templatePath);

    //core customizations
    validateHBaseSiteXML(siteConf, secure, templatePath.toString());

  }

  /**
   * Validate the hbase-site.xml values
   * @param siteConf site config
   * @param secure secure flag
   * @param origin origin for exceptions
   * @throws org.apache.slider.core.exceptions.BadConfigException if a config is missing/invalid
   */
  public void validateHBaseSiteXML(Configuration siteConf,
                                    boolean secure,
                                    String origin) throws BadConfigException {
    try {
      SliderUtils.verifyOptionSet(siteConf, KEY_HBASE_CLUSTER_DISTRIBUTED,
          false);
      SliderUtils.verifyOptionSet(siteConf, KEY_HBASE_ROOTDIR, false);
      SliderUtils.verifyOptionSet(siteConf, KEY_ZNODE_PARENT, false);
      SliderUtils.verifyOptionSet(siteConf, KEY_ZOOKEEPER_QUORUM, false);
      SliderUtils.verifyOptionSet(siteConf, KEY_ZOOKEEPER_PORT, false);
      int zkPort =
        siteConf.getInt(TajoConfigFileOptions.KEY_ZOOKEEPER_PORT, 0);
      if (zkPort == 0) {
        throw new BadConfigException(
          "ZK port property not provided at %s in configuration file %s",
          TajoConfigFileOptions.KEY_ZOOKEEPER_PORT,
          siteConf);
      }

      if (secure) {
        //better have the secure cluster definition up and running
        SliderUtils.verifyOptionSet(siteConf, KEY_MASTER_KERBEROS_PRINCIPAL,
            false);
        SliderUtils.verifyOptionSet(siteConf, KEY_MASTER_KERBEROS_KEYTAB,
            false);
        SliderUtils.verifyOptionSet(siteConf,
            KEY_REGIONSERVER_KERBEROS_PRINCIPAL,
            false);
        SliderUtils.verifyOptionSet(siteConf,
            KEY_REGIONSERVER_KERBEROS_KEYTAB, false);
      }
    } catch (BadConfigException e) {
      //bad configuration, dump it

      log.error("Bad site configuration {} : {}", origin, e, e);
      log.info(ConfigHelper.dumpConfigToString(siteConf));
      throw e;
    }
  }

  private static Set<String> knownRoleNames = new HashSet<String>();
  static {
    List<ProviderRole> roles = TajoRoles.getRoles();
    knownRoleNames.add(SliderKeys.COMPONENT_AM);
    for (ProviderRole role : roles) {
      knownRoleNames.add(role.name);
    }
  }
  
  /**
   * Validate the instance definition.
   * @param instanceDefinition instance definition
   */
  @Override
  public void validateInstanceDefinition(AggregateConf instanceDefinition, SliderFileSystem fs) throws
      SliderException {
    super.validateInstanceDefinition(instanceDefinition, fs);
    ConfTreeOperations resources =
      instanceDefinition.getResourceOperations();
    Set<String> unknownRoles = resources.getComponentNames();
    unknownRoles.removeAll(knownRoleNames);
    if (!unknownRoles.isEmpty()) {
      throw new BadCommandArgumentsException("Unknown component: %s",
                                             unknownRoles.iterator().next());
    }
    providerUtils.validateNodeCount(instanceDefinition, TajoKeys.ROLE_WORKER,
                                    0, -1);
    providerUtils.validateNodeCount(instanceDefinition, TajoKeys.ROLE_MASTER,
                                    0, -1);
  }

  @Override
  public void prepareAMAndConfigForLaunch(SliderFileSystem fileSystem,
      Configuration serviceConf,
      AbstractLauncher launcher,
      AggregateConf instanceDescription,
      Path snapshotConfDirPath,
      Path generatedConfDirPath,
      Configuration clientConfExtras,
      String libdir,
      Path tempPath, boolean miniClusterTestRun) throws
                                                         IOException,
      SliderException {

    // add any and all dependency files
    Map<String, LocalResource> providerResources =
        new HashMap<String, LocalResource>();

    ProviderUtils.addProviderJar(providerResources,
        this,
        "slider-hbase-provider.jar",
        fileSystem,
        tempPath,
        libdir, miniClusterTestRun);

    Class<?>[] classes = {
    };
    String[] jars =
        {
        };
    ProviderUtils.addDependencyJars(providerResources, fileSystem, tempPath,
        libdir, jars,
        classes);
    

    launcher.addLocalResources(providerResources);

    //load in the template site config
    log.debug("Loading template configuration from {}", snapshotConfDirPath);
    Configuration siteConf = ConfigHelper.loadTemplateConfiguration(
      serviceConf,
        snapshotConfDirPath,
      TajoKeys.SITE_XML,
      TajoKeys.HBASE_TEMPLATE_RESOURCE);

    if (log.isDebugEnabled()) {
      log.debug("Configuration came from {}",
                siteConf.get(SliderXmlConfKeys.KEY_TEMPLATE_ORIGIN));
      ConfigHelper.dumpConf(siteConf);
    }
    //construct the cluster configuration values

    ConfTreeOperations appconf =
      instanceDescription.getAppConfOperations();

    
    Map<String, String> clusterConfMap = buildSiteConfFromInstance(
      instanceDescription);

    //merge them
    ConfigHelper.addConfigMap(siteConf,
                              clusterConfMap.entrySet(),
                              "HBase Provider");

    //now, if there is an extra client conf, merge it in too
    if (clientConfExtras != null) {
      ConfigHelper.mergeConfigurations(siteConf, clientConfExtras,
                                       "Slider Client", true);
    }

    if (log.isDebugEnabled()) {
      log.debug("Merged Configuration");
      ConfigHelper.dumpConf(siteConf);
    }

    Path sitePath = ConfigHelper.saveConfig(serviceConf,
                                            siteConf,
                                            generatedConfDirPath,
                                            TajoKeys.SITE_XML);

    log.debug("Saving the config to {}", sitePath);
    launcher.submitDirectory(generatedConfDirPath,
                             SliderKeys.PROPAGATED_CONF_DIR_NAME);

  }


}
