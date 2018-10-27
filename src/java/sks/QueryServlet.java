package sks;
import java.io.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.*;
import javax.servlet.http.*;
import sks.dataset.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import sks.rtree.*;
import sks.ski.Index;
import sks.ski.ResultIterator;
import sks.ski.SKIManager;

/**
 * QueryServlet
 * <p>
 *
 * Servlet for Query service
 * manage the Query process
 * <p>
 * public class LoadServlet extends HttpServlet
 *
 * @author Ariel Cary
 * @version $Id: ComparisonOperator.java,v 1.0 December 28, 2007
 */
public class QueryServlet extends HttpServlet {
  static private String datStore1;
  static private String idxStore1;
  static private String idxStore2;

  @Override
  public void init() throws ServletException {
    try {
      InitialContext context = new InitialContext();
      datStore1 = (String)context.lookup("java:comp/env/datStore1");
      idxStore1 = (String)context.lookup("java:comp/env/idxStore1");
      idxStore2 = (String)context.lookup("java:comp/env/idxStore2");
      
      //Check the Top priority datesets first
      String[] categories = {"firstamerican_points", "Street_segment_Streets", "deedsfull"};
      
      for (String category : categories) {
        Properties metaCategory = readMetaCategory(category, getCategoryDataPath(category));

        try {
          LoadServlet.reloadCategory(category, getCategoryDataPath(category),
                  LoadServlet.getCategoryIndexPath(category, idxStore1, idxStore2),
                  getServletContext(), metaCategory);
        } catch (IOException ex) {
          Logger.getLogger(QueryServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
      
      context.close();
    } catch (NamingException e) {
      throw new UnavailableException("Cannot find dataset repository path");
    }
  }

  /**
  * Calculate the distance between two points.
  */
  private double distance(double lat1, double lon1, double lat2, double lon2, char unit) {
    double theta = lon1 - lon2;
    double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) +
            Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));

    if (dist > 1) {
      dist = 1;
    } else if (dist < -1) {
      dist = -1;
    }

    dist = Math.acos(dist);
    dist = rad2deg(dist);
    dist = dist * 60 * 1.1515;

    if (unit == 'K') {
      dist = dist * 1.609344;
    } else if (unit == 'N') {
      dist = dist * 0.8684;
    }
    
    return (dist);
  }

  /**
  * This function converts decimal degrees to radians    
  */
  private double deg2rad(double deg)
  {
    return (deg * Math.PI / 180.0);
  }

  /**
  *  This function converts radians to decimal degrees
  */
  private double rad2deg(double rad) {
    return (rad * 180.0 / Math.PI);
  }

  /**
  *  Meta-category file extension.
  */
  static public final String MTC_EXTENSION = ".mtc";
  static public final String MTC_SEPARATOR = ":";

  /**
   * Reads a metacategory property file.
   * 08-FEB-2010: Meta categories.
   *
   * @param metaCategoryName Meta-category name.
   * @return
   */
  public static Properties readMetaCategory(String metaCategoryName,
          String datStore) {
    Properties metaCategory = new Properties();

    try {
      metaCategory.load(new FileInputStream(datStore + metaCategoryName + "/" +
                              metaCategoryName + MTC_EXTENSION));
    } catch (IOException e) {
      return null;
    }

    return metaCategory;
  }

  /**
   * parseQueryParameters
   * @param request
   * @param queryTextPredicates
   * @param numericParams
   * @param textParams
   * @param schema
   */
  private void parseQueryParameters(HttpServletRequest request,
          ArrayList<QueryTextPredicate> queryTextPredicates,
          ArrayList<NumericParameter> numericParams,
          ArrayList<TextParameter> textParams,
          Schema schema, ArrayList<QueryTextPredicate> mtcPredicates) {
    Enumeration queryParamNames = request.getParameterNames();
    Pattern greaterThanPat = Pattern.compile(".+<[^=]+");
    Pattern lessThanPat = Pattern.compile(".+>[^=]+");
    QueryTextPredicate textPredicate = null;

    while (queryParamNames.hasMoreElements()) {
      String paramName = (String) queryParamNames.nextElement();
      int index;
      if (paramName.endsWith("<") || greaterThanPat.matcher(paramName).matches()) {  // Inequality operators: "<", "<="
        index = schema.getFieldIndex(paramName.substring(0, paramName.indexOf("<")));

        if (schema.getNumberFieldIndexes().contains(index)) {   // on numeric field
          String values[] = request.getParameterValues(paramName);
          ComparisonOperator compOP = ComparisonOperator.GREATER_THAN_EQUAL;

          if (values[0].length() == 0) { // "<" operator
            values[0] = paramName.substring(paramName.indexOf("<") + 1);
            compOP = ComparisonOperator.GREATER_THAN;
          }

          for (String numericField : values) {
            try {
              numericField = RecordParser.cleanseNumericField(numericField);
              double value = Double.parseDouble(numericField);
              numericParams.add(new NumericParameter(schema.getNumberFieldIndexes().indexOf(index), value, compOP, paramName));
            } catch (NumberFormatException e) {
              // Bad numeric parameter value found. Do not add it to the numeric parameter list.
            }
          }
        }

        // Support for inequality operators on strings.
        if (schema.getTextFieldIndexes().contains(index)) { // on string field
          String values[] = request.getParameterValues(paramName);
          ComparisonOperator compOP = ComparisonOperator.GREATER_THAN_EQUAL;

          if (values[0].length() == 0) {
            values[0] = paramName.substring(paramName.indexOf("<") + 1);
            compOP = ComparisonOperator.GREATER_THAN;
          }

          for (String textField: values) {
            //textParams.add(new TextParameter(index, RecordParser.cleanseTextField(textField), compOP));
            textParams.add(new TextParameter(schema.getTextFieldIndexes().indexOf(index),
                    RecordParser.cleanseTextField(textField), compOP, paramName));
          }
        }
      } else if (paramName.endsWith(">") || lessThanPat.matcher(paramName).matches()) { // Inequality operators: ">", ">="
        index = schema.getFieldIndex(paramName.substring(0, paramName.indexOf(">")));

        if (schema.getNumberFieldIndexes().contains(index)) {   // on numeric field
          String values[] = request.getParameterValues(paramName);
          ComparisonOperator compOP = ComparisonOperator.LESS_THAN_EQUAL;

          if (values[0].length() == 0) { // ">" operator
            values[0] = paramName.substring(paramName.indexOf(">") + 1);
            compOP = ComparisonOperator.LESS_THAN;
          }

          for (String s : values) {
            try {
              s = RecordParser.cleanseNumericField(s);
              double value = Double.parseDouble(s);
              numericParams.add(new NumericParameter(schema.getNumberFieldIndexes().indexOf(index),
                      value, compOP, paramName));
            } catch (NumberFormatException e) {
              // Bad numeric parameter value found. Do not add it to the numeric parameter list.
            }
          }
        }

        // Support for comparison operator on strings
        if (schema.getTextFieldIndexes().contains(index)) { // on string field
          String values[] = request.getParameterValues(paramName);
          ComparisonOperator compOP = ComparisonOperator.LESS_THAN_EQUAL;

          if (values[0].length() == 0) {
            values[0] = paramName.substring(paramName.indexOf(">") + 1);
            compOP = ComparisonOperator.LESS_THAN;
          }

          for (String term : values) {
            // add text field with comparison operator.
            textParams.add(new TextParameter(schema.getTextFieldIndexes().indexOf(index),
                    RecordParser.cleanseTextField(term), compOP, paramName));

          }
        }
      } else {  // Equality "=", Not_equal "!=", Negation "|=" operators.
        index = schema.getFieldIndex(paramName);
        ComparisonOperator aFieldOperator = ComparisonOperator.EQUAL;
        String fieldName = paramName;

        if (paramName.endsWith("|")) { // OR "|" operator
          index = schema.getFieldIndex(paramName.substring(0, paramName.length() - 1));

          if (index != -1) {
            aFieldOperator = ComparisonOperator.OR;
            fieldName = paramName.substring(0, paramName.length() - 1);
          }
        } else if (paramName.endsWith("!")) { // NOT_EQUAL "!" operator
          index = schema.getFieldIndex(paramName.substring(0, paramName.length() - 1));

          if (index != -1) {
            aFieldOperator = ComparisonOperator.NOT_EQUAL;
            fieldName = paramName.substring(0, paramName.length() - 1);
          }
        }

        if (schema.getTextFieldIndexes().contains(index)) {
          String values[] = request.getParameterValues(paramName);
          textPredicate = new QueryTextPredicate(aFieldOperator, (short) index, fieldName);
          boolean hasNullConstant = false;

          for (String textField: values) {
            if (textField.equalsIgnoreCase(RecordParser.NULL_CONSTANT)) {
              hasNullConstant = true;
            }

            // Treat each term individually.
            String[] keywords = RecordParser.cleanseTextField(textField).split(" ", -1);
            for (String keyword : keywords) {
              if (keyword.length() > 0 || hasNullConstant) {
                textPredicate.add(keyword);
              }
            }
          }

          if (textPredicate.getKeywordList().size() > 0) {
            if (aFieldOperator == ComparisonOperator.EQUAL) {
              queryTextPredicates.add(0, textPredicate);
            } else {
              queryTextPredicates.add(textPredicate);
            }
          }

          textPredicate = null;
        } else if (schema.getNumberFieldIndexes().contains(index)) {  // on numeric field
          try {
            String values[] = request.getParameterValues(paramName);

            for (String s : values) {
              try {
                s = RecordParser.cleanseNumericField(s);
                double value = Double.parseDouble(s);
                numericParams.add(new NumericParameter(schema.getNumberFieldIndexes().indexOf(index),
                      value, aFieldOperator, fieldName));

                if (value == 0) {
                  // Add numeric constraint as text predicate, too.
                  textPredicate = new QueryTextPredicate(aFieldOperator, (short) index, fieldName);
                  textPredicate.add("");
                  queryTextPredicates.add(textPredicate);
                }
              } catch (NumberFormatException e) {
                // Bad numeric parameter value found.
                // Do not add it to the numeric parameter list.
              }
            }
          } catch (NumberFormatException e) {
            // Bad numeric parameter value found.
            // Do not add it to the numeric parameter list.
          }
        } // if (schema.getNumberFieldIndexes().contains(index))
      } // else {  // Equality "=" operator.
    } // while (queryParamNames.hasMoreElements())

    // Get anyfield predicates.
    String[] operators = {"", "|", "!"};

    for (int i = 0; i < operators.length; i++) {
      String[] anyfieldParam = request.getParameterValues("anyfield" + operators[i]);

      if (anyfieldParam != null && anyfieldParam.length > 0) {
        ComparisonOperator operator = ComparisonOperator.EQUAL;

        if (operators[i].equals("|")) {
          operator = ComparisonOperator.OR;
        } else if (operators[i].equals("!")) {
          operator = ComparisonOperator.NOT_EQUAL;
        }

        textPredicate = new QueryTextPredicate(operator, (short) -1, "anyfield");
        boolean hasNullConstant = false;

        for (String textField: anyfieldParam) {
          if (textField.equalsIgnoreCase(RecordParser.NULL_CONSTANT)) {
            hasNullConstant = true;
          }

          String[] keywords = RecordParser.cleanseTextField(textField).split(" ", -1);
          for (String keyword : keywords) {
            if (keyword.length() > 0 || hasNullConstant) {
              textPredicate.add(keyword);
            }
          }
        }

        if (textPredicate.getKeywordList().size() > 0) {
          if (operator == ComparisonOperator.EQUAL) {
            queryTextPredicates.add(0, textPredicate);
          } else {
            queryTextPredicates.add(textPredicate);
          }
        }

        textPredicate = null;
      } // if (anyfieldParam != null)
    } // for (int i = 0; i < operators.length; i++)


    if (mtcPredicates != null) {
      for (QueryTextPredicate mtcPredicate : mtcPredicates) {
        queryTextPredicates.add(0, mtcPredicate);
      }
    }

  } // private void parseQueryParameters()

  public String getCategoryDataPath(String category) {
    return datStore1;
  }
  
  @Override
  protected void doGet(HttpServletRequest request,
          HttpServletResponse response) throws ServletException, IOException {
    // Check Dataset.
    String category = request.getParameter("category");
    
    // Check if category's index is accepting queries.
    if (getServletContext().getAttribute(LoadServlet.SKS_PREFIX +
            category + LoadServlet.TRX_FLAG) != null) {
      RequestDispatcher dispatcher = request.getRequestDispatcher("/views/badrequest.jsp");
      request.setAttribute("message", "'" + category + "'" +
                  " category is under maintenance. Please try again later.");
      dispatcher.forward(request, response);
      return;
    }

    Object indexObject = getServletContext().getAttribute(LoadServlet.SKS_PREFIX +
            category + SKIManager.SKI_SUFFIX);

    if (indexObject == null) {
      if (!LoadServlet.reloadCategory(category, getCategoryDataPath(category),
           LoadServlet.getCategoryIndexPath(category, idxStore1, idxStore2),
           getServletContext(), readMetaCategory(category, getCategoryDataPath(category)))) {
        RequestDispatcher dispatcher = request.getRequestDispatcher("/views/categorynotfound.jsp");
        request.setAttribute("category", category);
        dispatcher.forward(request, response);
        return;
      }
      
      indexObject = getServletContext().getAttribute(LoadServlet.SKS_PREFIX +
                      category + SKIManager.SKI_SUFFIX);
    }
    
    if (indexObject != null && !(indexObject instanceof Index)) {
      RequestDispatcher dispatcher = request.getRequestDispatcher("/views/categorynotready.jsp");
      request.setAttribute("category", category);
      dispatcher.forward(request, response);
      return;
    }
    
    Index index = (Index) indexObject;
    Dataset dataset = index.getDataset();
    float x1;
    float y1;
    boolean isWindowQuery = false;
    Rectangle queryWindow = null;
    int topk;
    long timeout;
    double queryRadius; // in miles.
    boolean showHeader;
    boolean _debug_mode = false;

    if (request.getParameter("_debug") != null && request.getParameter("_debug").equals("y")) {
      _debug_mode = true;
    }

    ArrayList<QueryTextPredicate> queryTextPredicates = new ArrayList<QueryTextPredicate>();
    ArrayList<NumericParameter> numericParams = new ArrayList<NumericParameter>();
    ArrayList<TextParameter> textParams = new ArrayList<TextParameter>();

    // Parse query parameters.
    parseQueryParameters(request, queryTextPredicates, numericParams, textParams,
        dataset.getSchema(), (ArrayList<QueryTextPredicate>)
        getServletContext().getAttribute(LoadServlet.SKS_PREFIX + category + SKIManager.MTC_PREDICATES));
    
    // Post-filter parameters
    double proximalDistanceToDiscard = -1;
    boolean discardApproximateLocations = false;
    
    if (request.getParameter("discard_proximal_duplicates") != null) {
      try {
        proximalDistanceToDiscard = Double.valueOf(request.getParameter("discard_proximal_duplicates"));
      } catch (NumberFormatException e) {
        // Ignore invalid numbers.
      }
    }

    if (request.getParameter("discard_approximate_locations") != null
        && request.getParameter("discard_approximate_locations").equals("1")) {
      discardApproximateLocations = true;
    }

    try {
      // Get query location.
      String xParam = request.getParameter("x1");
      x1 = Float.parseFloat(xParam);

      String yParam = request.getParameter("y1");
      y1 = Float.parseFloat(yParam);

      String strTimeout = request.getParameter("timeout");
      String queryRadiusParam = request.getParameter("d");

      if (strTimeout == null) {
        timeout = 15000;
      } else {
        timeout = 1000 * Integer.parseInt(strTimeout);
      }

      if (null == queryRadiusParam) {
        queryRadius = 999999;
        //changed at 2012 03 20
      } else {
        queryRadius = Double.parseDouble(queryRadiusParam);
      }

      // Window query optional parameters.
      if (request.getParameter("x2") != null &&
          request.getParameter("y1") != null) {
        try {
          float x2 = Float.parseFloat(request.getParameter("x2"));
          float y2 = Float.parseFloat(request.getParameter("y2"));

          if (x2 < x1 || y2 < y1) {
            RequestDispatcher dispatcher = request.getRequestDispatcher("/views/badrequest.jsp");
            request.setAttribute("message", "'" + category + "'" +
                    " category query has invalid parameters. Please check.");
            dispatcher.forward(request, response);
            return;
          }

          // Define window and its center.
          queryWindow = new Rectangle(new Point(x1, y1), new Point(x2, y2));
          x1 = (x1 + x2) / 2f;
          y1 = (y1 + y2) / 2f;

          // Query radius is the distance from the window center to a corner.
          queryRadius = this.distance(y1, x1, y2, x2, 'M');
          isWindowQuery = true;
        } catch(NumberFormatException e) {
          isWindowQuery = false;
        }
      }

      // k parameter
      String numfindParam = request.getParameter("numfind");
      topk = Integer.parseInt(numfindParam);
      String headerParam = request.getParameter("header");
      showHeader = (headerParam != null && headerParam.equals("1"));
    } catch (Exception ex) {
      log("Bad Parameters", ex);
      RequestDispatcher dispatcher = request.getRequestDispatcher("/views/badrequest.jsp");
      request.setAttribute("message", "Please check Parameters.");
      dispatcher.forward(request, response);
      return;
    }

    // Execute the search query.
    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();

    if (showHeader) {
      printHeader(out, dataset.getHeaderFile());
    }

    Point searchPoint = new Point(x1, y1);

    boolean showIOStats = false;
    if (request.getParameter("_iostats") != null &&
        request.getParameter("_iostats").equals("y")) {
      showIOStats = true;
      index.resetIoReads();
    }

    long startTime = System.nanoTime();
    long time = System.currentTimeMillis();

    ResultIterator results = index.search(searchPoint, (queryRadius * 1609.344f),
            topk, numericParams, queryTextPredicates, _debug_mode);

    int hitCount = 0;
    int charCount = 0;
    Point previousResultPoint = null;

    long currTime = System.currentTimeMillis();
    double distanceToQueryPoint = 0;
    int falsePositives = 0;

    try {
      while (getServletContext().getAttribute(LoadServlet.SKS_PREFIX +
             category + LoadServlet.TRX_FLAG) == null &&
             ((currTime - time) <= timeout || _debug_mode) &&
             (hitCount < topk) && results.hasNext() &&
             (distanceToQueryPoint <= queryRadius)) {
        // Get next result record.
        Record result = results.next();

  //      if (result == null) {
  //        // Malformed record. Check next record.
  //        falsePositives++;
  //        continue;
  //      }

        // Compute distance to query point in miles.
        distanceToQueryPoint = distance(result.getLatitude(), result.getLongitude(),
                                y1, x1, 'M');

        // Apply post-filters.
        if (distanceToQueryPoint <= queryRadius &&
            recordPassesPostfilters(result, textParams, numericParams,
                previousResultPoint, proximalDistanceToDiscard,
                discardApproximateLocations, isWindowQuery, queryWindow)) {
          Point resultPoint = new Point((float) result.getLongitude(), (float) result.getLatitude());
          String compass = findCompassDirection(searchPoint, resultPoint);

          out.println(result.getData() + "\t" +
              result.getLatitude() + "\t" +
              result.getLongitude() + "\t" +
              (distanceToQueryPoint * 1609.344f) + "\t" + // distance in meters
              compass + "\t" + "");

          charCount += result.getData().length();
          previousResultPoint = resultPoint;
          hitCount++;
        } else {
          falsePositives++;
        }

        currTime = System.currentTimeMillis();
      } // while (results.hasNext() && (hitCount < topk) ...
    } catch (Exception e) {
      // DEBUG
      //out.println(e.getMessage());
      Logger.getLogger(QueryServlet.class.getName()).log(Level.SEVERE, null, e);
    } finally {
      // Close IO streams.
      results.clear();
      results = null;
    }

    long elapsedTime = System.nanoTime() - startTime;

    // Print footer.
    out.println("===");
    out.printf("STATS: %d records, %d characters.\n", hitCount, charCount);
    out.printf("TIME: elapsed %.6f seconds, includes the disk access time for record retrieval.\n",
               elapsedTime / 1000000000f);
    out.println("====");

    // hparams
    if (showIOStats) {
      int spatialLIO = index.getSpatialIoReads();
      int textLIO = index.getTextIoReads();
      out.printf("\n*IO*:\n");
      out.printf("\tSpatial %d LIOs, Text %d LIOs.\n", spatialLIO, textLIO);
      out.printf("\tTOTAL_LIO: %d\n", (spatialLIO + textLIO));
      out.printf("\n\tFALSE_POSITIVES: %d\n", (falsePositives));
    }

    if (request.getParameter("_xpred") != null && request.getParameter("_xpred").equals("y")) {
      out.printf("\n*XPRED*:\n");
      out.printf("\n\tText Predicates:\n");

      for (QueryTextPredicate p: queryTextPredicates) {
        out.printf("\t\t" + p.toString() + "\n");
      }

      out.printf("\n\tNumeric Predicates:\n");
      for (NumericParameter p : numericParams) {
        out.printf("\t\t" + p.toString() + "\n");
      }

      out.printf("\n\tPost Filters:\n");
      for (TextParameter p : textParams) {
        out.printf("\t\t" + p.toString() + "\n");
      }
    } // _xpred

    if (request.getParameter("_plotdata") != null && request.getParameter("_plotdata").equals("y")) {
      //sksC2 tree.dumpSuperNodePlotData();
      out.printf("\n*SN PLOT DATA DUMPED*: at [sks.tmp]\n");
    }

    if (request.getParameter("_termplotdata") != null && request.getParameter("_termplotdata").equals("y")) {
      //tree.dumpTermPlotData(siidx, queryTextPredicates);
      out.printf("\n*TERM SN PLOT DATA DUMPED*: at [sks.tmp]\n");
    }

    out.close();
  }

  /**
   * recordPassesPostfilters
   * @param rec
   * @param textParams
   * @param numericParams
   * @return
   */
  private boolean recordPassesPostfilters(Record rec,
          ArrayList<TextParameter> textParams, ArrayList<NumericParameter> numericParams,
          Point previousResultPoint, double proximalDistanceToDiscard,
          boolean discardApproximateLocations, boolean isWindowQuery,
          Rectangle queryWindow) {

    // Window query.
    if (isWindowQuery) {
      Point point = new Point((float) rec.getLongitude(), (float) rec.getLatitude());
      
      if (!queryWindow.isPointInside(point)) {
        return false;
      }
    }

    // Post-filter: Discard approximate location.
    if (discardApproximateLocations &&
        isLocationOverloaded(rec.getLatitude(), rec.getLongitude())) {
      return false;
    }

    // Post-filter: Discard proximal duplicate.
    if (proximalDistanceToDiscard >= 0 && previousResultPoint != null) {
      double proxDist = distance(rec.getLatitude(), rec.getLongitude(),
                                  previousResultPoint.y, previousResultPoint.x, 'M');

      if (proxDist < proximalDistanceToDiscard) {
        // Discard this object.
        return false;
      }
    } // if (pfDisProxDupsDist >= 0 && lastResultPoint != null)
    
    double[] numericValues = rec.getNumericValues();

    // Apply numeric filters.
    if (numericValues != null) {
      for (NumericParameter param : numericParams) {
        // Compare parameter with record value.
        if (param.numFieldIndex < numericValues.length) {
          if (!param.isSatisfiedBy(numericValues[param.numFieldIndex])) {
            return false;
          }
        }
      }
    } // if (numericValues != null)

    // Apply text-field filters.
    String[] textValues = rec.getTextValues();
    if (textValues != null) {
      for (TextParameter param : textParams) {
        if (!param.isSatisfiedBy(textValues[param.index])) {
          return false;
        }
      }
    } // if (passes && textValues != null)

    return true;
  }

  /**
   * Checks if the given coordinates are overloaded.
   * @param lat
   * @param lon
   * @return true if coordinates are overloaded, false otherwise.
   */
  private boolean isLocationOverloaded(double lat, double lon) {
    // Def. a location is approximate if:
    // - lat has format: **.***107
    // - lon has format: ***.***xyz, where xyz is in {001, 004, 020, 100, 999}
    String sLatitude = String.valueOf(lat);
    sLatitude = sLatitude.substring(sLatitude.indexOf(".") + 1);

    if (sLatitude.matches("[0-9]{3}107$")) {
      String sLongitude = String.valueOf(lon);
      sLongitude = sLongitude.substring(sLongitude.indexOf(".") + 1);

      if (sLongitude.matches("[0-9]{3}(001|004|020|100|999)$")) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  } // private boolean isLocationOverloaded()

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {
  }

  @Override
  public String getServletInfo() {
    return "SKS Query";
  }

  private void printHeader(PrintWriter out, File header) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(header));
    String line;
    int count = 1;
    while (!(line = reader.readLine()).startsWith("FIELD")) {
      out.println(line);
    }

    while (!(line = reader.readLine()).equals("=")) {
      out.println(line);
      count++;
    }

    out.println("FIELD-" + count++ + "\t" + "latitude");
    out.println("FIELD-" + count++ + "\t" + "longitude");
    out.println("FIELD-" + count++ + "\t" + "distance\tDistance");
    out.println("FIELD-" + count++ + "\t" + "compass_direction\tCompass direction:  N S W E NW SW SE NE");
    out.println("FIELD-" + count + "\t" + "offset\t");
    out.println("=");
    out.println(reader.readLine() + "\tlatitude\tlongitude\tdistance\tcompass_direction\toffset");
    out.println("==");
    reader.close();
  }

  private String findCompassDirection(Point search, Point result) {
    StringBuffer sb = new StringBuffer();

    if (search.y < result.y)
        sb.append("N");
    else if (search.y > result.y)
        sb.append("S");

    if (search.x < result.x)
        sb.append("E");
    else if (search.x > result.x)
        sb.append("W");

    if (sb.length() == 0)
        // Assume that north is the default when the search and result point are exact.
        sb.append("N");

    return sb.toString();
  }
} // public class QueryServlet