package dnsClient;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;

public class DNSResponse {
    private int queryID;                  // this is for the response it must match the one in the request 
    protected int answerCount = 0;          // number of answers
    private boolean decoded = false;      // Was this response successfully decoded
    private int nsCount = 0;              // number of nscount response records
    private int additionalCount = 0;      // number of additional (alternate) response records
    protected boolean authoritative = false;// Is this an authoritative record

    // Note you will almost certainly need some additional instance variables.
    class rData{
        String ipAddress;
        String name;
        int type;
        int classInt;
        long ttl;
        int rdLength;
        int totalOffset;
        rData(){
            return;
        }
    }
    // When in trace mode you probably want to dump out all the relevant information in a response
    DNSResponse(){
        return;
    }

    private static String getString(byte[] arr, int offset, int count){
        String result = "";
        for(int i = 0; i < count; i++){
            char c = (char) arr[offset+i];
            result+= c;
        }
        return result+".";
    }

    private int convertBytesToInt (byte [] arr, int offset)      // unsigned
    {
        return (arr[offset] & 0xFF) << 8 | (arr[offset+1] & 0xFF);
    }

    private int convert4BytesToInt(byte[] bytes, int offset) {
        return bytes[offset] << 24 | (bytes[offset+1] & 0xFF) << 16 | (bytes[offset+2] & 0xFF) << 8 | (bytes[offset+3] & 0xFF);
    }

    private static String getActualType(int i){
        String actualType = "";
        switch (i){
            case 1:{
                actualType = "A";
                break;
            }
            case 2:{
                actualType = "NS";
                break;
            }
            case 28:{
                actualType = "AAAA";
                break;
            }
            case 5:{
                actualType = "CN";
                break;
            }
        }
        return actualType;
    }

    byte[] encodeQuery(String query) {

        byte[] encodedQuery = new byte[58];

        try {
            String[] split = query.split("\\.");
            int counter = 12;
            //FIX THIS SHIT
            int queryId = (int) (Math.random() * 255);
            encodedQuery[1] = (byte) queryId;
            encodedQuery[2] = 0;
            encodedQuery[3] = 0;
            encodedQuery[5] = 1;
            for (int i = 0; i < split.length; i++) {
                encodedQuery[counter] = (byte) split[i].length();
                counter++;
                for (int j = 0; j < split[i].length(); j++) {
                    String a = split[i];
                    char c = a.charAt(j);
                    String hexForAscii = Integer.toHexString((int) c);
                    byte test = Byte.parseByte(hexForAscii, 16);
                    encodedQuery[counter] = test;
                    counter++;
                }
            }
            encodedQuery[counter + 2] = 1;
            encodedQuery[counter + 4] = 1;
        }catch(Exception e){
            e.printStackTrace();
        }
        return encodedQuery;
    }

    public rData readRDATA (byte [] arr, int offset){
        rData rData = new rData();

        if ((arr[offset] & 0xFF) == 192){
//            System.out.print("h");
            int npointer = arr[arr[offset+1] + 1 & 0xFF]  & 0xFF;
//            System.out.println(npointer);
            rData.name = "";
            byte[] nameb = new byte[128];

            for (int i  = 0; i< 50; i++) {
                if ((arr[arr[offset + 1] + i &0xFF ]   & 0xFF) == 0) break;

                if ((arr[arr[offset + 1] + i + 1 &0xFF]) > 30) {
                    nameb[i] = arr[arr[offset+1] + i + 1 & 0xff];
                } else {
                    nameb[i] = 0x2e;
                }
                }
            try {
                rData.name = new String(nameb, "ASCII");
//                System.out.println(rData.name);
            } catch (Exception E) {
            }


        }
        rData.type = this.convertBytesToInt(arr, offset+2);
        rData.classInt = this.convertBytesToInt(arr, offset+4);
        rData.ttl = this.convert4BytesToInt(arr, offset+6);
        rData.rdLength = this.convertBytesToInt(arr, offset+10);
        int innerOffset = offset+12;
        String ipAddress = "";
        if(rData.type == 1 && rData.rdLength == 4){
            byte[] nextIp = new byte[4];
            nextIp[0] = arr[innerOffset];
            nextIp[1] = arr[innerOffset+1];
            nextIp[2] = arr[innerOffset+2];
            nextIp[3] = arr[innerOffset+3];
            try {
                rData.ipAddress = InetAddress.getByAddress(nextIp).toString().replaceAll("[/]","");
            }
            catch (Exception e) {
            }
        }
        if(rData.type == 28 && rData.rdLength ==16){
            byte[] tmp2 = new byte[16];
            System.arraycopy(arr, innerOffset, tmp2, 0, 16);
            try {
                rData.ipAddress= Inet6Address.getByAddress(tmp2).toString().replaceAll("[/]","");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else{
            for (int i = 0; i < rData.rdLength; i++)
            {
                int ipBits = arr[innerOffset+i];
                ipAddress += ipBits + ".";
            }
            try {
                rData.ipAddress = InetAddress.getByName(ipAddress.substring(0, ipAddress.length() - 1)).toString();
            }catch(Exception e){
//                e.printStackTrace();
            }
        }
        rData.totalOffset = 12+rData.rdLength;
        return rData;
    }

    rData decodeQuery(byte[] response, DNSlookup looker) throws  Exception{
        byte[] defaultadd = new byte[4];
        InetAddress s = InetAddress.getByAddress(defaultadd);
        String qname = "";
        int counter = 0;
        this.queryID = this.convertBytesToInt(response, 0);
        int questionCount = this.convertBytesToInt(response, 4);
        this.answerCount = this.convertBytesToInt(response, 6);
        this.nsCount = this.convertBytesToInt(response,8);
        this.additionalCount = this.convertBytesToInt(response, 10);
        int offset = 12;
        while (response[offset] != 0){
            offset++;
        }
        if(this.answerCount >= 1){
            this.authoritative = true;
        }
        if(looker.tracingOn){
            String queryType = "A";
            if(looker.IPV6Query){
                queryType = "AAAA";
            }
            System.out.println();
            System.out.println();
            System.out.println("Query ID     "+this.queryID+" "+" "+looker.queryString+"  "+queryType+" --> "+looker.dnsString);
            System.out.println("Response ID "+this.queryID+" Authoritative = "+this.authoritative);
            System.out.println("  Answers ("+this.answerCount+")");
        }
        int queryType = this.convertBytesToInt(response, offset+1);
        int queryClass = this.convertBytesToInt(response, offset+3);
        offset +=5;
        int totalCount = this.nsCount + this.additionalCount +this.answerCount;
        ArrayList<rData> rDataList = new ArrayList<rData>();

        for (int i = 0; i < totalCount; i++){
            rData r = readRDATA(response, offset);
            offset += r.totalOffset;
            if(looker.tracingOn){
                if(i == this.answerCount) {
                    System.out.println("  Nameservers (" + this.nsCount + ")");
                }
                if(i == this.nsCount+this.answerCount){
                    System.out.println("  Additional Information ("+this.additionalCount+")");
                }

                System.out.format("       %-30s %-10d %-4s %s\n", r.name, r.ttl, getActualType(r.type), r.ipAddress);
            }
            rDataList.add(r);
        }
        for(rData c: rDataList) {
            if(this.answerCount >= 1){
                return rDataList.get(0);
            }
            if(c.type == 1){
                return c;
            }
        }
        return null;
    }
}

