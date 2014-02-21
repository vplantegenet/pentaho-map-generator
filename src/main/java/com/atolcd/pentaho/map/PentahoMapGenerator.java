package com.atolcd.pentaho.map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * RepositoryFilePublisher provides public method for being used as WebService by ws-content pentaho system.
 */
public class PentahoMapGenerator {

  private static final Log logger = LogFactory.getLog(PentahoMapGenerator.class);

  /**
   * Default constructor
   */
  public PentahoMapGenerator() {
  }

  /**
   * Public method used as a webservice.
   */
  public String getGeoms() {
    throw new UnsupportedOperationException();
  }
}
