package com.youlong.hd;
import java.io.IOException;
import java.util.Arrays;

/** IPv4/UDP DNS packets only; the VPN routes only its virtual DNS address. */
public final class DnsPacket {
    public final byte[] packet, query;
    public final String domain;
    private final int questionEnd;
    public DnsPacket(byte[] bytes,int length) throws IOException {
        if (length<40 || length>bytes.length || (bytes[0]>>>4 & 15)!=4) throw new IOException("IPv4 required");
        int ip=(bytes[0]&15)*4;
        if (ip<20 || ip+20>length || bytes[9]!=17 || (u16(bytes,6)&0x3fff)!=0 || u16(bytes,2)!=length
                || u16(bytes,ip+2)!=53 || u16(bytes,ip+4)!=length-ip
                || bytes[16]!=10 || bytes[17]!=94 || bytes[18]!=0 || bytes[19]!=2) throw new IOException("invalid DNS transport");
        packet=Arrays.copyOf(bytes,length); query=Arrays.copyOfRange(bytes,ip+8,length);
        if (query.length<17 || (query[2]&0x80)!=0 || u16(query,4)!=1) throw new IOException("one DNS question required");
        StringBuilder name=new StringBuilder(); int offset=12;
        while (true) {
            if (offset>=query.length) throw new IOException("truncated name");
            int count=query[offset++]&255; if (count==0) break;
            if (count>63 || offset+count>query.length || name.length()+count>253) throw new IOException("invalid DNS label");
            if (name.length()>0) name.append('.');
            for(int i=0;i<count;i++) {int c=query[offset++]&255;if(c<33||c>126||c=='.')throw new IOException("invalid DNS name");name.append((char)c);}
        }
        if (offset+4>query.length) throw new IOException("truncated question");
        questionEnd=offset+4; domain=name.toString().toLowerCase(java.util.Locale.ROOT);
    }
    public byte[] blocked() {
        byte[] response=Arrays.copyOf(query,questionEnd);
        response[2]=(byte)(0x80 | (query[2]&1)); response[3]=(byte)0x83;
        Arrays.fill(response,6,12,(byte)0);
        return reply(response);
    }
    public byte[] reply(byte[] dns) {
        if (dns.length<12 || dns.length>65507 || dns[0]!=query[0] || dns[1]!=query[1] || (dns[2]&0x80)==0)
            throw new IllegalArgumentException("invalid DNS response");
        byte[] result=new byte[28+dns.length];result[0]=0x45;put16(result,2,result.length);result[8]=64;result[9]=17;
        System.arraycopy(packet,16,result,12,4);System.arraycopy(packet,12,result,16,4);
        int ip=(packet[0]&15)*4;put16(result,20,53);put16(result,22,u16(packet,ip));put16(result,24,8+dns.length);
        System.arraycopy(dns,0,result,28,dns.length);
        long sum=0;for(int i=0;i<20;i+=2)sum+=u16(result,i);while((sum>>>16)!=0)sum=(sum&65535)+(sum>>>16);put16(result,10,(int)~sum);
        return result;
    }
    private static int u16(byte[] b,int i) {return (b[i]&255)*256+(b[i+1]&255);}
    private static void put16(byte[] b,int i,int value) {b[i]=(byte)(value>>>8);b[i+1]=(byte)value;}
}
