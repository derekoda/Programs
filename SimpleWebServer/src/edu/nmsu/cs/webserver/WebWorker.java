package edu.nmsu.cs.webserver;

/**
 * Web worker: an object of this class executes in its own new thread to receive and respond to a
 * single HTTP request. After the constructor the object executes on its "run" method, and leaves
 * when it is done.
 *
 * One WebWorker object is only responsible for one client connection. This code uses Java threads
 * to parallelize the handling of clients: each WebWorker runs in its own thread. This means that
 * you can essentially just think about what is happening on one client at a time, ignoring the fact
 * that the entirety of the webserver execution might be handling other clients, too.
 *
 * This WebWorker class (i.e., an object of this class) is where all the client interaction is done.
 * The "run()" method is the beginning -- think of it as the "main()" for a client interaction. It
 * does three things in a row, invoking three methods in this class: it reads the incoming HTTP
 * request; it writes out an HTTP header to begin its response, and then it writes out some HTML
 * content for the response content. HTTP requests and responses are just lines of text (in a very
 * particular format).
 * 
 * @author Jon Cook, Ph.D.
 * 
 * The original code has been modified by Derek L. Oda for Program 1 of CS 371
 *
 **/

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.text.*;
import java.util.*;
import java.time.*;

public class WebWorker implements Runnable {

	private Socket socket;
    private boolean isDefault = false;
	/**
	 * Constructor: must have a valid open socket
	 **/
	public WebWorker(Socket s) {
		socket = s;
	} // end constructor

	/**
	 * Worker thread starting point. Each worker handles just one HTTP request and then returns, which
	 * destroys the thread. This method assumes that whoever created the worker created it with a
	 * valid open socket object.
	 **/
	public void run() {
		System.err.println("Handling connection...");
		try {
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			File fileRequest = readHTTPRequest(is);
			String fileType = getFileType(fileRequest);
			writeHTTPHeader(os, fileType, fileRequest);
			writeContent(os, fileType, fileRequest);
			os.flush();
			socket.close();
		} // end try
		
		catch (Exception e) {
			System.err.println("Output error: " + e);
		} // end catch
		
		System.err.println("Done handling connection.");
		return;
	} // end run

	/**
	 * Read the HTTP request header.
	 **/
	private File readHTTPRequest(InputStream is) {
		String line;
		BufferedReader r = new BufferedReader(new InputStreamReader(is));
		String requested = null;
		
		while (true) {
			try {
				while (!r.ready())
					Thread.sleep(1);
				line = r.readLine();	
				System.err.println("Request line: (" + line + ")");
				if (line.length() == 0)
					break;
				if (requested == null)
					requested = line;
			} // end try
			catch (Exception e) {
			    System.err.println("Request error: " + e);
				break;
			} // end catch
		} // end while

        String[] tokenRequested = requested.split(" ");
        File requestFile = null;
        
        // check request
        if (tokenRequested[0].equals("GET")) {
     	    // first char is a '/'
     		requestFile = new File(tokenRequested[1].substring(1));
     		// check if file exists and is not a directory
     		if (requestFile.exists() && !requestFile.isDirectory()) 
     			return requestFile;
     		// check if default page
     		else if (tokenRequested[1].equals("/")) 
     			isDefault = true;
     	} // end if
     	// file does not exist 
     	return null;
	} // end readHHTPRequest

	/**
	 * Write the HTTP header lines to the client network connection.
	 * 
	 * @param os
	 *          is the OutputStream object to write to
	 * @param contentType
	 *          is the string MIME content type (e.g. "text/html")
	 * @param requested
	 * 			is the File we are attempting to access
	 **/
	private void writeHTTPHeader(OutputStream os, String fileType, File requested) throws Exception {
		Date d = new Date();
		DateFormat df = DateFormat.getDateTimeInstance();
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		// if default, ok
		if (requested != null || isDefault)
			os.write("HTTP/1.1 200 OK\n".getBytes());
		// if not, error
		else
			os.write("HTTP/1.1 404 Not Found\n".getBytes());
		os.write("Date: ".getBytes());
		os.write((df.format(d)).getBytes());
		os.write("\n".getBytes());
		os.write("Server: Derek's very own server\n".getBytes());
		// os.write("Last-Modified: Wed, 08 Jan 2003 23:11:55 GMT\n".getBytes());
		// os.write("Content-Length: 438\n".getBytes());
		os.write("Connection: close\n".getBytes());
		os.write("Content-Type: ".getBytes());
		os.write(fileType.getBytes());
		os.write("\n\n".getBytes()); // HTTP header ends with 2 newlines
		return;
	} // end writeHTTPHeader

	/**
	 * Write the data content to the client network connection. This MUST be done after the HTTP
	 * header has been written out.
	 * 
	 * @param os
	 *          is the OutputStream object to write to
	 * @param requested
	 * 			is the File we are attempting to access
	 **/
	private void writeContent(OutputStream os, String fileType, File requested) throws Exception {
		
	    // if file not found
	    if (requested == null && !isDefault) {
	    	os.write("<html><title>404 Not Found</title><head></head><body>\n".getBytes());
			os.write("<h3>404 Not Found</h3>\n".getBytes());
			os.write("</body></html>\n".getBytes());
	    }
			
		// if default page
	    else if (isDefault) {
	    	os.write("<title>Welcome</title>".getBytes());
		    os.write("<h3>My web server works!</h3>\n".getBytes());
	    } // end else if
		// else if text file
	    else if (fileType.equals("text/html")){
	    	os.write("<html><title>Welcome</title><head></head><body>\n".getBytes());
	    	Scanner scan = new Scanner(requested);
			
			while (scan.hasNextLine()) {
				
				String line = scan.nextLine();
				
				if (line.contains("<cs371date>")) {
					LocalDate date = LocalDate.now();
					line = line.replaceAll("<cs371date>", date.toString());
				} // end if
				if (line.contains("<cs371server>")) 
					line = line.replaceAll("<cs371server>", "Derek's Really Cool Server :)");
				
				os.write(line.getBytes());
			} // end while
			os.write("</body></html>\n".getBytes());
			scan.close();
		} // end else if
		// picture or gif
	    else if (fileType.equals("image/gif") || fileType.equals("image/png") || fileType.equals("image/jpeg")) {
			// convert image/gif to byte array for output stream
			byte[] imageToBytes = Files.readAllBytes(requested.toPath());
			os.write(imageToBytes);	
		} // end elseif
	} // end writeContent
    
	
	/**
	 * Check's files content type
	 * @param request: the requested file
	 * @return the file's content type
	 */
	private static String getFileType(File request) {
		if (request == null) {
			// return default
			return "text/html";
		} // end if
		
		String filename = request.getName();
		
		if (filename.endsWith(".gif")) 
			return "image/gif";
		else if (filename.endsWith(".jpeg") || filename.endsWith(".jpg")) 
			return "image/jpeg";
		else if (filename.endsWith(".png")) 
			return "image/png";
		else
		// return default
		return "text/html";
	}
} // end class
