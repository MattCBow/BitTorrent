import java.io.*;
import java.util.*;
import java.net.*;


public class BTLocalPeer extends BTPeer
{
    ServerSocket server_socket = null;

    public BTLocalPeer() throws IOException
    {

        this.id = "MCB";
        this.ip = InetAddress.getLocalHost().getHostAddress().toString();
        this.port = 6881;

        Random r = new Random();
        for (int i = 3; i < 20; i++)id += r.nextInt(10);

        while(this.server_socket==null){
            try{
                this.server_socket = new ServerSocket(port);
            }
            catch(IOException e){
                this.server_socket = null;
                this.port++;
            }
            if(port>6899){
                throw new IOException("Unable to open any ports");
            }
        }

    }
}
