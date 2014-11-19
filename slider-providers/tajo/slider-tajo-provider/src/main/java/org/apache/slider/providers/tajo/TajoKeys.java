package org.apache.slider.providers.tajo;/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

public interface TajoKeys {

  /** {@value */
  String WORKER = "worker";
  /** {@value */
  String MASTER = "master";

  String ROLE_WORKER = WORKER;
  String ROLE_MASTER = MASTER;

  String APPCONF_HADOOP_HOME = "hadoop.home";

  /**
   * What is the command for hbase to print a version: {@value}
   */
  String COMMAND_VERSION = "version";

  String ACTION_START = "start";
  String ACTION_STOP = "stop";

  /**
   * Config directory : {@value}
   */
  String ARG_CONFIG = "--config";
  /**
   *  name of the hbase script relative to the hbase root dir:  {@value}
   */
  String TAJO_SCRIPT = "tajo";
  
  /**
   *  name of the site conf to generate :  {@value}
   */
  String SITE_XML = "tajo-site.xml";
  /**
   * Template stored in the slider classpath -to use if there is
   * no site-specific template
   *  {@value}
   */
  String TAJO_CONF_RESOURCE = "org/apache/slider/providers/tajo/conf/";
  String HBASE_TEMPLATE_RESOURCE = TAJO_CONF_RESOURCE + SITE_XML;


  // For tajo-env.sh
  String HADOOP_HOME = "HADOOP_HOME";
  String TAJO_LOG_DIR = "TAJO_LOG_DIR";

  String TAJO_HEAPSIZE = "TAJO_HEAPSIZE";
  String TAJO_GC_OPTS = "SERVER_GC_OPTS";

  String PROPAGATED_CONFDIR = "PROPAGATED_CONFDIR";

  /**
   * Service type used in registry
   */
  String TAJO_SERVICE_TYPE = "org-apache-tajo";
  String TAJO_SITE_PUBLISHED_CONFIG = "tajo-site";
}


