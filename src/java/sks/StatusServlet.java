
package sks;

import sks.util.Downloader;
import java.io.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.*;
import javax.servlet.http.*;
import sks.ski.SKIManager;
import sks.ski.SpatialKeywordIndex;


/**
 * StatusServlet
 * <p>
 * Servlet for checking index status
 *
 * @author Ariel Cary
 * @version $Id: Loader.java,v 1.0 December 28, 2007
 */
public class StatusServlet extends HttpServlet {
  private String datStore1;
  private String idxStore1;
  private String idxStore2;

  @Override
  public void init() throws ServletException {
    try {
      InitialContext context;
      context = new InitialContext();
      datStore1 = (String)context.lookup("java:comp/env/datStore1");
      idxStore1 = (String)context.lookup("java:comp/env/idxStore1");
      idxStore2 = (String)context.lookup("java:comp/env/idxStore2");
      context.close();
    } catch (NamingException e) {
      throw new UnavailableException("Cannot find dataset repository path");
    }
  } // public void init()

  public String getCategoryDataPath(String category) {
    return datStore1;
  }

  public String getCategoryIndexPath(String category) {
    if (category != null &&
        (category.toLowerCase().equals("moa_db_union") || // TODO: update other datasets in idx2
         category.toLowerCase().equals("flproperties-xx"))) {
      return idxStore2;
    } else {
      return idxStore1;
    }
  }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String category = request.getParameter("category");
		if (category == null) {
      processStatusRequest(request, response);
    } else {
      processCategoryStatusRequest(request, response, category);
    }
	}

	private void processStatusRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO: the following code is only for testing,
    // this code should display all the statuses for each category
    response.setContentType("text/html;charset=UTF-8");
    PrintWriter out = response.getWriter();
                
		java.util.Enumeration e = getServletContext().getAttributeNames();
    int count = 0;
		while (e.hasMoreElements()) {
      String tmp = String.valueOf(e.nextElement());

      if (tmp.startsWith(LoadServlet.SKS_PREFIX) && tmp.endsWith(".index")) {
        count++;
        tmp = tmp.replaceAll(LoadServlet.SKS_PREFIX, "");
        tmp = tmp.replaceAll(".index", "");
        out.println(tmp + "<br/>");
      }
		}
                
    out.println(String.valueOf(count) + " datasets are loaded in all!");
		out.close();
	}
	
	private void processCategoryStatusRequest(HttpServletRequest request,
          HttpServletResponse response, String category) throws ServletException, IOException {
    Object obj = getServletContext().getAttribute(LoadServlet.SKS_PREFIX +
            category + SKIManager.SKI_SUFFIX);

    if (obj == null) {
      // Try to reload category.
      if (!LoadServlet.reloadCategory(category, getCategoryDataPath(category),
           getCategoryIndexPath(category), getServletContext(),
           null) // TODO: meta-category
           ) {
        // Continue.
      }

      obj = getServletContext().getAttribute(LoadServlet.SKS_PREFIX +
              category + SKIManager.SKI_SUFFIX);
    }
    
    Object eObj = getServletContext().getAttribute(LoadServlet.SKS_PREFIX +
            category + LoadServlet.EXCEPTION_FLAG);
    if (eObj != null && (eObj instanceof Exception)) {
      // An exception has been caught in a previous load attempt.
      obj = eObj;
    } else {
      if (getServletContext().getAttribute(LoadServlet.SKS_PREFIX +
              category + LoadServlet.LOADING_FLAG) != null) {
        Object loaderObj = getServletContext().getAttribute(LoadServlet.SKS_PREFIX +
                category + SKIManager.TEMP_SUFFIX);

        if (loaderObj != null && !(loaderObj instanceof SpatialKeywordIndex)) {
          // Loading process is in progress.
          obj = loaderObj;
        }
      }
    }
                
		RequestDispatcher dispatcher;
		request.setAttribute("category", category);
		if (obj instanceof Downloader) {
			Downloader downloader = (Downloader)obj;
			request.setAttribute("downloading", true);
			request.setAttribute("remote", downloader.getRemote());
			request.setAttribute("local", downloader.getLocal().getName());
			request.setAttribute("bytes", downloader.getBytesDownloadedCount());
			dispatcher = request.getRequestDispatcher("/views/loadstatus.jsp");
		} else if (obj instanceof Loader) {
			Loader loader = (Loader) obj;
			request.setAttribute("downloading", false);
      request.setAttribute("stage", loader.getLoadStage());
			request.setAttribute("processed", loader.getRecordsProcessed());
			dispatcher = request.getRequestDispatcher("/views/loadstatus.jsp");
		} else if (obj instanceof Exception) {
			Exception e = (Exception)obj;
      request.setAttribute("message", e.getMessage());
      dispatcher = request.getRequestDispatcher("/views/loaderror.jsp");
		} else if (obj instanceof SpatialKeywordIndex) {
      SpatialKeywordIndex ski = (SpatialKeywordIndex) obj;
      request.setAttribute("updatedOn", ski.getLastUpdatedOn());
			dispatcher = request.getRequestDispatcher("/views/categorystatus.jsp");
		} else {
			dispatcher = request.getRequestDispatcher("/views/categorynotfound.jsp");
		}

		dispatcher.forward(request, response);
	}
	
	public String getServletInfo() {
		return "SKS Status";
	}
}