import java.io.*;
import java.util.*;
import java.net.*;


public class BTConnection extends Thread
{
    BTLocalPeer local_peer;
    BTPeer remote_peer;
    BTTracker tracker;

    public BTConnection(BTLocalPeer local_peer, BTPeer remote_peer, BTTracker tracker)
    {

        this.local_peer = local_peer;
        this.remote_peer = remote_peer;
        this.tracker = tracker;

    }

    public void run(){
        System.out.println("Connection between \n"+local_peer+ "\n"+remote_peer);

        Socket socket = new Socket(remote_peer.ip,remote_peer.port);
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        byte[] peer_message = new byte[68];

        //Initialize Handshake
        byte[] handshake_message = new byte[68];
        byte[] handshake_header = {19,'B','i','t','T','o','r','r','e','n','t',' ','p','r','o','t','o','c','o','l',0,0,0,0,0,0,0,0};
        System.arraycopy(handshake_header, 0, handshake_message, 0, 28);
        System.arraycopy(tracker.torrent.info_hash.array(), 0, handshake_message,28 , 20);
        System.arraycopy(my_peer_id.getBytes(), 0, handshake_message,48 , 20);

        //Send handshake
        out.write(handshake_message);
        System.out.println("Handshake sent to \n"+remote_peer);
        in.read(peer_message);

        //Verify Handshake
        boolean valid_handshake = true;
        if(!Arrays.equals(handshake_header,Arrays.copyOfRange(peer_message,0,28)))valid_handshake = false;
        if(!Arrays.equals(torrent.info_hash.array(),Arrays.copyOfRange(peer_message,28,48)))valid_handshake = false;
        if(!ru_peer_id.equals(new String( Arrays.copyOfRange(peer_message,48,68) , "UTF-8")))valid_handshake = false;
        if(!valid_handshake){/*throw new Exception("Error: Invalid Hanshake");*/}
        else{System.out.println("Valid Handshake received from \n"+remote_peer);}
    }
}
