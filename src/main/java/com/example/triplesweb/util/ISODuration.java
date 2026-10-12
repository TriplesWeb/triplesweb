package com.example.triplesweb.util;

public class ISODuration {
    public static String toClock(String iso){
        if (iso==null||iso.isBlank()) return "";
        // 매우 단순 파싱 (PT#H#M#S)
        String t = iso.replace("PT","").toUpperCase();
        int h=0,m=0,s=0;
        try{ if(t.contains("H")){ h=Integer.parseInt(t.split("H")[0]); t=t.substring(t.indexOf("H")+1);} }catch(Exception ignored){}
        try{ if(t.contains("M")){ m=Integer.parseInt(t.split("M")[0]); t=t.substring(t.indexOf("M")+1);} }catch(Exception ignored){}
        try{ if(t.contains("S")){ s=Integer.parseInt(t.split("S")[0]); } }catch(Exception ignored){}
        return h>0 ? String.format("%d:%02d:%02d",h,m,s) : String.format("%d:%02d",m,s);
    }
}
