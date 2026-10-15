package emotes;

import java.io.ByteArrayOutputStream;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

public record EmoteQuery(String type,String category,String subgroup,String q,boolean alternates,int limit,int offset,String version) {
    public static EmoteQuery parse(String raw,boolean categories) {
        Map<String,String> fields=new HashMap<>();
        Set<String> allowed=categories?Set.of("type","includeAlternates","version"):Set.of("type","includeAlternates","version","category","subgroup","q","limit","offset");
        if(raw!=null && !raw.isEmpty()) for(String pair:raw.split("&",-1)) {
            String[] kv=pair.split("=",2);String key=decode(kv[0]),value=decode(kv.length==2?kv[1]:"");
            if(!allowed.contains(key) || fields.putIfAbsent(key,value)!=null) throw invalid();
        }
        String type=fields.getOrDefault("type",categories?"all":"emoji"),alternate=fields.getOrDefault("includeAlternates","false"),q=fields.getOrDefault("q","");
        if(!Set.of("all","emoji","ascii","kaomoji","symbol").contains(type) || !Set.of("true","false").contains(alternate) || q.codePointCount(0,q.length())>100) throw invalid();
        String category=fields.get("category"),subgroup=fields.get("subgroup"),version=fields.get("version");
        if(subgroup!=null && (!type.equals("emoji") || category==null) || category!=null && category.isEmpty() || subgroup!=null && subgroup.isEmpty() || version!=null && version.isEmpty())throw invalid();
        return new EmoteQuery(type,category,subgroup,q,alternate.equals("true"),number(fields,"limit",60,1,100),number(fields,"offset",0,0,5369),version);
    }
    private static int number(Map<String,String> f,String key,int fallback,int low,int high) {
        if(!f.containsKey(key))return fallback;String s=f.get(key);if(!s.matches("[0-9]+"))throw invalid();
        try{int n=Integer.parseInt(s);if(n<low || n>high)throw invalid();return n;}catch(NumberFormatException e){throw invalid();}
    }
    private static String decode(String value) {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int i=0;i<value.length();i++) {
            char ch=value.charAt(i);
            if(ch=='%') {if(i+2>=value.length())throw invalid();int a=Character.digit(value.charAt(++i),16),b=Character.digit(value.charAt(++i),16);if(a<0||b<0)throw invalid();bytes.write(a*16+b);}
            else if(ch<=127)bytes.write(ch=='+'?' ':ch);
            else throw invalid(); // Raw request-target is ASCII; Unicode must be UTF-8 percent encoded.
        }
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();}
        catch(CharacterCodingException e){throw invalid();}
    }
    static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid catalog query");}
}
