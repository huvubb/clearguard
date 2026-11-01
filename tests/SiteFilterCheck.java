import com.youlong.hd.SiteDomainPolicy;
import com.youlong.hd.DnsPacket;
import java.util.*;
import java.io.*;
public class SiteFilterCheck {
    static byte[] packet() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream dns=new DataOutputStream(bytes);
        dns.writeShort(123);dns.writeShort(256);dns.writeShort(1);dns.write(new byte[6]);
        for(String label:new String[]{"bad","example","test"}){dns.writeByte(label.length());dns.writeBytes(label);}dns.writeByte(0);dns.writeShort(1);dns.writeShort(1);
        byte[] query=bytes.toByteArray(),packet=new byte[query.length+28];packet[0]=0x45;put(packet,2,packet.length);packet[8]=64;packet[9]=17;
        packet[12]=10;packet[13]=94;packet[15]=1;packet[16]=10;packet[17]=94;packet[19]=2;put(packet,20,40000);put(packet,22,53);put(packet,24,query.length+8);System.arraycopy(query,0,packet,28,query.length);return packet;
    }
    static void put(byte[] p,int i,int v){p[i]=(byte)(v>>>8);p[i+1]=(byte)v;}
    public static void main(String[] args) throws Exception {
        Map<String,String> categories=new HashMap<>();categories.put("example.test","threat");
        SiteDomainPolicy rules=new SiteDomainPolicy(categories,"safe.example.test","custom.test");
        assert "threat".equals(rules.blockedCategory("bad.example.test"));
        assert rules.blockedCategory("notexample.test")==null;
        assert rules.blockedCategory("deep.safe.example.test")==null;
        assert "自定义".equals(rules.blockedCategory("custom.test"));
        assert SiteDomainPolicy.normalize("*.EXAMPLE.test.").equals("example.test");
        for(String invalid:new String[]{"https://example.test","com","bad..test","127.0.0.1","example.test;id"}) {
            try{SiteDomainPolicy.parse(invalid);throw new AssertionError(invalid);}catch(IllegalArgumentException expected){}
        }
        byte[] raw=packet();DnsPacket query=new DnsPacket(raw,raw.length);assert query.domain.equals("bad.example.test");
        byte[] blocked=query.blocked();assert (blocked[31]&15)==3;assert (blocked[20]&255)==0 && (blocked[21]&255)==53;
        assert (blocked[22]&255)==156 && (blocked[23]&255)==64;
        int checksum=0;for(int i=0;i<20;i+=2)checksum+=((blocked[i]&255)<<8)+(blocked[i+1]&255);while((checksum>>>16)>0)checksum=(checksum&65535)+(checksum>>>16);assert checksum==65535;
        try{new DnsPacket(raw,raw.length-1);throw new AssertionError();}catch(IOException expected){}
        raw[40]=(byte)0xc0;try{new DnsPacket(raw,raw.length);throw new AssertionError();}catch(IOException expected){}
        byte[] wrong=query.query.clone();wrong[0]++;wrong[2]=(byte)128;try{query.reply(wrong);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("PASS site suffix boundaries, allow-list precedence, DNS parsing, NXDOMAIN response and checksums");
    }
}
