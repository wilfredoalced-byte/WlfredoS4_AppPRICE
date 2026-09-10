package com.example.proyecto_acta_prais;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import java.net.URI;

public class Session {
    private final SharedPreferences prefs;
    public Session(Context c) { prefs=c.getSharedPreferences("price_session",Context.MODE_PRIVATE); }
    public String url() { return prefs.getString("url","http://10.0.2.2:8000/"); }
    public static String normalize(String value) {
        String s=value.trim();
        if(s.endsWith("/api/")) s=s.substring(0,s.length()-4);
        else if(s.endsWith("/api")) s=s.substring(0,s.length()-3);
        if(!s.endsWith("/")) s+="/";
        URI u=URI.create(s);
        if(!("http".equals(u.getScheme())||"https".equals(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)
            throw new IllegalArgumentException("Ingrese una URL válida, por ejemplo http://10.0.2.2:8000/");
        return s;
    }
    public void setUrl(String s) { prefs.edit().putString("url",normalize(s)).apply(); }
    public String token() { return prefs.getString("token",""); }
    public Models.User user() { return new Gson().fromJson(prefs.getString("user","null"),Models.User.class); }
    public boolean valid() { return user()!=null && !token().isEmpty() && System.currentTimeMillis()/1000 < prefs.getLong("expiry",0); }
    public boolean writable() { return valid() && !"CONSULTA".equals(user().rol); }
    public boolean admin() { return valid() && "ADMIN".equals(user().rol); }
    public String scope() { return url()+"|"+(user()==null?0:user().id); }
    public void save(Models.LoginResponse r) { prefs.edit().putString("token",r.token).putString("user",new Gson().toJson(r.usuario)).putLong("expiry",r.expires_at).apply(); }
    public void clear() { prefs.edit().remove("token").remove("user").remove("expiry").apply(); }
}
