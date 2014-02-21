package com.atolcd.pentaho.map;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.Fill;
import org.geotools.styling.Graphic;
import org.geotools.styling.LineSymbolizer;
import org.geotools.styling.Mark;
import org.geotools.styling.PointSymbolizer;
import org.geotools.styling.PolygonSymbolizer;
import org.geotools.styling.Rule;
import org.geotools.styling.SLDParser;
import org.geotools.styling.Stroke;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.expression.Literal;

public class StyleUtils {

  private static StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory(null);
  private static FilterFactory filterFactory = CommonFactoryFinder.getFilterFactory(null);

  public static Color hex2Rgb(String colorStr) {
    return new Color(
        Integer.valueOf(colorStr.substring(0, 2), 16),
        Integer.valueOf(colorStr.substring(2, 4), 16),
        Integer.valueOf(colorStr.substring(4), 16));
  }

  public static Style getStyleFromSLD(String fileName) throws FileNotFoundException {
    File fi = new File(fileName);
    SLDParser parser = new SLDParser(styleFactory, fi);
    Style[] styles = parser.readXML();
    return styles[0];
  }

  public static Style getFixedColorStyle(String[][] items, int colorIndex) {

    Style style = styleFactory.createStyle();

    Color cTrait = null;
    Color cFond = null;

    cTrait = StyleUtils.hex2Rgb("000000");
    cFond = StyleUtils.hex2Rgb("FF0000");

    float[] dashArray = null;

    Integer opaciteTrait = 100;
    double epaisseurTrait = 0.5;
    Integer opaciteFond = 50;

    double opLigne = opaciteTrait / 100.0;
    Stroke stroke = styleFactory.createStroke(
        filterFactory.literal(cTrait),
        filterFactory.literal(epaisseurTrait));
    stroke.setDashArray(dashArray);
    stroke.setOpacity(filterFactory.literal(opLigne));
    double opFond = opaciteFond / 100.0;

    LineSymbolizer sym = styleFactory.createLineSymbolizer(stroke, null);

    Mark mark = styleFactory.getCircleMark();

    mark.setStroke(styleFactory.createStroke(
        filterFactory.literal(cTrait), filterFactory.literal(epaisseurTrait)));
    mark.setFill(styleFactory.createFill(filterFactory.literal(cFond)));

    // gestion des styles de point (simple, picto ou miniature)
    Graphic gr = styleFactory.createDefaultGraphic();

    gr.graphicalSymbols().clear();
    gr.graphicalSymbols().add(mark);
    gr.setSize(filterFactory.literal(epaisseurTrait));

    PointSymbolizer symPoint = styleFactory.createPointSymbolizer();
    symPoint.setGraphic(gr);

    Set<Rule> rulesList = new HashSet<Rule>();

    for (int i = 1; i < items.length; i++) {

      // Ajout des règles
      Rule rule = styleFactory.createRule();

      Fill fill = styleFactory.createFill(
          filterFactory.literal(StyleUtils.hex2Rgb(items[i][colorIndex].replaceAll("#", ""))),
          filterFactory.literal(opFond));

      PolygonSymbolizer symPolygon = styleFactory.createPolygonSymbolizer(stroke, fill, null);

      Filter fil = filterFactory.equals(filterFactory.property(items[0][colorIndex]),
          filterFactory.literal(items[i][colorIndex]));

      rule.setFilter(fil);

      // Polygon
      rule.symbolizers().add(symPolygon);

      // Line
      // rule.symbolizers().add(sym);

      // point
      // rule.symbolizers().add(symPoint);

      rulesList.add(rule);
    }

    FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle(rulesList.toArray(new Rule[0]));
    style.featureTypeStyles().add(fts);

    return style;
  }

  public static Style getLinearGradientStyle(String[][] items, int valueIndex, String minColor, String maxColor,
      int intervals) {

    List<Double> values = new ArrayList<Double>();

    for (int i = 1; i < items.length; i++) {
      values.add(Double.parseDouble(items[i][valueIndex]));
    }

    String[] colors = makeLinearColorGradient(minColor, maxColor, intervals);
    double max = Collections.max(values);
    double min = Collections.min(values) - 1;
    double interval = max - min;

    Style style = styleFactory.createStyle();

    Color cTrait = StyleUtils.hex2Rgb("000000");

    float[] dashArray = null;

    Integer opaciteTrait = 100;
    double epaisseurTrait = 0.5;
    Integer opaciteFond = 50;

    double opLigne = opaciteTrait / 100.0;
    Stroke stroke = styleFactory.createStroke(
        filterFactory.literal(cTrait),
        filterFactory.literal(epaisseurTrait));
    stroke.setDashArray(dashArray);
    stroke.setOpacity(filterFactory.literal(opLigne));
    double opFond = opaciteFond / 100.0;

    Set<Rule> rulesList = new HashSet<Rule>();

    for (int i = 0; i < colors.length; i++) {

      // Ajout des règles
      Rule rule = styleFactory.createRule();

      Fill fill = styleFactory.createFill(
          filterFactory.literal(StyleUtils.hex2Rgb(colors[i].replaceAll("#", ""))),
          filterFactory.literal(opFond));

      PolygonSymbolizer symPolygon = styleFactory.createPolygonSymbolizer(stroke, fill, null);

      Literal lower = filterFactory.literal(min + i * (interval / intervals));
      Literal upper = filterFactory.literal(min + (i + 1) * (interval / intervals));
      int method = 0;
      int n = 3;

      if (method == 1) {
        lower =
          filterFactory.literal(Math.floor(Math.round(min)
            + (Math.round((interval) * Math.pow(i, n)) / (Math.pow(intervals, n)))));
        upper =
          filterFactory.literal(Math.floor(Math.round(min)
            + (Math.round((interval) * Math.pow(i + 1, n)) / (Math.pow(intervals, n)))));
      }
      if (method == 2) {
        lower =
          filterFactory.literal(Math.floor(Math.round(min)
            + (Math.round((interval) * Math.log(i)) / (Math.log(intervals)))));
        upper =
          filterFactory.literal(Math.floor(Math.round(min)
            + (Math.round((interval) * Math.log(i + 1)) / (Math.log(intervals)))));
      }

      Filter fil =
        filterFactory.between(filterFactory.property(items[0][valueIndex]), lower, upper);

      rule.setFilter(fil);

      // Polygon
      rule.symbolizers().add(symPolygon);

      rulesList.add(rule);
    }

    FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle(rulesList.toArray(new Rule[0]));
    style.featureTypeStyles().add(fts);

    return style;

  }

  private static String[] makeLinearColorGradient(String minColor, String maxColor, int intervals) {

    int[] minRgb = HTMLtoRGB(minColor);
    int[] maxRgb = HTMLtoRGB(maxColor);

    String[] gradient = new String[intervals];

    for (int i = 0; i < intervals; i++) {

      double intervals2 = intervals;
      double i2 = i;
      double pos = i2 / (intervals2 - 1.0);
      int[] intervalRgb = new int[3];

      intervalRgb[0] = (int) (Math.floor(minRgb[0] * (1.0 - pos) + maxRgb[0] * pos));
      intervalRgb[1] = (int) (Math.floor(minRgb[1] * (1.0 - pos) + maxRgb[1] * pos));
      intervalRgb[2] = (int) (Math.floor(minRgb[2] * (1.0 - pos) + maxRgb[2] * pos));

      gradient[i] = RGBtoHTML(intervalRgb);
    }

    return gradient;
  }

  private static int[] HTMLtoRGB(String html) {

    int[] rgb = new int[3];

    rgb[0] = Integer.parseInt(html.substring(1, 3), 16);
    rgb[1] = Integer.parseInt(html.substring(3, 5), 16);
    rgb[2] = Integer.parseInt(html.substring(5, 7), 16);

    return rgb;
  }

  private static String RGBtoHTML(int[] rgb) {

    String sr[] = new String[3];
    sr[0] = Integer.toHexString(rgb[0]);
    sr[1] = Integer.toHexString(rgb[1]);
    sr[2] = Integer.toHexString(rgb[2]);

    for (int y = 0; y < sr.length; y++) {
      if (sr[y].length() != 2) {
        sr[y] = "0" + sr[y];
      }
    }

    StringBuffer hexstr = new StringBuffer("#").append(sr[0]).append(sr[1]).append(sr[2]);

    return hexstr.toString();
  }
}
