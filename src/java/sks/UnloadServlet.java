package sks;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import sks.ski.Index;
import sks.ski.SKIManager;

/**
 * Servlet for Unload service.
 * <p>
 *
 * Servlet for Unload service
 * manage the Unload process
 * <p>
 * public class UnloadServlet extends HttpServlet
 *
 * @author Ariel Cary
 * @version $Id: UnloadServlet.java,v 1.0
 */

public class UnloadServlet extends HttpServlet {

  @Override
  public void init() throws ServletException {
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String category = request.getParameter("category");
    //String deleteParam = request.getParameter("delete");

    if (category == null) {
      RequestDispatcher dispatcher = request.getRequestDispatcher("/views/badrequest.jsp");
      request.setAttribute("message", "Missing category parameter.");
      dispatcher.forward(request, response);
      return;
    }

    Object indexObject = getServletContext().getAttribute(LoadServlet.SKS_PREFIX +
            category + SKIManager.SKI_SUFFIX);

    if (indexObject != null && !(indexObject instanceof Index)) {
      RequestDispatcher dispatcher = request.getRequestDispatcher("/views/categorynotready.jsp");
      request.setAttribute("category", category);
      dispatcher.forward(request, response);
      return;
    }

    if (indexObject == null) {
      RequestDispatcher dispatcher = request.getRequestDispatcher("/views/categorynotfound.jsp");
      request.setAttribute("category", category);
      dispatcher.forward(request, response);
      return;
    }

    getServletContext().removeAttribute(LoadServlet.SKS_PREFIX + category + SKIManager.SKI_SUFFIX);
    Index index = (Index) indexObject;
    index.clear();
    index = null;
    
    request.setAttribute("category", category);
    RequestDispatcher dispatcher = request.getRequestDispatcher("/views/unloaded.jsp");
    dispatcher.forward(request, response);
    System.gc();
  }

  @Override
  public String getServletInfo() {
      return "SKS Unload";
  }
}