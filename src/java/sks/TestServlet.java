package sks;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;


/**
 * Servlet for Test service.
 * <p>
 *
 * Servlet for Test service
 * manage the Test process
 * <p>
 * public class TestServletServlet extends HttpServlet
 *
 * @author Ariel Cary
 * @version $Id: TestServletServlet.java,v 1.0
 */


public class TestServlet extends HttpServlet
{
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		response.setContentType("text/plain");
		PrintWriter out = response.getWriter();

		String category = request.getParameter("category");
		File datasetFile = new File("c:\\test\\hello\\" + category + ".asc");
	
		out.println("p:"+datasetFile.getPath());
		out.println("n:"+datasetFile.getName());
		out.println("a:"+datasetFile.getAbsoluteFile());
		out.println("a:"+datasetFile.getAbsolutePath());
		out.println("c:"+datasetFile.getCanonicalPath());
		
		out.close();
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
	}
	
	public String getServletInfo()
	{
		return "SKS Test";
	}
}