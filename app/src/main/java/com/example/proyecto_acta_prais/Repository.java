package com.example.proyecto_acta_prais;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class Repository {
    public static final ExecutorService IO=Executors.newSingleThreadExecutor();
    public final Session session;
    public final LocalStore store;
    public final ApiService api;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    public interface Job<T> { T run() throws Exception; }
    public interface Result<T> { void done(T value,String error); }
    public static <T> void async(Job<T> job,Result<T> callback) {
        IO.execute(()-> { try { T value=job.run();MAIN.post(()->callback.done(value,null)); }
            catch(Exception e) { String message=e instanceof IOException?"Sin conexión con la API. Sus registros permanecen guardados en el teléfono.":e.getMessage();MAIN.post(()->callback.done(null,message==null?"No se pudo completar la operación.":message)); } });
    }
    public Repository(Context c) {
        session=new Session(c);store=new LocalStore(c.getApplicationContext(),session.scope());
        final String token=session.token();
        OkHttpClient http=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS).writeTimeout(35,TimeUnit.SECONDS)
            .addInterceptor(chain->{ okhttp3.Request.Builder r=chain.request().newBuilder();if(!token.isEmpty())r.header("Authorization","Bearer "+token);return chain.proceed(r.build()); }).build();
        api=new Retrofit.Builder().baseUrl(session.url()).client(http).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService.class);
    }
    public static <T> T require(Response<T> r) throws Exception {
        if(r.isSuccessful()&&r.body()!=null) return r.body();
        String msg="Error de API ("+r.code()+").";
        if(r.errorBody()!=null) { try { msg=JsonParser.parseString(r.errorBody().string()).getAsJsonObject().get("message").getAsString(); } catch(Exception ignored) {} }
        throw new Exception(msg);
    }
    public String sync() throws Exception {
        require(api.me().execute());
        List<String> errors=new ArrayList<>();
        for(Models.Establishment e:store.list("establecimientos",Models.Establishment.class,true)) {
            long revision=store.revision("establecimientos",e.uuid);
            try {
                if(e.id==0) {
                    Response<Models.Establishment> lookup=api.establishment(e.uuid).execute();
                    if(lookup.isSuccessful()) { Models.Establishment r=require(lookup);e.id=r.id;e.version=r.version; }
                    else if(lookup.code()!=404) require(lookup);
                }
                Models.Establishment saved=require((e.id==0?api.createEstablishment(e):api.updateEstablishment(e.uuid,e)).execute());
                store.acknowledge("establecimientos",saved,revision);
            } catch(IOException e1) { throw e1; }
              catch(Exception e1) { store.error("establecimientos",e.uuid,e1.getMessage());errors.add(e.agente+": "+e1.getMessage()); }
        }
        for(Models.Fiscalization f:store.list("fiscalizaciones",Models.Fiscalization.class,true)) {
            long revision=store.revision("fiscalizaciones",f.uuid);
            try {
                if(store.dirty("establecimientos",f.establecimiento_uuid)) throw new Exception("Primero sincronice el establecimiento asociado.");
                if(f.id==0) {
                    Response<Models.Fiscalization> lookup=api.fiscalization(f.uuid).execute();
                    if(lookup.isSuccessful()) { Models.Fiscalization r=require(lookup);f.id=r.id;f.version=r.version; }
                    else if(lookup.code()!=404) require(lookup);
                }
                Models.Fiscalization saved=require((f.id==0?api.createFiscalization(f):api.updateFiscalization(f.uuid,f)).execute());
                store.acknowledge("fiscalizaciones",saved,revision);
            } catch(IOException e) { throw e; }
              catch(Exception e) { store.error("fiscalizaciones",f.uuid,e.getMessage());errors.add(f.expediente+": "+e.getMessage()); }
        }
        store.catalogs("incumplimientos",require(api.findings().execute()));
        store.catalogs("productos",require(api.products().execute()));
        store.merge("establecimientos",require(api.establishments("").execute()),Models.Establishment.class);
        store.merge("fiscalizaciones",require(api.fiscalizations("").execute()),Models.Fiscalization.class);
        return errors.isEmpty()?"Sincronización completa. Pendientes: "+store.pending():"Pendientes conservados:\n"+android.text.TextUtils.join("\n",errors);
    }
}
