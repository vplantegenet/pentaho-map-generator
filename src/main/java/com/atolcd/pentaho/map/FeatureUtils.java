package com.atolcd.pentaho.map;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Geometry;

public class FeatureUtils {

  public static SimpleFeatureBuilder getDefaultFeatureBuilder() {
    return getFeatureBuilder(false);
  }

  public static SimpleFeatureBuilder getUidAwareFeatureBuilder(boolean addUid) {
    return getFeatureBuilder(true);
  }

  public static SimpleFeatureBuilder getFeatureBuilder(boolean addUid) {
    SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
    builder.setName("items");

    // add attributes in order
    builder.add("geom", Geometry.class);

    if (addUid) {
      builder.add("uid", String.class);
    }

    SimpleFeatureType schema = builder.buildFeatureType();
    return new SimpleFeatureBuilder(schema);
  }

  public static SimpleFeatureBuilder getFullFeatureBuilder(String[] header, int geomIndex) {
    SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
    builder.setName("items");

    for (int i = 0; i < header.length; i++) {
      if (i != geomIndex) {
        builder.add(header[i], String.class);
      }
    }

    // add attributes in order
    builder.add("geom", Geometry.class);
    builder.add("uid", String.class);

    SimpleFeatureType schema = builder.buildFeatureType();
    return new SimpleFeatureBuilder(schema);
  }

}
