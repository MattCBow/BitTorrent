// Matt Bowyer
// 156 00 9078

import java.io.*;
import java.nio.*;
import java.util.*;
import java.net.*;
import java.security.*;

import GivenTools.*;

public class BTTracker implements Runnable
{
    private volatile boolean run = true;

    BTPeer.LocalPeer local_peer;
    TorrentInfo torrent;
    File destination_file;
    byte[] file_bytes;
    String file_name;
    String event;
    int uploaded;
    int downloaded;
    int left;
    int interval;
    URL tracker_url;
    ArrayList<BTConnection> connections;
    HashMap<ByteBuffer,Object> tracker_map;
    ArrayList<HashMap<ByteBuffer,Object>> peer_array_list;
    ArrayList<Integer> piece_requests;
    ArrayList<Integer> pieces_requested;
    long startTime;

    public BTTracker(TorrentInfo torrent, String file_name) throws IOException
    {
        //Creates Local Peer
        try{ this.local_peer = new BTPeer.LocalPeer(this); }
        catch (Exception e){ System.out.println(e.getMessage()); }

        //Intializes all fields
        this.torrent = torrent;
        this.uploaded=0;
        this.downloaded=0;
        this.left=torrent.file_length;
        this.event="started";
        this.startTime = System.nanoTime();
        this.connections = new ArrayList<BTConnection>();
        this.file_name = file_name;
        this.file_bytes = new byte[torrent.file_length];
        this.destination_file = new File(file_name);
        local_peer.bitfield = newBitfield();

        //Checks If File already exists and uploads previously downloaded pieces
        if(destination_file.exists())
        {
            this.event="completed";
            System.out.println("Download already started");
            RandomAccessFile f = new RandomAccessFile(file_name, "r");
            if(f.length()==torrent.file_length) f.read(file_bytes);
            for(int piece_index=0; piece_index<torrent.piece_hashes.length; piece_index++)
            {
                int piece_length = torrent.piece_length;
                if(piece_index==(torrent.piece_hashes.length-1))
                piece_length = torrent.file_length-torrent.piece_length*(torrent.piece_hashes.length-1);
                byte[] file_piece = new byte[piece_length];
                System.arraycopy(file_bytes, piece_index*torrent.piece_length , file_piece, 0, piece_length);
                if(!verifyHash(piece_index, file_piece)){
                    this.event="started";
                    this.left -= piece_length;
                }
            }
        }
        if(event.equals("completed"))System.out.println("Download already completed");

        //Created Piece Request Queue
        this.piece_requests = new ArrayList<Integer>();
        this.pieces_requested = new ArrayList<Integer>();
        for(Integer i=0;i<local_peer.bitfield.length;i++) if(local_peer.bitfield[i]==false)piece_requests.add(i);
        Collections.shuffle(piece_requests, new Random());
    }

    public void run()
    {
        run = true;
        while(run){
            try{
                 //Contacts Tracker and gets Peer List
                 HttpURLConnection http_connection = contactTracker();
                 DataInputStream in = new DataInputStream(http_connection.getInputStream());
                 byte[] http_response = new byte[http_connection.getContentLength()];
                 in.read(http_response);
                 in.close();
                 tracker_map = (HashMap<ByteBuffer, Object>) Bencoder2.decode(http_response);
                 peer_array_list = (ArrayList<HashMap<ByteBuffer, Object>>) tracker_map.get(ByteBuffer.wrap("peers".getBytes()));

                 //Finds New Peers to download from
                 Iterator<HashMap<ByteBuffer,Object>> it = peer_array_list.iterator();
                 while (run && it.hasNext() && !piece_requests.isEmpty()) {
                     HashMap<ByteBuffer,Object> peer = it.next();
                     String ip = new String(((ByteBuffer)peer.get(ByteBuffer.wrap("ip".getBytes()))).array());
                     String id = new String(((ByteBuffer)peer.get(ByteBuffer.wrap("peer id".getBytes()))).array());
                     int port = (Integer) peer.get(ByteBuffer.wrap("port".getBytes()));
                     BTPeer new_peer = new BTPeer(id, ip, port);
                     if(  (ip.equals("128.6.171.130")|| ip.equals("128.6.171.131")) && !isConnected(new_peer) ){
                         BTConnection con = new BTConnection(local_peer, new_peer, this);
                         connections.add(con);
                         con.start();
                     }
                 }

                 //Checks If download is Completed
                 if(event.equals("started") && piece_requests.isEmpty() && pieces_requested.isEmpty())
                 {
                     System.out.println("-----DOWNLOAD COMPLETE------");
                     save();
                 }

                 //Waits by the interval given from the Tracker
                 interval = (Integer)tracker_map.get(ByteBuffer.wrap("interval".getBytes()));
                 Thread.sleep(interval);
            }
            catch(MalformedURLException e){System.err.println(e);}
            catch(InterruptedException e) {System.err.println(e);}
            catch(BencodingException e) {System.err.println(e);}
            catch(ProtocolException e) {System.err.println(e);}
            catch(IOException e) {System.err.println(e);}
            catch(Exception e) {System.err.println(e);}
        }
    }

    public void stop()
    {
        //Clears Tracker Proccess
        run = false;
        piece_requests.clear();


        //Tells all other Threads Running to stop
        local_peer.stopListening();
        while(!connections.isEmpty())
        {
            BTConnection con = connections.get(0);
            con.dropConnection();
        }

        //If the file has not been saved, save it
        if(!event.equals("completed")) save();

        //Tells the Tracker Its done
        event="stopped";
        try{contactTracker();}
        catch(Exception e){System.err.println(e);}

    }

    //Saves the file and Notifys the Tracker
    public void save()
    {
        event="completed";
        try{
            FileOutputStream file_out = new FileOutputStream(destination_file);
            file_out.write(file_bytes);

            event="completed";
            contactTracker();
        }
        catch(Exception e){System.err.println(e);}

        //Calculate the total Download Time
        long endTime   = System.nanoTime();
        long totalTime = endTime - startTime;
        System.out.println("TOTAL TIME: "+(totalTime/Math.pow(10,9))+" seconds");
        System.out.println("---------FILE SAVED---------");

    }

    public boolean isConnected(BTPeer new_peer)
    {
        Iterator<BTConnection> btIt = connections.iterator();
        BTConnection con;

        //Checks if a connection with a peer already exits
		while (btIt.hasNext())
        {
			con = btIt.next();
            if(con.remote_peer.equals(new_peer)) return true;
		}
        return false;
    }

    //Sends a Message to the Tracker and returns the response
    public HttpURLConnection contactTracker() throws Exception
    {
        String escaped_info_hash = "";
        for (byte b : torrent.info_hash.array()) escaped_info_hash += "%"+String.format("%02x", b & 0xff);

        tracker_url = new URL(torrent.announce_url.toString()
                              +"?info_hash="+escaped_info_hash
                              +"&peer_id="+local_peer.id
                              +"&port="+local_peer.port
                              +"&left="+left
                              +"&uploaded="+uploaded
                              +"&downloaded="+downloaded
                              +"&event="+event);
        return  (HttpURLConnection) tracker_url.openConnection();
    }


    public boolean[] newBitfield()
    {
        boolean[] bits = new boolean[torrent.piece_hashes.length];
        for(boolean b: bits)  b = false;
        return bits;
    }

    //Creates a readable bitfield from the bytes in a bitfield message
    public boolean[] bytesToBitfield(byte[] bytes)
    {

        boolean[] bits = new boolean[torrent.piece_hashes.length];
        for (int i = 0; i < bits.length; i++) {
            if ((bytes[i / 8] & (1 << (7 - (i % 8)))) > 0) bits[i] = true;
        }
        return bits;
    }

    //Converts the readable bitfield into a sendable message of bytes
    public byte[] bitsToBytefield(boolean[] bits)
    {
        byte[] bytes = new byte[(int)Math.ceil(bits.length/8)];
        for(byte b: bytes)b=0;
        for(int i = 0; i<bits.length;i++){
            if(bits[i])bytes[(int)Math.floor(i/8)] += Math.pow(2,7-(i%8));
        }
        return bytes;
    }

    //Verifies the hash of a file piece
    public boolean verifyHash(int piece_index, byte[] file_piece)
    {
        try{
            MessageDigest hash_function = MessageDigest.getInstance("SHA-1");
            byte[] file_piece_hash = new byte[20];
            file_piece_hash = hash_function.digest(file_piece);
            byte[] valid_hash = torrent.piece_hashes[piece_index].array();
            if(!Arrays.equals(file_piece_hash,valid_hash))return false;
            local_peer.bitfield[piece_index]=true;
            return true;
        }
        catch(NoSuchAlgorithmException e){System.err.println(e);return false;}
    }

    //Uploads a file piece to the array of file bytes and updates tracker progress
    public void savePiece(int piece_index, byte[] file_piece)
    {
        downloaded += file_piece.length;
        left -= file_piece.length;
        System.arraycopy(file_piece, 0, file_bytes, piece_index*torrent.piece_length, file_piece.length);
        Iterator<BTConnection> btIt = connections.iterator();
        BTConnection con;
        //Sends a have message to all the current connections
		while (btIt.hasNext())
        {
			con = btIt.next();
            try {con.sendHaveMessage(piece_index);}
            catch(Exception e) {System.err.println(e);}
		}
    }

}
