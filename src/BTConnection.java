// Matt Bowyer
// 156 00 9078

import java.io.*;
import java.nio.*;
import java.util.*;
import java.net.*;

public class BTConnection extends Thread
{
    BTPeer.LocalPeer local_peer;
    BTPeer remote_peer;
    BTTracker tracker;
    Socket socket;
    DataInputStream in;
    DataOutputStream out;
    boolean interested;
    boolean choked;

    public BTConnection(BTPeer.LocalPeer local_peer, BTPeer remote_peer, BTTracker tracker)
    {
        //Creates a Seeder Connection
        this.local_peer = local_peer;
        this.tracker = tracker;
        this.remote_peer = remote_peer;
        this.interested = true;
        this.choked = true;
        try{
            socket = new Socket(remote_peer.ip,remote_peer.port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        }
        catch(Exception e){System.err.println(e);}
    }

    public BTConnection(BTPeer.LocalPeer local_peer, BTPeer remote_peer, BTTracker tracker, Socket socket)
    {
        //Creates a Leecher Connection
        this.local_peer = local_peer;
        this.tracker = tracker;
        this.socket = socket;
        this.interested = false;
        this.choked = true;
        try{
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        }
        catch(Exception e){System.err.println(e);}
    }

    public void run(){
        try{

            //Always Handshake First
            handshake();

            //If I am interested notify Remote Peer and look for Unchoke Message
            if(interested) showInterest();

            //If I am not interested see if the Remote Peer is
            if(!interested) allowInterest();

            //If Local is interested and Remote is Unchoked Download Pieces
            if(interested && !choked) downloadPieces();

            //If Remote is interested and Local is Unchoked Upload Pieces
            if(!interested && !choked) uploadPieces();

        }
        catch(Exception e){
            //System.err.println(e);
            dropConnection();
        }

    }

    public void handshake() throws Exception
    {
        //Create Handshake Messages
        byte[] remote_handshake = new byte[68];
        byte[] local_handshake = new byte[68];
        byte[] handshake_header = {19,'B','i','t','T','o','r','r','e','n','t',' ','p','r','o','t','o','c','o','l',0,0,0,0,0,0,0,0};
        System.arraycopy(handshake_header, 0, local_handshake, 0, 28);
        System.arraycopy(tracker.torrent.info_hash.array(), 0, local_handshake,28 , 20);
        System.arraycopy(local_peer.id.getBytes(), 0, local_handshake,48 , 20);

        //Exchange Handshake Messages
        out.write(local_handshake);
        in.read(remote_handshake);

        //Verify Handshake
        boolean valid_handshake = true;
        if(!Arrays.equals(Arrays.copyOfRange(handshake_header,0,20),Arrays.copyOfRange(remote_handshake,0,20))){valid_handshake = false;}
        if(!Arrays.equals(tracker.torrent.info_hash.array(),Arrays.copyOfRange(remote_handshake,28,48))){valid_handshake = false;}
        remote_peer.id = new String( Arrays.copyOfRange(remote_handshake,48,68) , "UTF-8");
        if(!valid_handshake)throw new Exception("\nINVALID HANDSHAKE\t"+remote_peer);
    }

    public void showInterest() throws Exception
    {
        //Send Interest Messsage
        byte[] interest_message = {0x00,0x00,0x00,0x01,0x02};
        out.write(interest_message);

        //Get Remote Peer Bitfield
        int bitfield_length = in.readInt()-1;
        int bitfield_index = in.readByte();
        if(bitfield_index!=0x05){throw new Exception("\nNO BITFIELD RECIEVED\t"+remote_peer);}
        byte[] peer_bitfield = new byte[bitfield_length];
        in.read(peer_bitfield);
        remote_peer.bitfield = tracker.bytesToBitfield(peer_bitfield);

        //Recieve Unchoke Message from Remote Peer
        int unchoke_length = in.readInt();
        int unchoke_index = in.readByte();
        if(unchoke_index!=0x01){throw new Exception("\nPEER IS CHOKING CONNECTION\t"+remote_peer);}
        choked = false;
    }

    public void allowInterest() throws Exception
    {
        //recieve Interest
        int interest_length = in.readInt();
        int interest_index = in.readByte();
        if(interest_index!=0x02){throw new Exception("\nPEER IS NOT INTERSTED\t"+remote_peer);}

        //send bitfield
        byte[] bitfield_message = tracker.bitsToBytefield(local_peer.bitfield);
        out.write(bitfield_message);

        //send unchoked
        byte[] unchoke_message = {0x00,0x00,0x00,0x01,0x01};
        out.write(unchoke_message);

    }

    //Sends out a Request Message to Remote Peer
    public void sendRequest(int piece_index) throws Exception
    {
        int length = tracker.torrent.piece_length;
        if(piece_index==(tracker.torrent.piece_hashes.length-1))
        length = tracker.torrent.file_length-tracker.torrent.piece_length*(tracker.torrent.piece_hashes.length-1);
        byte[] request_message = new byte[17];
        byte[] request_header = {0x00,0x00,0x00,0x0D,0x06};
        System.arraycopy(request_header, 0, request_message, 0 , 5);
        System.arraycopy(ByteBuffer.allocate(4).putInt(piece_index).array(), 0, request_message,5 , 4);
        System.arraycopy(ByteBuffer.allocate(4).putInt(0).array(), 0, request_message,9 , 4);
        System.arraycopy(ByteBuffer.allocate(4).putInt(length).array(), 0, request_message,13 , 4);
        out.write(request_message);
    }

    public int getRequest() throws Exception
    {
        int length_prefix = in.readInt();
        int message_index = in.readByte();
        if(message_index != 0x07){throw new Exception("\nINCORRECT INDEX\t"+remote_peer);}
        int message_piece_index = in.readInt();
        int message_piece_offset = in.readInt();
        if(message_piece_offset != 0){throw new Exception("\nINCORRECT OFFSET\t"+remote_peer);}
        int message_piece_length = in.readInt();
        return message_piece_index;
    }
    public void sendPiece(int piece_index) throws Exception
    {
        //Get Length of peice incase its the last truncated peice
        int length = tracker.torrent.piece_length;
        if(piece_index==(tracker.torrent.piece_hashes.length-1))
        length = tracker.torrent.file_length-tracker.torrent.piece_length*(tracker.torrent.piece_hashes.length-1);

        //Create Peice Message
        byte[] piece_message = new byte[length+13];
        System.arraycopy(ByteBuffer.allocate(4).putInt(length+9).array(), 0, piece_message, 0 , 4);
        piece_message[4] = 0x07;
        System.arraycopy(ByteBuffer.allocate(4).putInt(piece_index).array(), 0, piece_message,5 , 4);
        System.arraycopy(ByteBuffer.allocate(4).putInt(0).array(), 0, piece_message,9 , 4);
        System.arraycopy(tracker.file_bytes, (piece_index*tracker.torrent.piece_length), piece_message, 13, length);

        //Send out Message
        out.write(piece_message);

        //Update tracker
        tracker.uploaded += length;

    }

    //Gets a Piece Message and Verifies its counterparts
    public byte[] getPiece(int piece_index) throws Exception
    {
        int length_prefix = in.readInt();
        if(length_prefix > tracker.torrent.piece_length+9){throw new Exception("\nINCORRECT LENGTH\t"+remote_peer);}
        int message_index = in.readByte();
        if(message_index != 0x07){throw new Exception("\nINCORRECT INDEX\t"+remote_peer);}
        int message_piece_index = in.readInt();
        if(message_piece_index != piece_index){throw new Exception("\nINCORRECT INDEX\t"+remote_peer);}
        int message_piece_offset = in.readInt();
        if(message_piece_offset != 0){throw new Exception("\nINCORRECT OFFSET\t"+remote_peer);}
        byte[] file_piece = new byte[length_prefix-9];
        in.read(file_piece);
        return file_piece;
    }

    public void uploadPieces() throws Exception
    {
        //while true
        boolean uploading = true;
        while(uploading)
        {
            int piece_index = getRequest();
            //If Local Has Piece Send it
            if(local_peer.bitfield[piece_index])
            {
                sendPiece(piece_index);
            }
            else //Otherwise Drop Connection
            {
                uploading = false;
                throw new Exception("CANNOT SEND REQUESTED PIECE");
            }

        }
    }
    public void downloadPieces() throws Exception
    {
        int piece_index = -1;
        try{
            //Actively takes requests from the queue and sends them to the remote peer for download
            while(!tracker.piece_requests.isEmpty())
            {
                piece_index = tracker.piece_requests.get(0);
                tracker.piece_requests.remove((Integer)piece_index);
                tracker.pieces_requested.add(piece_index);
                //If the remote peer has the file piece download it
                if(remote_peer.bitfield[piece_index]){
                    sendRequest(piece_index);
                    Thread.sleep(tracker.interval);
                    byte[] file_piece = getPiece(piece_index);
                    if(tracker.verifyHash(piece_index, file_piece)) tracker.savePiece(piece_index, file_piece);
                    else throw new Exception("\nINCORRECT HASH\t\t"+remote_peer);
                    System.out.println("PIECE #"+piece_index+" DOWNLOADED\t"+remote_peer);
                }
                else tracker.piece_requests.add(piece_index);
                tracker.pieces_requested.remove((Integer)piece_index);
            }
        }
        //If the File Piece was incorrectly downloaded add it back to the piece request queue
        catch(Exception e){
            if(piece_index!=-1)
            {
                tracker.pieces_requested.remove((Integer)piece_index);
                tracker.piece_requests.add(piece_index);
            }
            throw new Exception("Could Not Hold Connection");
        }
    }

    public void sendHaveMessage(int piece_index) throws Exception
    {
        //Creates a have Message
        byte[] have_message = new byte[9];
        byte[] have_header = {0x00,0x00,0x00,0x05,0x04};
        System.arraycopy(have_header, 0, have_message, 0 , 5);
        System.arraycopy(ByteBuffer.allocate(4).putInt(piece_index).array(), 0, have_message,5 , 4);
        //Sends it out
        out.write(have_message);
    }

    public void dropConnection()
    {
        try{
            in.close();
            out.close();
            socket.close();
            tracker.connections.remove(this);
            //System.out.println("CONNECTION DROPPED\t"+remote_peer);
        }
        catch(IOException e){System.err.println(e);}
    }

}
