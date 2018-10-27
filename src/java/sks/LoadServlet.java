/**
 * SKS LICENSE v1.00
 *
 * 
 * Copyright (c) 2011, TerraFly, FIU All rights reserved. Redistribution and 
 * use in source and binary forms, with or without modification, are permitted 
 * provided that the following conditions are met:
 * 
 * 1.Redistributions of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer. 
 * 
 * 2.Redistributions in binary form must reproduce the above copyright notice, 
 * this list of conditions and the following disclaimer in the documentation 
 * and/or other materials provided with the distribution. 
 * 
 * 3.Neither the name of the organization nor the names of its contributors 
 * may be used to endorse or promote products derived from this software 
 * without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL TerraFly FIU BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED 
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF 
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * Copyright 2011 (C) Computer Science FIU. All Rights Reserved.
 * Contributions are Copyright (C) 2011 by their associated contributors.
 * Project Director: Naphtali Rishe. 
 * Lead Researcher and Developer: Ariel Cary. Software Engineer: Yun Lu
 *
 */

package sks;

import sks.util.Downloader;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Properties;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.*;
import javax.servlet.http.*;
import sks.dataset.RecordParser;
import sks.dataset.Schema;
import sks.ski.BigSpatialKeywordIndex;
import sks.ski.Index;

import sks.ski.SKIManager;
import sks.ski.SpatialKeywordIndex;

/**
 * Servlet for Load service.
 * <p>
 *
 * Servlet for Load service
 * manage the Load process
 * <p>
 * public class LoadServlet extends HttpServlet
 *
 * @author Ariel Cary
 * @version $Id: ComparisonOperator.java,v 1.0 December 28, 2007
 */




public class LoadServlet extends HttpServlet {
  private String datStore1;
  private String idxStore1;
  private String idxStore2;
  private String binStore;
  private String tmpStore;

  /**
 * Flags
 */
  public static final String SKS_PREFIX = "sks.";
  public static final String LOADING_FLAG = ".loading"; // currently building index.
  public static final String EXCEPTION_FLAG = ".exception"; // last Exception object.
  private static final String RELOADING_FLAG = ".reloading"; // reloading existing index.
  private static final String PARENT_INFO = ".pinfo"; // parent index information.
  public static final String TRX_FLAG = ".trx"; // replacing index in progress.

  @Override

 /**
 * Exception Handler
 */
  public void init() throws ServletException {
    try {
      InitialContext context = new InitialContext();
      datStore1 = (String)context.lookup("java:comp/env/datStore1");
      idxStore1 = (String)context.lookup("java:comp/env/idxStore1");
      idxStore2 = (String)context.lookup("java:comp/env/idxStore2");
      binStore = (String)context.lookup("java:comp/env/binStore");
      tmpStore = (String)context.lookup("java:comp/env/tmpStore");
      context.close();
    } catch (NamingException e) {
      throw new UnavailableException("Cannot find dataset repository path");
    }
  } // public void init()

  /**
   * @param category
   * @return
   */
  public String getCategoryDataPath(String category) {
    return datStore1;
  }

  /**
   * @param category
   * @param idxStore1
   * @param idxStore2
   * @return
   */
  public static String getCategoryIndexPath(String category,
          String idxStore1, String idxStore2) {
    if (category != null &&
        (category.toLowerCase().equals("moa_db_union") ||
         category.toLowerCase().equals("flproperties") ||
         category.toLowerCase().equals("ypages") ||
         category.toLowerCase().equals("nypages") ||
         category.toLowerCase().equals("nyproperties") ||
         category.toLowerCase().equals("street_segment_streets") ||
         category.toLowerCase().equals("flproperties-08") ||
         category.toLowerCase().equals("flproperties_23") ||
         category.toLowerCase().equals("flproperties-09"))) {
      return idxStore2;
    } else {
      return idxStore1;
    }
  }

  /**
   * ArrayList
   * @return
   */
    private static ArrayList<QueryTextPredicate> parseMTCParameters(String[] params,
          Schema schema) {
    // TODO: support for other operators than "=" and numeric fields.
    ArrayList<QueryTextPredicate> queryTextPredicates = null;

    if (params != null) {
      //String[] params = metaCategory.getProperty("parameters").split(MTC_SEPARATOR);
      queryTextPredicates = new ArrayList<QueryTextPredicate>();
      QueryTextPredicate textPredicate = null;
      
      for (String param : params) {
        String[] paramPair = param.split("=");

        if (paramPair.length != 2) {
          continue; // Wrong parameter format.
        }

        int index = schema.getFieldIndex(paramPair[0]);

        if (schema.getTextFieldIndexes().contains(index)) {
          String values[] = paramPair[1].split(" ");
          textPredicate = new QueryTextPredicate(ComparisonOperator.EQUAL, (short) index, paramPair[0]);
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
            queryTextPredicates.add(0, textPredicate);
          }

          textPredicate = null;
        }
      } // for (String param : params)

      if (queryTextPredicates.size() == 0) {
        queryTextPredicates = null;
      }
    } // if (metaCategory != null)

    return queryTextPredicates;
  }


  /**
   * 
   * @param category
   * @param datStore
   * @param idxStore
   * @param context
   * @param metaCategory
   * @return
   */
  public static boolean reloadCategory(String category, String datStore,
                          String idxStore, ServletContext context,
                          Properties metaCategory) throws IOException {
    if (metaCategory == null) {
      return reloadACategory(category, datStore, idxStore, context);
    } else {
      // Start reloading MTC.
      context.setAttribute(SKS_PREFIX + category + RELOADING_FLAG, "yes");
      
      // Reload categories.
      String[] categories = metaCategory.getProperty("categories").split(
              QueryServlet.MTC_SEPARATOR, -1);

      ArrayList<SpatialKeywordIndex> skis = new ArrayList<SpatialKeywordIndex>();
      BigSpatialKeywordIndex bigSKI = new BigSpatialKeywordIndex();
      int indexNumber = 0;
      
      for (String aCategory : categories) {
        if (reloadACategory(aCategory, datStore, idxStore, context)) {
          // Include current SKI.
          skis.add((SpatialKeywordIndex) context.getAttribute(SKS_PREFIX +
                  aCategory + SKIManager.SKI_SUFFIX));
          
          // Keep a pointer to the parent index.
          Hashtable<String, Object> parentInfo = new Hashtable<String, Object>();
          parentInfo.put("bski", bigSKI);
          parentInfo.put("indexNumber", new Integer(indexNumber));
          context.setAttribute(SKS_PREFIX + aCategory + PARENT_INFO, parentInfo);
        } else {
          // Finish reloading MTC.
          context.removeAttribute(SKS_PREFIX + category + RELOADING_FLAG);
          return false;
        }
        
        indexNumber++;
      } // for (String aCategory : categories)
      
      // Install BigSpatialKeywordIndex.
      bigSKI.initializeIndexes(skis.toArray(new SpatialKeywordIndex[skis.size()]));
      context.setAttribute(SKS_PREFIX + category + SKIManager.SKI_SUFFIX, bigSKI);

      // Retrieve additional MTC parameters.
      ArrayList<QueryTextPredicate> mtcParameters = parseMTCParameters(
              metaCategory.getProperty("parameters").split( QueryServlet.MTC_SEPARATOR, -1),
              bigSKI.getDataset().getSchema());

      if (mtcParameters != null) {
        context.setAttribute(SKS_PREFIX + category + SKIManager.MTC_PREDICATES, mtcParameters);
      }

      // Finish reloading MTC.
      context.removeAttribute(SKS_PREFIX + category + RELOADING_FLAG);
      
      return true;
    }
  } // public static boolean reloadCategory()
  
  /**
   *
   * @param category
   * @param datStore
   * @param idxStore
   * @param context
   * @return
   */
  private static boolean reloadACategory(String category, String datStore,
                          String idxStore, ServletContext context) throws IOException {
    if (context == null ||
        context.getAttribute(SKS_PREFIX + category + RELOADING_FLAG) != null) {
      // Reloading is in progress. Quit.
      return false;
    }

    // Start reloading.
    context.setAttribute(SKS_PREFIX + category + RELOADING_FLAG, "yes");

    if (context.getAttribute(SKS_PREFIX + category + SKIManager.SKI_SUFFIX) != null &&
        context.getAttribute(SKS_PREFIX + category + SKIManager.SKI_SUFFIX) instanceof SpatialKeywordIndex) {
      // Release resources held by previous SKI if any.
      SpatialKeywordIndex ski = (SpatialKeywordIndex) context.getAttribute(SKS_PREFIX +
              category + SKIManager.SKI_SUFFIX);
      ski.clear();
      ski = null;
    }

    SKIManager skiManager = new SKIManager(datStore, idxStore, category, false);
    SpatialKeywordIndex ski = skiManager.readSKI();
    
    // Install index.
    context.setAttribute(SKS_PREFIX + category + SKIManager.SKI_SUFFIX, ski);

    if (ski != null) {
      skiManager.loadNodeMap(); // reload node map.
      ski.getSIF().startupStore(); // Bitmap store.

//      if (category.equals("gcity") || category.equals("gtown") //||
//          //category.equals("gns_2011_wcity_gtown")
//          ) {
//        skiManager.loadNodesInStorage(ski.getRtree().getVolatileStorage(), // re-load R-tree upper level nodes.
//                ski.getRtree().getRootNode(), 3); //TREE_UPPER_LEVELS_TO_LOAD);
//        ski.getSIF().getBitmapStore().warmUpCache(3);
//      }
    } else {
      // Finish reloading.
      context.removeAttribute(SKS_PREFIX + category + RELOADING_FLAG);
      return false;
    }

    // Finish reloading.
    context.removeAttribute(SKS_PREFIX + category + RELOADING_FLAG);
    return true;
  } // public static boolean reloadACategory()

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {
    // Service parameters:
    // category
    // dataset
    // header
    // refresh -- deprecated. In progress loads do not overwrite the current index.

    // Context attributes:
    // SKS_PREFIX + category - current dataset.
    // SKS_PREFIX + category + ".index" - current Rtree index.
    // SKS_PREFIX + category + ".loaderror" - current error file object.
    // SKS_PREFIX + category + ".v2p" - current Volatile2PersistentReader object.
    // SKS_PREFIX + category + EXCEPTION_FLAG - last Exception object.
    // SKS_PREFIX + category + RELOADING_FLAG - current index is being loaded into memory.
    // SKS_PREFIX + category + LOADING_FLAG - load request is being processed.
    //   No loads are accepted but queries on the current index are allowed.
    //
    // Deprecated:
    // SKS_PREFIX + category + ".tmpSuffix" - indicates temporary file suffix.
    //
    // Unused?
    // SKS_PREFIX + category + ".isReloaded" - set after index is successfully reloaded into memory.
    // SKS_PREFIX + category + ".updating" - apparently used when refresh="no"?

    final String category = request.getParameter("category");
    final String datasetParam = request.getParameter("dataset");
    final String headerParam = request.getParameter("header");
    
    if (category == null) {
      //Missing category parameter
      RequestDispatcher dispatcher = request.getRequestDispatcher("/views/badrequest.jsp");
      request.setAttribute("message", "Missing category parameter.");
      dispatcher.forward(request, response);
      return;
    }

    // Set index build-in-progress semaphore.
    if (getServletContext().getAttribute(SKS_PREFIX + category + LOADING_FLAG) != null) {
        RequestDispatcher dispatcher = request.getRequestDispatcher("/views/loadingInProgress.jsp");
        request.setAttribute("message", "'" + category + "' category load is in progress. Please wait.");
        request.setAttribute("category", category);
        dispatcher.forward(request, response);
      return;
    } else {
      // Set load-in-progress flag.
      getServletContext().setAttribute(SKS_PREFIX + category + LOADING_FLAG, "yes");

      // Start load with a clean slate.
      getServletContext().removeAttribute(SKS_PREFIX + category + EXCEPTION_FLAG);
    }

    // Bug fix - 5/13/2008 reload category so that a cold refresh will not overwrite existing index.
    if (getServletContext().getAttribute(SKS_PREFIX + category + SKIManager.SKI_SUFFIX) == null &&
        !reloadCategory(category, getCategoryDataPath(category),
          getCategoryIndexPath(category, idxStore1, idxStore2), getServletContext(),
          null) // no meta-category allowed.
          ) {
      // Category couldn't be reloaded. Continue.
    }
    
    /**
     * The load process is executed in a new thread in order to permit
     * doGet to return, which will release the client
     * (i.e. the browser finishes loading the page). We do not want to
     * leave the client hanging while the load process is executing.
     */
    Thread load = new Thread() {
      @Override
      public void run() {
        try {
          String categoryPath = tmpStore + category;
          File datasetFile = new File(categoryPath + "/" + category +
                  SKIManager.DATA_SUFFIX + SKIManager.TEMP_SUFFIX);
          File headerFile = new File(categoryPath + "/" + category +
                  SKIManager.HEADER_SUFFIX + SKIManager.TEMP_SUFFIX);
          ServletContext context = getServletContext();

          /**
           * If the URLs for the dataset and header are present as parameters, then
           * download the files to the repository. Otherwise, it is assumed that the dataset
           * and header files are already in the repository.
           */
          SKIManager skiManager = new SKIManager(tmpStore, tmpStore, category, true);
          if (datasetParam != null && headerParam != null) {
            Downloader downloader = new Downloader(new URL(headerParam), headerFile);
            context.setAttribute(SKS_PREFIX + category + SKIManager.TEMP_SUFFIX, downloader);
            downloader.download();

            downloader = new Downloader(new URL(datasetParam), datasetFile);
            context.setAttribute(SKS_PREFIX + category + SKIManager.TEMP_SUFFIX, downloader);
            downloader.download();
          }

          // Set log file.
          if (new File(categoryPath + "/" + category + SKIManager.LOG_SUFFIX +
                        SKIManager.TEMP_SUFFIX).exists()) {
            new File(categoryPath + "/" + category + SKIManager.LOG_SUFFIX +
                        SKIManager.TEMP_SUFFIX).delete();
          }
          File logFile = new File(categoryPath + "/" + category +
                        SKIManager.LOG_SUFFIX + SKIManager.TEMP_SUFFIX);

          // Build spatial keyword index.
          Loader loader = new Loader(datasetFile, headerFile, logFile,
                                category, binStore, tmpStore);
          context.setAttribute(SKS_PREFIX + category + SKIManager.TEMP_SUFFIX, loader);
          
          if (loader.load()) {
            // Block new queries.
            getServletContext().setAttribute(SKS_PREFIX + category + TRX_FLAG, "yes");
            
            // Wait a little to let current queries finish.
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }

            // Release resources held by the current SKI if any.
            if (context.getAttribute(SKS_PREFIX + category + SKIManager.SKI_SUFFIX) != null &&
                context.getAttribute(SKS_PREFIX + category + SKIManager.SKI_SUFFIX) instanceof Index) {
              Index index = (Index) context.getAttribute(SKS_PREFIX + category + SKIManager.SKI_SUFFIX);
              index.clear();
              index = null;
              context.removeAttribute(SKS_PREFIX + category + SKIManager.SKI_SUFFIX);
            }

            // Switch files.
            if (skiManager.moveSKIfiles(getCategoryDataPath(category),
                  getCategoryIndexPath(category, idxStore1, idxStore2), logFile)) {
              skiManager.clear();
              skiManager = null;

              // Reload new index into memory.
              reloadACategory(category, getCategoryDataPath(category),
                getCategoryIndexPath(category, idxStore1, idxStore2), getServletContext());
            } else {
              // Reload old index if it exists.
              reloadACategory(category, getCategoryDataPath(category),
                getCategoryIndexPath(category, idxStore1, idxStore2), getServletContext());
              throw new Exception("New index cannot be installed.");
            }

            // Load has finished. Remove transient flags.
            context.removeAttribute(SKS_PREFIX + category + SKIManager.TEMP_SUFFIX);
            context.removeAttribute(SKS_PREFIX + category + LOADING_FLAG);
            context.removeAttribute(SKS_PREFIX + category + TRX_FLAG);

            // Update child index if any.
            if (context.getAttribute(SKS_PREFIX + category + PARENT_INFO) != null) {
              Hashtable<String, Object> parentInfo = (Hashtable<String, Object>) context.getAttribute(SKS_PREFIX + category + PARENT_INFO);
              if (parentInfo.get("bski") != null &&
                  parentInfo.get("indexNumber") != null) {
                BigSpatialKeywordIndex bSKI = (BigSpatialKeywordIndex) parentInfo.get("bski");
                int indexNumber = (Integer) parentInfo.get("indexNumber");
                SpatialKeywordIndex ski = (SpatialKeywordIndex) context.getAttribute(SKS_PREFIX + category + SKIManager.SKI_SUFFIX);
                bSKI.updateIndex(indexNumber, ski);
              }
            }
          } // if (loader.load())

          // TODO: Test concurrency controls: query + load + status
          // C1: second load is not working after the 1st load has finished.
        } catch (Exception e) {
          log(e.toString(), e);

          // Keep the exception object.
          getServletContext().setAttribute(SKS_PREFIX + category + EXCEPTION_FLAG, e);

          // Load has finished. Remove transient flags.
          getServletContext().removeAttribute(SKS_PREFIX + category + SKIManager.TEMP_SUFFIX);
          getServletContext().removeAttribute(SKS_PREFIX + category + LOADING_FLAG);
          getServletContext().removeAttribute(SKS_PREFIX + category + TRX_FLAG);
        } // try
      } // public void run()
    }; // Thread load = new Thread()
    
    load.start();

    RequestDispatcher dispatcher = request.getRequestDispatcher("/views/loading.jsp");
    request.setAttribute("category", category);
    dispatcher.forward(request, response);

    System.gc(); //garbage collection
  } // protected void doGet()

  @Override
  public String getServletInfo() {
    return "SKS Load";
  }
} // public class LoadServlet extends HttpServlet