package com.example.proyecto_acta_prais;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;

/** Cola persistente por servidor y usuario. Nunca sustituye cambios pendientes al descargar. */
public class LocalStore extends SQLiteOpenHelper {
    private final String scope;
    private final Gson gson=new Gson();
    public LocalStore(Context c,String s) { super(c,"price_local.db",null,1); scope=s; }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE registros(scope TEXT NOT NULL,tipo TEXT NOT NULL,uuid TEXT NOT NULL,payload TEXT NOT NULL,dirty INTEGER NOT NULL DEFAULT 0,revision INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '',PRIMARY KEY(scope,tipo,uuid))");
        db.execSQL("CREATE TABLE catalogos(scope TEXT NOT NULL,tipo TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(scope,tipo))");
        db.execSQL("CREATE TABLE recuperacion(scope TEXT NOT NULL,uuid TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(scope,uuid))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { }
    public synchronized void save(String type,Models.Record data,boolean dirty) {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv=new ContentValues();cv.put("payload",gson.toJson(data));cv.put("dirty",dirty?1:0);cv.put("error","");
            long rev=revision(type,data.uuid);
            cv.put("revision",rev+1);
            if(db.update("registros",cv,"scope=? AND tipo=? AND uuid=?",new String[]{scope,type,data.uuid})==0) {
                cv.put("scope",scope);cv.put("tipo",type);cv.put("uuid",data.uuid);db.insertOrThrow("registros",null,cv);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public long revision(String type,String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT revision FROM registros WHERE scope=? AND tipo=? AND uuid=?",new String[]{scope,type,id})) { return c.moveToFirst()?c.getLong(0):0; }
    }
    public boolean dirty(String type,String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT dirty FROM registros WHERE scope=? AND tipo=? AND uuid=?",new String[]{scope,type,id})) { return c.moveToFirst()&&c.getInt(0)==1; }
    }
    public String error(String type,String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT error FROM registros WHERE scope=? AND tipo=? AND uuid=?",new String[]{scope,type,id})) { return c.moveToFirst()?c.getString(0):""; }
    }
    public void error(String type,String id,String message) {
        ContentValues cv=new ContentValues();cv.put("error",message);getWritableDatabase().update("registros",cv,"scope=? AND tipo=? AND uuid=?",new String[]{scope,type,id});
    }
    public <T> T get(String type,String id,Class<T> clazz) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM registros WHERE scope=? AND tipo=? AND uuid=?",new String[]{scope,type,id})) { return c.moveToFirst()?gson.fromJson(c.getString(0),clazz):null; }
    }
    public <T> List<T> list(String type,Class<T> clazz,boolean pending) {
        List<T> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM registros WHERE scope=? AND tipo=?"+(pending?" AND dirty=1":"")+" ORDER BY rowid DESC",new String[]{scope,type})) { while(c.moveToNext()) out.add(gson.fromJson(c.getString(0),clazz)); }
        return out;
    }
    public synchronized <T extends Models.Record> void merge(String type,List<T> remote,Class<T> clazz) {
        List<T> existing=list(type,clazz,false);
        for(T r:remote) if(!dirty(type,r.uuid)) save(type,r,false);
        for(T old:existing) {
            boolean found=false;for(T r:remote) if(r.uuid.equals(old.uuid)) { found=true;break; }
            if(!found&&!dirty(type,old.uuid)) remove(type,old.uuid);
        }
    }
    public synchronized void acknowledge(String type,Models.Record remote,long sentRevision) {
        if(revision(type,remote.uuid)==sentRevision) save(type,remote,false);
        else {
            Models.Record local="establecimientos".equals(type)?get(type,remote.uuid,Models.Establishment.class):get(type,remote.uuid,Models.Fiscalization.class);
            if(local!=null) { local.id=remote.id;local.version=remote.version;save(type,local,true); }
        }
    }
    public void remove(String type,String id) { getWritableDatabase().delete("registros","scope=? AND tipo=? AND uuid=?",new String[]{scope,type,id});clearRecovery(id); }
    public int pending() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM registros WHERE scope=? AND dirty=1",new String[]{scope})) { c.moveToFirst();return c.getInt(0); }
    }
    public void catalogs(String type,List<Models.Catalog> items) {
        ContentValues cv=new ContentValues();cv.put("scope",scope);cv.put("tipo",type);cv.put("payload",gson.toJson(items));getWritableDatabase().insertWithOnConflict("catalogos",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public List<Models.Catalog> catalogs(String type) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM catalogos WHERE scope=? AND tipo=?",new String[]{scope,type})) {
            return c.moveToFirst()?gson.fromJson(c.getString(0),new TypeToken<List<Models.Catalog>>(){}.getType()):new ArrayList<>();
        }
    }
    public void recovery(Models.Fiscalization f) {
        ContentValues cv=new ContentValues();cv.put("scope",scope);cv.put("uuid",f.uuid);cv.put("payload",gson.toJson(f));getWritableDatabase().insertWithOnConflict("recuperacion",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public Models.Fiscalization recovery(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM recuperacion WHERE scope=? AND uuid=?",new String[]{scope,id})) { return c.moveToFirst()?gson.fromJson(c.getString(0),Models.Fiscalization.class):null; }
    }
    public void clearRecovery(String id) { getWritableDatabase().delete("recuperacion","scope=? AND uuid=?",new String[]{scope,id}); }
}
