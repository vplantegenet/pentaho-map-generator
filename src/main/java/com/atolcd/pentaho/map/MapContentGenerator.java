package com.atolcd.pentaho.map;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.image.TIFFTranscoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.map.MapViewport;
import org.geotools.referencing.CRS;
import org.geotools.renderer.RenderListener;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.Style;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.pentaho.platform.api.engine.ILogger;
import org.pentaho.platform.api.engine.IParameterProvider;
import org.pentaho.platform.api.engine.IPluginResourceLoader;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.engine.services.solution.SimpleContentGenerator;
import org.w3c.dom.Document;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.io.WKTReader;

/**
 * Content generator
 */
public class MapContentGenerator extends SimpleContentGenerator {

  private static final Log logger = LogFactory.getLog(MapContentGenerator.class);

  /**
   * Static MIME types
   */
  private static final String MIME_SVG = "image/svg+xml";
  private static final String MIME_JPG = "image/jpeg";
  private static final String MIME_PNG = "image/png";
  private static final String MIME_TIFF = "image/tiff";

  /**
   * Encoding used for reading parameters.
   */
  public static String ENCODING = "UTF-8";

  private String outputType;

  private SimpleFeatureBuilder getFeatureBuilder(String[] items, int geomIndex, int valueIndex,
      CoordinateReferenceSystem crs) {
    SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();

    builder.setCRS(crs);
    builder.setName("items");

    // add attributes in order
    builder.add("geom", Geometry.class);

    for (int i = 0; i < items.length; i++) {

      if (valueIndex != -1 && i == valueIndex) {
        builder.add(items[i], Double.class);
        continue;
      }

      if (i != geomIndex) {
        builder.add(items[i], String.class);
      }

    }

    SimpleFeatureType schema = builder.buildFeatureType();
    return new SimpleFeatureBuilder(schema);
  }

  private SVGGeneratorContext setupContext() throws ParserConfigurationException {
    Document document = null;

    DocumentBuilderFactory dbf = DocumentBuilderFactory
        .newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();

    // Create an instance of org.w3c.dom.Document
    document = db.getDOMImplementation().createDocument(null, "svg", null);

    // Set up the context
    SVGGeneratorContext ctx = SVGGeneratorContext
        .createDefault(document);

    return ctx;
  }

  @Override
  public void createContent(OutputStream out) throws Exception {

    // Load configfile
    IPluginResourceLoader resLoader = PentahoSystem.get(IPluginResourceLoader.class, null);

    InputStream in = resLoader.getResourceAsStream(MapContentGenerator.class, "config/PMG.properties");
    Properties config = new Properties();

    try {
      config.load(in);
    } catch (IOException e) {
      logger.error("Error while loading properties file", e);
    }

    config.list(System.out);

    int srid = Integer.parseInt(config.getProperty("srs", "2154"));
    String sldFile = config.getProperty("sldFile", "");

    CoordinateReferenceSystem crs = CRS.decode("EPSG:" + srid);

    // Getting url params
    final IParameterProvider requestParams = this.parameterProviders.get(IParameterProvider.SCOPE_REQUEST);
    this.outputType = requestParams.getStringParameter("outputType", "png");
    final int geomIndex = Integer.parseInt(requestParams.getStringParameter("geomIndex", "0"));
    final int valueIndex = Integer.parseInt(requestParams.getStringParameter("valueIndex", "-1"));
    final int colorIndex = Integer.parseInt(requestParams.getStringParameter("colorIndex", "2"));

    final String minColor = "#" + requestParams.getStringParameter("minColor", "FFFF00");
    final String maxColor = "#" + requestParams.getStringParameter("maxColor", "FF0000");
    final int intervals = Integer.parseInt(requestParams.getStringParameter("intervals", "10"));

    int wdt = Integer.parseInt(requestParams.getStringParameter("width", "400"));
    int hgt = Integer.parseInt(requestParams.getStringParameter("height", "400"));

    final boolean providedColors = Boolean.parseBoolean(requestParams.getStringParameter("providedColors", "false"));
    final boolean useSLD = Boolean.parseBoolean(requestParams.getStringParameter("useSLD", "false"));

    Iterator iter = requestParams.getParameterNames();
    Map<String, String> allParams = new HashMap<String, String>();

    while (iter.hasNext()) {

      String param = (String) iter.next();
      allParams.put(param, requestParams.getStringParameter(param, ""));

    }

    if (allParams.containsKey("outputType")) {
      allParams.remove("outputType");
    }

    // ouput type for cda
    allParams.put("outputType", "csv");

    String[][] items = DataProvider.getDataFromCda(allParams);

    // Initial map creation
    MapContent mapContent = new MapContent();

    GeometryFactory factory = new GeometryFactory(new PrecisionModel(), srid);

    WKTReader reader = new WKTReader(factory);

    SimpleFeatureBuilder builder = getFeatureBuilder(items[0], geomIndex, valueIndex, crs);

    // Feature collection to be added
    DefaultFeatureCollection col = new DefaultFeatureCollection();

    for (int i = 0; i < items.length; i++) {
      if (i > 0 && items[i][geomIndex] != null) {

        Geometry geom = reader.read(items[i][geomIndex]);
        SimpleFeature f = builder.buildFeature("item" + i);

        f.setAttribute("geom", geom);

        for (int j = 0; j < items[0].length; j++) {
          if (valueIndex != -1 && j == valueIndex) {
            f.setAttribute(items[0][j], Double.parseDouble(items[i][j]));
          }

          if (j != geomIndex) {
            f.setAttribute(items[0][j], items[i][j]);
          }
        }

        col.add(f);
      }
    }

    Style style;
    if (useSLD) {
      style = StyleUtils.getStyleFromSLD(sldFile);
    } else {
      style =
        (providedColors) ? StyleUtils.getFixedColorStyle(items, colorIndex) : StyleUtils.getLinearGradientStyle(items,
            valueIndex, minColor, maxColor, intervals);
    }

    FeatureLayer fl = new FeatureLayer(col, style);
    mapContent.addLayer(fl);

    // en mètres
    double finalHeight = 0.11;
    double finalwidth = 0.17;

    int scale = 5550000;

    // calcul des limites de la carte
    double ulcx = col.getBounds().centre().x - (finalwidth * scale / 2);
    double ulcy = col.getBounds().centre().y + (finalHeight * scale / 2);
    double drcx = col.getBounds().centre().x + (finalwidth * scale / 2);
    double drcy = col.getBounds().centre().y - (finalHeight * scale / 2);

    double imageHeight = col.getBounds().getHeight();
    double imageWidth = col.getBounds().getWidth();

    double ppp = 90.714;

    // bbox
    Coordinate limitHautGauche = new Coordinate(ulcx, ulcy);
    Coordinate limitBasDroite = new Coordinate(drcx, drcy);
    ReferencedEnvelope generalBbox = null;
    try {
      generalBbox = new ReferencedEnvelope(new Envelope(limitHautGauche, limitBasDroite), crs);
    } catch (MismatchedDimensionException e1) {

    }

    generalBbox = col.getBounds();

    mapContent.setViewport(new MapViewport(generalBbox));

    // setResponseHeaders(MIME_SVG, 0, "carte.svg");

    if ("svg".equals(this.outputType)) {
      setResponseHeaders(MIME_SVG, 0, "carte.svg");
      renderSvg(mapContent, wdt, hgt, out);
    } else {

      File outputFile = File.createTempFile("tmp", ".svg");
      renderSvg(mapContent, wdt, hgt, new FileOutputStream(outputFile));

      if ("jpg".equals(this.outputType)) {

        setResponseHeaders(MIME_JPG, 0, "carte.jpg");
        renderAsJPEG(outputFile, out);
      } else if ("tiff".equals(this.outputType)) {

        setResponseHeaders(MIME_TIFF, 0, "carte.tiff");
        renderAsTIFF(outputFile, out);
      } else {
        setResponseHeaders(MIME_PNG, 0, "carte.png");
        renderAsPNG(outputFile, out);
      }
    }

    out.flush();
    out.close();

  }

  @Override
  public Log getLogger() {
    return logger;
  }

  private void renderAsJPEG(File svgFile, OutputStream out) {

    // Create a JPEG transcoder
    JPEGTranscoder t = new JPEGTranscoder();

    // Set the transcoding hints.
    t.addTranscodingHint(JPEGTranscoder.KEY_QUALITY,
        new Float(1));

    // Create the transcoder input.
    TranscoderInput input;
    try {
      input = new TranscoderInput(svgFile.toURI().toURL().toString());
      TranscoderOutput output = new TranscoderOutput(out);

      // Save the image.
      t.transcode(input, output);

    } catch (MalformedURLException e) {
      // TODO(vpl) logger.error("Auto-generated log", e);

    } catch (TranscoderException e) {
      // TODO(vpl) logger.error("Auto-generated log", e);
    }

  }

  private void renderAsPNG(File svgFile, OutputStream out) {

    // Create a PNG transcoder
    PNGTranscoder t = new PNGTranscoder();

    // Create the transcoder input.
    TranscoderInput input;
    try {
      input = new TranscoderInput(svgFile.toURI().toURL().toString());

      TranscoderOutput output = new TranscoderOutput(out);

      // Save the image.
      t.transcode(input, output);
    } catch (MalformedURLException e) {
      // TODO(vpl) logger.error("Auto-generated log", e);

    } catch (TranscoderException e) {
      // TODO(vpl) logger.error("Auto-generated log", e);

    }

  }

  private void renderAsTIFF(File svgFile, OutputStream out) {

    // Create a TIFF transcoder
    TIFFTranscoder t = new TIFFTranscoder();

    // Create the transcoder input.
    TranscoderInput input;
    try {
      input = new TranscoderInput(svgFile.toURI().toURL().toString());

      TranscoderOutput output = new TranscoderOutput(out);

      // Save the image.
      t.transcode(input, output);
    } catch (MalformedURLException e) {
      // TODO(vpl) logger.error("Auto-generated log", e);

    } catch (TranscoderException e) {
      // TODO(vpl) logger.error("Auto-generated log", e);

    }

  }

  private void renderSvg(MapContent map, int width, int height, OutputStream out) throws Exception {

    Rectangle paintArea = new Rectangle(width, height);
    ReferencedEnvelope mapArea = map.getViewport().getBounds();

    // on crée le contexte pour le document svg
    SVGGeneratorContext ctx = setupContext();

    // on genere la SVG graphic vide
    SVGGraphics2D g2d = new SVGGraphics2D(ctx, true);

    // on définit la taille du svg final
    g2d.setSVGCanvasSize(new Dimension(width, height));

    logger.debug("Debut rendu");

    StreamingRenderer renderer = new StreamingRenderer();
    renderer.setMapContent(map);

    if (this.loggingLevel == ILogger.DEBUG) {
      renderer.addRenderListener(new RenderListener() {

        @Override
        public void errorOccurred(Exception e) {
          logger.debug("Erreur : " + e.getMessage());
        }

        @Override
        public void featureRenderer(SimpleFeature feature) {
          logger.debug("Rendu feature");
        }
      });

    }

    RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    renderer.setJava2DHints(hints);

    Map<String, Object> rendererParams = new HashMap<String, Object>();
    rendererParams.put("optimizedDataLoadingEnabled", true);
    renderer.setRendererHints(rendererParams);

    try {
      renderer.paint(g2d, paintArea, mapArea);
    } catch (Exception e) {
      throw new Exception("Erreur lors de la génération de la carte au format SVG", e);
    }

    OutputStreamWriter osw = new OutputStreamWriter(out);
    try {
      g2d.stream(osw);
    } finally {
      if (osw != null) {
        osw.close();
      }
    }
  }

  private void setResponseHeaders(final String mimeType, final int cacheDuration, final String attachmentName) {
    // Make sure we have the correct mime type
    final HttpServletResponse response =
      (HttpServletResponse) this.parameterProviders.get("path").getParameter("httpresponse");
    response.setHeader("Content-Type", mimeType);

    if (attachmentName != null) {
      response.setHeader("content-disposition", "inline;filename=" + attachmentName);
    }

    DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.FRANCE);
    Date date = new Date();

    response.setHeader("Last-Modified", dateFormat.format(date));

    // Cache?
    if (cacheDuration > 0) {
      response.setHeader("Cache-Control", "max-age=" + cacheDuration);
    } else {
      response.setHeader("Cache-Control", "no-cache");
    }
  }

  @Override
  public String getMimeType() {

    if ("jpg".equals(this.outputType)) {
      return MIME_JPG;
    }

    if ("png".equals(this.outputType)) {
      return MIME_PNG;
    }

    if ("tiff".equals(this.outputType)) {

      return MIME_TIFF;
    }

    return MIME_SVG;
  }

}
