import java.io.*;
import java.net.*;

public class SimpleProxyServer {
	public static String[] hosts = {"dh2026pc01.utm.utoronto.ca", "dh2026pc02.utm.utoronto.ca", "dh2026pc04.utm.utoronto.ca"};
	public static int InstancePerHost = 2;
	public static String[] partitions;
	public static int numPartitions;
	public static int localport;
	public static int remoteport;
	public static int turn = 0;
	
	
	public static void main(String[] args) throws IOException {
		try {
			if(args.length != 3){
				throw new Exception("Not enough arguments");
			}
			SimpleProxyServer.numPartitions = Integer.parseInt(args[1]);
			SimpleProxyServer.InstancePerHost = Integer.parseInt(args[2]);
			ProxyHandler.InstancePerHost = Integer.parseInt(args[2]);
			SimpleProxyServer.partitions = new String[SimpleProxyServer.numPartitions];
			String host = "your Proxy Server";
			int remoteport = Integer.parseInt(args[0]);
			int localport = 8085;
			// Print a start-up message
			System.out.println("Starting proxy for " + host + ":" + remoteport + " on port " + localport);
			
			ServerSocket serverConnect = new ServerSocket(localport);
			System.out.println("Proxy Server started.\nListening for connections on port : " + localport + " ...\n");
			
			//ProxyHandler.hosts = hosts;
			ProxyHandler.numPartitions = Integer.parseInt(args[1]);
			if (SimpleProxyServer.numPartitions != 1) {
			      for (int i = 0; i < numPartitions;i++) {
			    	  String[] temp = {hosts[i%hosts.length], hosts[(i+1)%hosts.length]};
			    	  ProxyHandler.partitionToHosts.put(new Integer(i), temp);
			    	  ProxyHandler.partitionToturn.put(new Integer(i), new Integer(0));
			      }
			  }	else {
			    	  for (int i = 0; i < numPartitions;i++) {
			        	  String[] temp = {hosts[i%hosts.length]};
			        	  ProxyHandler.partitionToHosts.put(new Integer(i), temp);
			        	  ProxyHandler.partitionToturn.put(new Integer(i), new Integer(0));
			          }
		      }
			while (true) {
				Socket client = serverConnect.accept();
				System.out.println("Got a connection");
				ProxyHandler handler = new ProxyHandler(client, remoteport);
				handler.start();
			}
		} 
		catch (Exception e) {
			System.err.println(e);
		}
	}
}
