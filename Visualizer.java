import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 * A complete Java class that demonstrates how to create an HTML viewer with styles,
 * using the JEditorPane, HTMLEditorKit, StyleSheet, and JFrame.
 * 
 * @author alvin alexander, devdaily.com.
 *
 */
public class Visualizer
{
	
	JEditorPane jEditorPane;
	String htmlString;
	String longURL = null;
	
	String stateDatabase;
	
  public static void main(String[] args)
  {
    try {
		new Visualizer();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
  }
  
  public Visualizer() throws IOException
  {
	  
	  String cwd = new java.io.File( "." ).getCanonicalPath();
	  System.out.println(cwd);

	  stateDatabase = "jdbc:sqlite:"+cwd+"/database/SystemState.db";

	  
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        // create jeditorpane
        jEditorPane = new JEditorPane();
        
        // make it read-only
        jEditorPane.setEditable(false);
        
        // create a scrollpane; modify its attributes as desired
        JScrollPane scrollPane = new JScrollPane(jEditorPane);
        
        // add an html editor kit
        HTMLEditorKit kit = new HTMLEditorKit();
        jEditorPane.setEditorKit(kit);
        
        // add some styles to the html
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body {color:#000; font-family:times; margin: 4px; }");
        styleSheet.addRule("h1 {color: blue;}");
        styleSheet.addRule("h2 {color: #ff0000;}");
        styleSheet.addRule("pre {font : 10px monaco; color : black; background-color : #fafafa; }");

        // create a document, set it on the jeditorpane, then add the html
        Document doc = kit.createDefaultDocument();
        jEditorPane.setDocument(doc);
        jEditorPane.setText(htmlString);

        // now add it all to a frame
        JFrame j = new JFrame("HtmlEditorKit Test");
        j.getContentPane().add(scrollPane, BorderLayout.CENTER);

        // make it easy to close the application
        j.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // display the frame
        j.setSize(new Dimension(700,600));
        
        // pack it, if you prefer
        //j.pack();
        
        // center the jframe, then make it visible
        j.setLocationRelativeTo(null);
        j.setVisible(true);
        
        Multi3 m1=new Multi3();  
        
        Thread t1 =new Thread(m1);  
        t1.start();  
      }
    });
    
    
  }
  
  class Multi3 implements Runnable{  
	  public void run(){  
		  int i = 0;
	        while(true) {
	        	// create some simple html as a string
	            htmlString = "<html>\n"
	                              + "<body>\n"
	                              + longURL
	                              + "</body>\n";
	        	jEditorPane.setText(htmlString);
	        	i+=1;
	        	try {
	        		read_state(DriverManager.getConnection(stateDatabase));
					Thread.sleep(500);
				} catch (InterruptedException | SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        }
	  
	  }  
	  
	  
	  public synchronized void read_state(Connection process_database) throws SQLException{


  		longURL = "Processes: \n";
		  
		String sql = "SELECT * FROM Processes";
		
		PreparedStatement pstmt = process_database.prepareStatement(sql);
		ResultSet rs  = pstmt.executeQuery();
		
		while (rs.next()) {
			longURL += "<p>"+rs.getString("process_id")+"</p>";
		}
		
		longURL+="<p></p>";
		longURL += "<p> Databases: </p>";
		
		sql = "SELECT * FROM Databases";
		
		pstmt = process_database.prepareStatement(sql);
		rs  = pstmt.executeQuery();
		
		while (rs.next()) {
			longURL += "<p>"+rs.getString("port")+"|"+rs.getString("partition")+"</p>";
		}
		
		longURL+="<p></p>";
		longURL += "<p>\n Counts: \n</p>";
		
		sql = "SELECT * FROM Datacount";
		
		pstmt = process_database.prepareStatement(sql);
		rs  = pstmt.executeQuery();
		
		while (rs.next()) {
			longURL += "<p>"+rs.getString("partition_type")+"|"+rs.getString("count")+"</p>";
			}
		
		
		return;
		}
	  
	  
  }
  
}