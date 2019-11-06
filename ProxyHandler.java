import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.*;
import java.net.*;

class ProxyHandler extends Thread {
	public Socket server;
	public Socket client;
	static final File WEB_ROOT = new File("./");
	static final String DEFAULT_FILE = "index.html";
	static final String FILE_NOT_FOUND = "404.html";
	static final String METHOD_NOT_SUPPORTED = "not_supported.html";
	static final String REDIRECT_RECORDED = "redirect_recorded.html";
	static final String REDIRECT = "redirect.html";
	static final String NOT_FOUND = "notfound.html";
	static final String ERROR = "error.html";
	public static int numPartitions;
	public static int InstancePerHost;
	// public static String[] hosts = {"localhost","localhost","localhost"};
	public static ConcurrentHashMap<Integer, String[]> partitionToHosts = new ConcurrentHashMap<Integer, String[]>();
	public static ConcurrentHashMap<Integer, Integer> partitionToturn = new ConcurrentHashMap<Integer, Integer>();
	public static ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<String, String>();
	public static ArrayList<String> errors = new ArrayList<String>();
	public int localport, remoteport;
	public static int[] turns = { 0, 0, 0 };
	static final Pattern pattern_write = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$");
	static final Pattern pattern_read = Pattern.compile("^GET\\s+/(\\S+)\\s+(\\S+)/(\\S+)$");

	public ProxyHandler(Socket client, int remoteport) {
		this.client = client;
		this.remoteport = remoteport;
	}

	public void run() {
		try {
			System.out.println("Entered the run function");
			// System.out.println("0: "+hosts[0]);

			handleConnection();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * runs a single-threaded proxy server on the specified local port. It never
	 * returns.
	 * 
	 */
	public void handleConnection() throws Exception {
		// Create a ServerSocket to listen for connections with

		// ServerSocket ss = new ServerSocket(localport);
		System.out.println("Got server socket");

		Socket server = null;
		final BufferedReader streamFromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
		final OutputStream streamToClient = client.getOutputStream();
		PrintWriter out1 = new PrintWriter(streamToClient);
		BufferedOutputStream dataOut = new BufferedOutputStream(streamToClient);

		final byte[] req = new byte[1024];
		byte[] reply = new byte[4096];

		String request = streamFromClient.readLine();

		if (request == null) {
			return;
		}

		System.out.println(request);

		Matcher match;
		String[] hosts = {};
		Integer[] ports = {};
		int partition = -1;
		// client wants to either read, write or bother the server
		if ((match = pattern_write.matcher(request)).matches()) {
			String shortResource = match.group(1);
			partition = Math.floorMod(shortResource.hashCode(), numPartitions);
			hosts = partitionToHosts.get(partition);
			ports = new Integer[hosts.length];
			for (int i = 0; i < hosts.length; i++) {
				int checks = 0;
				ports[i] = Math.floorMod(shortResource.hashCode(), InstancePerHost);
				while (!hostAvailable(hosts[i], remoteport+ports[i]) && checks < InstancePerHost) {
					ports[i]++;
					ports[i] = Math.floorMod(ports[i], InstancePerHost);
					checks++;
				}
			}
			// host = hosts[partition];
		} else if ((match = pattern_read.matcher(request)).matches()) {
			String shortResource = match.group(1);
			if(cache.containsKey(shortResource)) { //it's a read! and we happen to have seen this before
				File file = new File(WEB_ROOT, REDIRECT);
				int fileLength = (int) file.length();
				String contentMimeType = "text/html";
				byte[] fileData = readFileData(file, fileLength);
			    System.out.println("FOUND IN CACHE");	
				out1.println("HTTP/1.1 307 Temporary Redirect");
				out1.println("Location: " + cache.get(shortResource));
				out1.println("Server: Java HTTP Server/Shortner : 1.0");
				out1.println("Date: " + new Date());
				out1.println("Content-type: " + contentMimeType);
				out1.println("Content-length: " + fileLength);
				out1.println(); 
				out1.flush();
				dataOut.write(fileData, 0, fileLength);
				                                                        
				dataOut.flush();
				out1.close();
				dataOut.close();
				streamToClient.close();
				client.close();
				return;
			}
			partition = Math.floorMod(shortResource.hashCode(), numPartitions);
			hosts = new String[1];
			ports = new Integer[1];
			String[] hostsToRead = partitionToHosts.get(partition);
			int k = partitionToturn.get(partition);
			System.out.println(k);
			System.out.println(Math.floorMod(k,InstancePerHost));
			int hostup = (int) Math.floor(k / InstancePerHost);
			int checks = 0;
			while (!(hostAvailable(hostsToRead[hostup], remoteport+Math.floorMod(k,InstancePerHost))) && checks < hostsToRead.length*InstancePerHost) {
				k++;
				k = Math.floorMod(k, hostsToRead.length*InstancePerHost);
				hostup = (int) Math.floor(k / InstancePerHost);
				checks++;
			}
			hosts[0] = hostsToRead[hostup];
			ports[0] = Math.floorMod(k , InstancePerHost);
			int turn = partitionToturn.get(partition);
			partitionToturn.put(partition, Math.floorMod(k + 1 ,hostsToRead.length*InstancePerHost));
			// host = hosts[partition];
		}
		//System.out.println(hosts.toString());
		if (hosts != null) {
			// System.out.println("Host: "+host);
			try {
				if(errors.contains(match.group(1))) {
					throw new Exception("Bad short resource");
				}
				if (!hostsAvailabilityCheck(hosts, remoteport)) {
					throw new Exception();
				}
				BufferedReader[] streamFromServers = new BufferedReader[hosts.length];
				for (int i = 0; i < hosts.length; i++) {
					server = new Socket(hosts[i], remoteport+ports[i]);
					System.out.println("Server Socket made");
					streamFromServers[i] = new BufferedReader(new InputStreamReader(server.getInputStream()));
					OutputStream streamToServer = server.getOutputStream();

					Writer out = new BufferedWriter(new OutputStreamWriter(server.getOutputStream(), "UTF8"));

					out.append(request).append("\n");
					out.flush();
				}
				System.out.println("Closed printwriter");

				int bytesRead;
				for (int i = 0; i < streamFromServers.length; i++) {
					String inputFromServer;
					streamFromServers[i].mark(50);
					if ((inputFromServer = streamFromServers[i].readLine()) != null && !(inputFromServer.contains("307 Temporary Redirect") || inputFromServer.contains("200 OK"))) {
						if(!errors.contains(match.group(1)) && !inputFromServer.contains("404 File Not Found")){
							errors.add(match.group(1));
						}
						System.out.println("BAD");
						throw new Exception("URLShortner failed");
					}
					streamFromServers[i].reset();
				}
				String inputFromServer;
				Writer out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), "UTF8"));
				while ((inputFromServer = streamFromServers[0].readLine()) != null) {
					out.append(inputFromServer).append("\n");
					if(inputFromServer.contains("Location: ")) { //this is a read and it does redirect so stash away!
						if(cache.size() >= 1000) {
							cache = new ConcurrentHashMap<String, String>();
						}
						cache.put(match.group(1), inputFromServer.substring(10, inputFromServer.length()));
					}
				}
				out.flush();
				// update cache on writes
				if ((match = pattern_write.matcher(request)).matches()) {
					if(cache.size() >= 1000) {
						cache = new ConcurrentHashMap<String, String>();
					}
					cache.put(match.group(1), match.group(2));

				}	
				System.out.println("Closing server stream");
				// The server closed its connection to us, so we close our
				// connection to our client.
				streamToClient.close();
			} catch (Exception e) {
				System.err.println("Proxy Handler: " + e);
				File file = new File(WEB_ROOT, ERROR);
				int fileLength = (int) file.length();
				String content = "text/html";
				byte[] fileData = readFileData(file, fileLength);
				out1.println("HTTP/1.1 503 Service Unavailable");
				out1.println("Server: Java HTTP Server/Shortner : 1.0");
				out1.println("Date: " + new Date());
				out1.println("Content-type: " + content);
				out1.println("Content-length: " + fileLength);
				out1.println();
				out1.flush();

				dataOut.write(fileData, 0, fileLength);
				dataOut.flush();
				out1.close();
				dataOut.close();
				streamToClient.close();
				client.close();

			}
			/*
			 * finally { try { if (server != null) server.close(); if (client != null)
			 * client.close(); } catch (IOException e) { } }
			 */
		}
	}

	/**
	 * 
	 * Returns True if host service is up and running, false otherwise.
	 * 
	 * @param host
	 * @param port
	 * @return
	 */

	private static byte[] readFileData(File file, int fileLength) throws IOException {
		FileInputStream fileIn = null;
		byte[] fileData = new byte[fileLength];

		try {
			fileIn = new FileInputStream(file);
			fileIn.read(fileData);
		} finally {
			if (fileIn != null)
				fileIn.close();
		}

		return fileData;
	}

  
  /**
   * 
   * Returns True if all hosts are up and running, exception otherwise.
   * @param host
   * @param port
   * @return
   */
  public static boolean hostsAvailabilityCheck(String[] hosts,int port) throws IOException{ 
	  for (int i = 0; i < hosts.length; i++) {
		  
		  Socket s = new Socket(hosts[i], port);
		  s.close();
		  
	  }
	  return true;
  }
  

public static boolean hostAvailable(String host,int port) { 
	
	try {
		Socket s = new Socket(host, port);
		s.close();
		return true;
	}	catch (Exception e) {
		return false;
	}
}

}
