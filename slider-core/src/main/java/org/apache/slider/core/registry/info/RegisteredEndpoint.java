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

package org.apache.slider.core.registry.info;

import org.apache.slider.core.exceptions.SliderException;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class RegisteredEndpoint {

  // standard types

  /**
   * URL: {@value}
   */
  public static final String TYPE_URL = "url";
  
  /**
   * hostname: {@value}
   */
  public static final String TYPE_HOSTNAME = "hostname";
  
  /**
   * "hostname:port" pair: {@value}
   */
  public static final String TYPE_INETADDRESS = "inetaddress";
  
  /**
   * simple path string: {@value}
   */
  public static final String TYPE_PATH = "path";

  // standard protocols

  /**
   * Generic TCP protocol: {@value}
   */

  public static final String PROTOCOL_TCP = "tcp";

  /**
   * Generic TCP protocol: {@value}
   */

  public static final String PROTOCOL_UDP = "udp";

  /**
   * HTTP: {@value}
   */

  public static final String PROTOCOL_HTTP = "http";

  /**
   * HTTPS: {@value}
   */

  public static final String PROTOCOL_HTTPS = "http";

  /**
   * Classic "Writable" Hadoop IPC: {@value}
   */
  public static final String PROTOCOL_HADOOP_RPC = "org.apache.hadoop.ipc.RPC";

  /**
   * Protocol buffer based Hadoop IPC: {@value}
   */
  public static final String PROTOCOL_HADOOP_PROTOBUF = "org.apache.hadoop.ipc.Protobuf";

  /**
   * The address -format is driven by the type entry
   */
  public String address;

  /**
   * Protocol
   */
  public String protocol = "";

  public String type = "";

  /**
   * Human readable type
   */
  public String description = "";
  
  public RegisteredEndpoint() {
  }

  public RegisteredEndpoint(String address,
                            String protocol,
                            String type,
                            String description) {
    this.address = address;
    this.protocol = protocol;
    this.type = type;
    this.description = description;
  }

  /**
   * Build an endpoint instance from a URI, extracting
   * the protocol from it
   * @param uri URI to set the value to
   * @param description description
   */
  public RegisteredEndpoint(URI uri,
                            String description) {
    
    this.address = uri.toString();
    this.protocol = uri.getScheme();
    this.type = TYPE_URL;
    this.description = description;
  }
  /**
   * Build an endpoint instance from a URI, extracting
   * the protocol from it
   * @param uri URI to set the value to
   * @param description description
   */
  public RegisteredEndpoint(InetSocketAddress address,
    String protocol,
      String description) {
    
    this.address = address.toString();
    this.protocol = protocol;
    this.type = TYPE_INETADDRESS;
    this.description = description;
  }

  /**
   * Build an endpoint instance from a URL, extracting
   * the protocol from it
   * @param url URL to set the value to
   * @param description description
   */
  public RegisteredEndpoint(URL url,
                            String description) throws URISyntaxException {
    this(url.toURI(), description);
  }

  /**
   * Get the value as a URL
   * @return  URL of the value -if the value type is URL
   * @throws SliderException if the value is of the wrong type, or unparsable
   */
  public URL asURL() throws SliderException {
    verifyEndpointType(TYPE_URL);
    try {
      return new URL(address);
    } catch (MalformedURLException e) {
      throw new SliderException(-1, e,
          "could not create a URL from %s : %s", address, e.toString());
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb =
        new StringBuilder();
    if (TYPE_URL.equals(type)) {
      sb.append(address);
    } else {
      sb.append("protocol='").append(protocol).append('\'');
      sb.append(" address='").append(address).append('\'');
      sb.append(" type='").append(type).append('\'');
    }
    sb.append(" -- \"").append(description).append('"');
    return sb.toString();
  }


  /**
   * Verify that an endpoint is of the desired type
   * @param desiredType desired type
   * @throws SliderException if it is not
   */
  public void verifyEndpointType(String desiredType) throws SliderException {
    if (!type.equals(desiredType)) {
      throw new SliderException(-1, "Body of endpoint is of type %s and not %s",
          type, desiredType);
    }
  }
}
