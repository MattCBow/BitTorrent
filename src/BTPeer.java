// Matt Bowyer
// 156 00 9078

import java.io.*;
import java.util.*;
import java.net.*;

public class BTPeer
{
    public String id;
    public String ip;
    public int port;
    public boolean[] bitfield;

    public BTPeer()
    {
        id = "";
        ip = "";
        port = 0;
    }

    public BTPeer(String peer_id, String peer_ip, int peer_port)
    {
        id = peer_id;
        ip = peer_ip;
        port = peer_port;
    }

    public BTPeer(String peer_ip, int peer_port)
    {
        ip = peer_ip;
        port = peer_port;
    }

    @Override
    public String toString(){
        return (ip+":"+port);
    }

    public boolean equals(BTPeer new_peer)
    {
        if(ip.equals(new_peer.ip) && port == new_peer.port) return true;
        else return false;
    }

    public String printBitfield()
    {
        String stringField = "";
        for(boolean b: bitfield)
        {
            int x = b ? 1 : 0;
            stringField += " "+x;
        }
        return stringField;
    }
    //-----------------------------------------------------------------
    public final static class LocalPeer extends BTPeer implements Runnable
    {
        ServerSocket server = null;
        Socket socket;
        DataInputStream in;
        boolean listening;
        BTTracker tracker;

        public LocalPeer(BTTracker tracker) throws IOException
        {

            this.id = "MCB";
            this.ip = InetAddress.getLocalHost().getHostAddress().toString();
            this.port = 6881;
            this.tracker = tracker;

            Random r = new Random();
            for (int i = 3; i < 20; i++)id += r.nextInt(10);

            //Continueally try to open up a server port
            while(this.server==null){
                try{
                    this.server = new ServerSocket(port);
                }
                catch(IOException e){
                    this.server = null;
                    this.port++;
                }
                if(port>6899){
                    throw new IOException("Unable to open any ports");
                }
            }
            System.out.println("Local Peer "+id+" "+this);

            (new Thread(this)).start();

        }

        public void run()
        {
            try{
              // create socket
              System.out.println("Seeder hosted on port:"+port);
              listening=true;
              // repeatedly wait for connections, and process
              while (listening)
              {
                  // a "blocking" call which waits until a connection is requested
                  socket = server.accept();
                  BTPeer remote_peer = new BTPeer(socket.getInetAddress().getHostName(), socket.getPort());
                  System.out.println("Accepted connection from client at "+remote_peer);

                  if(!tracker.isConnected(remote_peer)){
                      BTConnection con = new BTConnection(this, remote_peer, tracker, socket);
                      tracker.connections.add(con);
                      con.start();
                  }

              }
            }
            catch (IOException e){}
        }

        public void stopListening()
        {
            try{
                //Close all connections
                listening = false;
                server.close();
                if(socket!=null)socket.close();
                System.out.println("STOPPED LISTENING \t"+this);
            }
            catch(IOException e){}
        }

    }
}
