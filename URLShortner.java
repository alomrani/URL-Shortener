import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.lang.management.ManagementFactory;


public class URLShortner { 
	
	// port to listen connection
	static int PORT = 8080;

	public static void main(String[] args) {

		//String pid = ManagementFactory.getRuntimeMXBean().getName();
		//System.out.println("PID is : "+pid);

		

		try {

			String cwd = new java.io.File( "." ).getCanonicalPath();
			System.out.println(cwd);

			String stateDatabase = "jdbc:sqlite:/virtual/" + System.getProperty("user.name") + "/database/SystemState.db";

			if(args.length != 2){
				throw new Exception("Not enough arguments");
			}
			//Create references to existing partition files

            PORT = Integer.valueOf(args[0]);
			//Initiate Server to listen for requests
			ServerSocket serverConnect = new ServerSocket(PORT);
			System.out.println("Server started.\nListening for connections on port : " + PORT + " ...\n");
			int partitions = Integer.parseInt(args[1]);

			String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
			System.out.println(pid);
			
			Connection connection = DriverManager.getConnection(stateDatabase);

			try{
				System.out.println("starting updates");
				Handler.update_state(Integer.valueOf(pid),PORT,1,0,0,connection);
				System.out.println("updated state");
				Handler.update_databases(PORT,1,connection);
				Handler.update_databases(PORT,2,connection);
				System.out.println("updated databases");
				Handler.update_datacount(1,1,connection);
				Handler.update_datacount(2,1,connection);
			} 
			catch(Exception e){
				System.err.println("State Database Error : " + e.getMessage());
			}

			//On connection with a client a new thread is spawned to handle the request
			while (true) {
				Socket socket = serverConnect.accept();
				Handler handler = new Handler(socket, partitions);
				handler.start();
			}
		} catch (Exception e) {
			System.err.println("Server Connection error : " + e.getMessage());
		}
	}
}

class Handler extends Thread{
	
	static final File WEB_ROOT = new File("./");
	static final String DEFAULT_FILE = "index.html";
	static final String FILE_NOT_FOUND = "404.html";
	static final String METHOD_NOT_SUPPORTED = "not_supported.html";
	static final String REDIRECT_RECORDED = "redirect_recorded.html";
	static final String REDIRECT = "redirect.html";
	static final String NOT_FOUND = "notfound.html";
	static final String ERROR = "error.html";
	static final String HEALTHCHECK = "healthcheck.html";
	static int partitions = 0;
	final Socket socket;
	final static boolean verbose = true;
	
	public Handler(Socket socket, int partitions) {
		this.socket = socket;
		Handler.partitions = partitions;
	}
	
	public void run() {
		if (verbose) { System.out.println("Connecton opened. (" + new Date() + ")"); }
		handle(socket);
	}

	public static void handle(Socket connect) {
		BufferedReader in = null; 
		PrintWriter out = null; 
		BufferedOutputStream dataOut = null;
		
		try {
			in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
			out = new PrintWriter(connect.getOutputStream());
			dataOut = new BufferedOutputStream(connect.getOutputStream());
			
			//Get input from the client
			String input = in.readLine();
			if (input == null) {
				in.close();
				out.close();
				connect.close();
				return;
			}
			if(verbose)System.out.println("first line: "+input);

			Pattern phealth = Pattern.compile("^GET /healthcheck (\\S+)/(\\S+)$");
			Matcher mhealth = phealth.matcher(input);
			if(mhealth.matches()) {
				System.out.println("MATCHES HEALTHCHECK");
				
				File file = new File(WEB_ROOT, HEALTHCHECK);
				int fileLength = (int) file.length();
				String contentMimeType = "text/html";

				//read content to return to client
				byte[] fileData = readFileData(file, fileLength);
				
				out.println("HTTP/1.1 200 OK");
				out.println("Server: Java HTTP Server/Shortner : 1.0");
				out.println("Date: " + new Date());
				out.println("Content-type: " + contentMimeType);
				out.println("Content-length: " + fileLength);
				out.println(); 
				out.flush(); 

				dataOut.write(fileData, 0, fileLength);
				dataOut.flush();
				
			}
			else {
			//Match the string for writing a URL to a partition
			Pattern pput = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$");
			Matcher mput = pput.matcher(input);
			if(mput.matches()) {
				String shortResource=mput.group(1);
				String longResource=mput.group(2);
				//Pick a partition pseudo randomly
				int partitionNumber = Math.floorMod(shortResource.hashCode(),partitions);
				String url = "jdbc:sqlite:/virtual/" + System.getProperty("user.name") + "/database/partition" + (partitionNumber + 1) + ".db";
				Connection partition;
				try {
					partition = DriverManager.getConnection(url);
				} catch (SQLException e) {
					System.err.println("Database Connection error : " + e.getMessage());
					
					try {
						String[] cmd = {"./dataRecovery", String.valueOf(partitionNumber + 1)};
						Process proc = Runtime.getRuntime().exec(cmd);
						System.out.println("Database " + partitionNumber  + " recovered!");
					}catch(Exception e2) {
						throw e2;
					}
					
					try {
						partition = DriverManager.getConnection(url);
					}catch(SQLException e2) {
						throw e2;
					}
				}

				//String httpVersion=mput.group(4);
				//Write the short:url string to partition file
				
				save(shortResource, longResource, partition);
				System.out.println(partition);
				File file = new File(WEB_ROOT, REDIRECT_RECORDED);
				int fileLength = (int) file.length();
				String contentMimeType = "text/html";

				//read content to return to client
				byte[] fileData = readFileData(file, fileLength);
				
				out.println("HTTP/1.1 200 OK");
				out.println("Server: Java HTTP Server/Shortner : 1.0");
				out.println("Date: " + new Date());
				out.println("Content-type: " + contentMimeType);
				out.println("Content-length: " + fileLength);
				out.println(); 
				out.flush(); 

				dataOut.write(fileData, 0, fileLength);
				dataOut.flush();
			} else {

				//Match the string for reading a URL from a partition
				Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)/(\\S+)$");
				System.out.println("Input: "+input);
				Matcher mget = pget.matcher(input);
				boolean matched = mget.matches();
				System.out.println("Matched: "+matched);
				if(matched){
					String method=mget.group(1);
					String shortResource=mget.group(2);
					System.out.println("method: "+method);
					System.out.println("shortResource: "+shortResource);
					

					System.out.println("Hash Code: "+ shortResource.hashCode());
					System.out.println("Modulus: "+ partitions);
					//Pick the partition that was used for writing into the partition previously
					int partitionNumber = Math.floorMod(shortResource.hashCode(),partitions);
					String url = "jdbc:sqlite:/virtual/" + System.getProperty("user.name") + "/database/partition" + (partitionNumber + 1) + ".db";
					Connection partition;
					try {
                                        	partition = DriverManager.getConnection(url);
                                	} catch (SQLException e) {
                                        	System.err.println("Database Connection error : " + e.getMessage());

                                        	try {
                                                	String[] cmd = {"./dataRecovery", String.valueOf(partitionNumber + 1)};
                                                	Process proc = Runtime.getRuntime().exec(cmd);
                                                	System.out.println("Database " + partitionNumber  + " recovered!");
                                        	}catch(Exception e2) {
                                                	throw e2;
                                        	}
                                        	try {
                                                	partition = DriverManager.getConnection(url);
                                        	}catch(SQLException e2) {
                                                	throw e2;
                                        	}
                                	}

					System.out.println("partition Number: "+(partitionNumber+1));

					System.out.println("partition: "+partition);

					String httpVersion=mget.group(4);

					System.out.println("httpVersion: "+httpVersion);

					//Attempt to find the short:url in the chosen partition
					String longResource = find(shortResource, partition);

					System.out.println("longResource: "+longResource);
					try {
						if(longResource!=null) {
							File file = new File(WEB_ROOT, REDIRECT);
							int fileLength = (int) file.length();
							String contentMimeType = "text/html";
		
							//read content to return to client
							byte[] fileData = readFileData(file, fileLength);
							
							//Tell the client to redirect to the long url location
							// out.println("HTTP/1.1 301 Moved Permanently");
							out.println("HTTP/1.1 307 Temporary Redirect");
							out.println("Location: "+longResource);
							out.println("Server: Java HTTP Server/Shortner : 1.0");
							out.println("Date: " + new Date());
							out.println("Content-type: " + contentMimeType);
							out.println("Content-length: " + fileLength);
							out.println(); 
							out.flush(); 
		
							dataOut.write(fileData, 0, fileLength);
							dataOut.flush();
						} else {
							//If the provided string does not match read/write pattern, abort
	
							File file = new File(WEB_ROOT, FILE_NOT_FOUND);
							int fileLength = (int) file.length();
							String content = "text/html";
							byte[] fileData = readFileData(file, fileLength);
							
							out.println("HTTP/1.1 404 File Not Found");
							out.println("Server: Java HTTP Server/Shortner : 1.0");
							out.println("Date: " + new Date());
							out.println("Content-type: " + content);
							out.println("Content-length: " + fileLength);
							out.println(); 
							out.flush(); 
							
							dataOut.write(fileData, 0, fileLength);
							dataOut.flush();
						}
					}
					catch(Exception e) {
						e.printStackTrace();
						File file = new File(WEB_ROOT, ERROR);
						int fileLength = (int) file.length();
						String content = "text/html";
						byte[] fileData = readFileData(file, fileLength);
						out.println("HTTP/1.1 503 Error");
						out.println("Server: Java HTTP Server/Shortner : 1.0");
						out.println("Date: " + new Date());
						out.println("Content-type: " + content);
						out.println("Content-length: " + fileLength);
						out.println(); 
						out.flush(); 
						
						dataOut.write(fileData, 0, fileLength);
						dataOut.flush();
						}
					
						System.out.print("done");
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Server error: " + e);
		} finally {
			try {
				in.close();
				out.close();
				connect.close(); // we close socket connection
			} catch (Exception e) {
				System.err.println("Error closing stream : " + e.getMessage());
			} 
			
			if (verbose) {
				System.out.println("Connection closed.\n");
			}
		}
	}

	private static synchronized String find(String shortURL, Connection partition) throws SQLException {
		String sql = "SELECT shortURL, longURL FROM urls WHERE shortURL = ?";
		String longURL = null;

		PreparedStatement pstmt  = partition.prepareStatement(sql);
		pstmt.setString(1, shortURL);

		ResultSet rs  = pstmt.executeQuery();

		// loop through the result set
		while (rs.next()) {
			longURL = rs.getString("longURL");
		}
		return longURL;
	}

	public static synchronized void update_state(int pid, int port, int health, 
												  float load, float response_time, 
												  Connection process_database) throws SQLException{

		String sql = "INSERT OR REPLACE INTO Processes(process_id,port,health,load,response_time) VALUES(?,?,?,?,?)";
		 
        PreparedStatement pstmt = process_database.prepareStatement(sql);
        pstmt.setInt(1, pid);
		pstmt.setInt(2, port);
        pstmt.setInt(3, health);
		pstmt.setFloat(4, load);
		pstmt.setFloat(5, response_time);
        pstmt.executeUpdate();
		return;
	}

	public static synchronized void update_databases(int port, int partition, 
										Connection process_database) throws SQLException{

		String sql = "INSERT INTO Databases(port,partition) VALUES(?,?)";
		 
        PreparedStatement pstmt = process_database.prepareStatement(sql);
        pstmt.setInt(1, port);
		pstmt.setInt(2, partition);
        pstmt.executeUpdate();
		return;
	}

	public static synchronized void update_datacount(int partition, int update, 
										Connection process_database) throws SQLException{

		String sql = "SELECT count FROM Datacount WHERE partition_type = ?";
		
		int old_count = 0;

		PreparedStatement pstmtread  = process_database.prepareStatement(sql);
		pstmtread.setInt(1, partition);
		ResultSet rs  = pstmtread.executeQuery();

		// loop through the result set
		while (rs.next()) {
			old_count = rs.getInt("count");
			sql = "UPDATE Datacount SET count=? WHERE partition_type=?";
		 
			PreparedStatement pstmt = process_database.prepareStatement(sql);
			pstmt.setInt(1, old_count+update);
			pstmt.setInt(2, partition);

			System.out.println("Count= "+(old_count+update));
			System.out.println("Partition= "+partition);
			pstmt.executeUpdate();

		}
		return;
	}


	private static synchronized void save(String shortURL,String longURL, Connection partition) throws SQLException {
		String sql = "INSERT OR REPLACE INTO urls(shortURL,longURL) VALUES(?,?)";
		 
        PreparedStatement pstmt = partition.prepareStatement(sql);
        pstmt.setString(1, shortURL);
        pstmt.setString(2, longURL);
        pstmt.executeUpdate();
		return;
	}
	
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
}
