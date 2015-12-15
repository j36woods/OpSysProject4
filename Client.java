import java.io.*;
import java.net.*;
import java.util.*;

public class Client
{
  // IO streams
  private DataOutputStream toServer;
  private DataInputStream fromServer;

  public static void main( String[] args )
  {
    new Client();
  }

  public Client()
  {
    Scanner keyboard = new Scanner( System.in );

    try
    {
      while ( true )
      {
        // Create a socket to connect to the server
        Socket socket = new Socket( "localhost", 8765 );
        // Socket socket = new Socket( "linux00.cs.rpi.edu", 9889 );
        // Socket socket = new Socket( "128.113.126.29", 9889 );

        // Create an input stream to receive data from the server
        fromServer = new DataInputStream( socket.getInputStream() );

        // Create an output stream to send data to the server
        toServer = new DataOutputStream( socket.getOutputStream() );

        // Get the radius from the user
       // System.out.print( "Enter radius: " );
        //double radius = keyboard.nextDouble();

        // Send the radius to the server
        byte[] b = new String("STORE abc.txt 12\nABCDEFG\0\0\0\nZ").getBytes();
        toServer.write(b);
        toServer.flush();
        
        byte[] r = new byte[100];
        fromServer.read(r);
        String line = new String(r, "UTF-8");	
        System.out.println(line.trim());
        
        b = new String("DIR\n").getBytes();
        toServer.write(b);
        toServer.flush();
        
        r = new byte[100];
        fromServer.read(r);
        line = new String(r, "UTF-8");	
        System.out.println(line.trim());
        //System.out.println( "Sent radius " + radius + " to server" );

        // Get area from the server
        //double area = fromServer.readDouble();   // BLOCK

        // Display to the text area
        //System.out.println( "Area received from server is " + area );
      }
    }
    catch ( IOException ex )
    {
      System.err.println( ex );
    }
  }
}