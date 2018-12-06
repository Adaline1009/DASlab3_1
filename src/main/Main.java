package main;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import main.jBitTorrent.BDecoder;
import main.jBitTorrent.BEncoder;
import main.jBitTorrent.DownloadManager;
import main.jBitTorrent.Peer;
import main.jBitTorrent.TorrentFile;
import main.jBitTorrent.TorrentProcessor;
import main.jBitTorrent.Utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class Main
{

    public static void main(String[] args) throws Exception
    {
        //
        String fileName2 = "E:\\ubuntu-14.04.5-server-i386.iso.torrent";
        String fileName = "C:\\Users\\Даниил\\IdeaProjects\\1\\DASlab3\\src\\main\\resources\\ubuntu-14.04.5-server-i386.iso.torrent";
        Bencode bencode = new Bencode();
        RandomAccessFile f = new RandomAccessFile(fileName, "r");
        byte[] b = new byte[(int) f.length()];
        f.readFully(b);
        Map torrent = bencode.decode(b, Type.DICTIONARY);
        String url = (String) torrent.get("announce");
        Date creationDate = new Date((Long) torrent.get("creation date"));
        long size = ((Long) ((Map) torrent.get("info")).get("length")).longValue();

        System.out.println("Url: "+url);
        System.out.println("Creation date: "+creationDate);
        System.out.println("size: "+size);
        try
        {
            TorrentProcessor tp = new TorrentProcessor();

            TorrentFile t = tp.getTorrentFile(tp.parseTorrent(fileName));
            DownloadManager dm = new DownloadManager(t, Utils.generateID());
            dm.startListening(6881, 6889);
            dm.startTrackerUpdate();
            dm.blockUntilCompletion();
            dm.stopTrackerUpdate();
            dm.closeTempFiles();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    private static Map sendRequestToTracker(final Map torrent) throws IOException
    {
        String symbols = "0123456789";
        String peerId = new Random().ints(20, 0, symbols.length())
                .mapToObj(symbols::charAt)
                .map(Object::toString)
                .collect(Collectors.joining());

        Map torrentInfo = (Map) torrent.get("info");
        byte[] info_hash_as_binary = Utils.hash(BEncoder.encode(torrentInfo));
        String info_hash_as_url = Utils.byteArrayToURLString(info_hash_as_binary);
        long size = ((Long) ((Map) torrent.get("info")).get("length")).longValue();

        String endpoint =
                torrent.get("announce") + "?info_hash=" + info_hash_as_url + "&peer_id=" + peerId + "&port=" + 6889 +
                        "&downloaded=" + 0 + "&uploaded=" + 0 + "&left=" + size + "&compact=1";
        URL source = new URL(endpoint);
        URLConnection uc = source.openConnection();
        InputStream is = uc.getInputStream();

        BufferedInputStream bis = new BufferedInputStream(is);
        Map response = BDecoder.decode(bis);
        bis.close();
        is.close();
        return response;
    }


    private static Map getPeers(final Map response)
    {
        Object peers = response.get("peers");
        ArrayList peerList = new ArrayList();
        Map l = new LinkedHashMap<String, Peer>();
        if (peers instanceof List)
        {
            peerList.addAll((List) peers);
            if (peerList != null && peerList.size() > 0)
            {
                for (int i = 0; i < peerList.size(); i++)
                {
                    String peerID = new String((byte[]) ((Map) (
                            peerList.
                                    get(i))).
                            get(
                                    "peer_id"));
                    String ipAddress = new String((byte[]) ((Map) (
                            peerList.
                                    get(
                                            i))).
                            get("ip"));
                    int port = ((Long) ((Map) (peerList.get(i))).get(
                            "port")).intValue();
                    Peer p = new Peer(peerID, ipAddress, port);
                    l.put(p.toString(), p);
                }
            }
        }
        else if (peers instanceof byte[])
        {
            byte[] p = ((byte[]) peers);
            for (int i = 0; i < p.length; i += 6)
            {
                Peer peer = new Peer();
                peer.setIP(Utils.byteToUnsignedInt(p[i]) + "." +
                        Utils.byteToUnsignedInt(p[i + 1]) + "." +
                        Utils.byteToUnsignedInt(p[i + 2]) + "." +
                        Utils.byteToUnsignedInt(p[i + 3]));
                peer.setPort(Utils.byteArrayToInt(Utils.subArray(p,
                        i + 4, 2)));
                l.put(peer.toString(), peer);
            }
        }
        return l;
    }
}
