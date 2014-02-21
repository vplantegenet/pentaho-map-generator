package com.atolcd.pentaho.map;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;
import org.pentaho.platform.api.engine.IApplicationContext;
import org.pentaho.platform.engine.core.system.PentahoSystem;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class DataProvider {

  private final static Splitter SPLITTER_TRIMMER = Splitter.on(';').trimResults(
      CharMatcher.WHITESPACE.or(CharMatcher.is('"')));

  public static String[][] getDataFromCda(final Map<String, String> params)
      throws Exception {

    IApplicationContext appCtx = PentahoSystem.getApplicationContext();
    appCtx.getFullyQualifiedServerURL();

    // TODO : Replace URL call with plugin-manager getting CDA + createContent

    ArrayList<String[]> values = new ArrayList<String[]>();

    String cdaUrl = appCtx.getFullyQualifiedServerURL() + "/content/cda/doQuery?";

    for (Entry<String, String> param : params.entrySet()) {
      cdaUrl += "&" + URLEncoder.encode(param.getKey()) + "=" + URLEncoder.encode(param.getValue());
    }

    String name = "admin";
    String password = "password";

    String authString = name + ":" + password;

    byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
    String authStringEnc = new String(authEncBytes);

    StringBuffer result = new StringBuffer();

    URL cdaSource = new URL(cdaUrl);

    System.out.println(cdaSource);
    try {
      BufferedReader in;
      String inputLine;

      URLConnection urlConnection = cdaSource.openConnection();

      InputStream is = urlConnection.getInputStream();
      InputStreamReader isr = new InputStreamReader(is);

      in = new BufferedReader(isr);

      while ((inputLine = in.readLine()) != null) {
        values.add(Iterables.toArray(SPLITTER_TRIMMER.split(inputLine), String.class));
      }
      in.close();
    } catch (IOException e) {
      // TODO(vpl) logger.error("Auto-generated log", e);

    }

    return values.toArray(new String[0][0]);
  }
}
