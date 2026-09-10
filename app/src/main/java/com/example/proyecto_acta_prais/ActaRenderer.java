package com.example.proyecto_acta_prais;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Base64;
import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** La vista previa renderiza este PDF, sin una segunda plantilla HTML. */
public class ActaRenderer {
    static class Field {String key,align;int page;float x,y,w,h,size;boolean bold;}
    static class Spec {float width,height;List<Field> fields,masks,signatures;}
    private final Context context;
    private final Spec spec;
    public ActaRenderer(Context c)throws IOException {
        context=c.getApplicationContext();
        try(Reader reader=new InputStreamReader(context.getAssets().open("acta_layout.json"),StandardCharsets.UTF_8)){spec=new Gson().fromJson(reader,Spec.class);}
    }
    private String value(Models.Fiscalization f,String key){
        String[] p=key.split("\\.");
        switch(p[0]) {
            case "datos":return f.datos.getOrDefault(p[1],"");
            case "expediente":return f.expediente;
            case "fecha":return f.fecha;
            case "hora_apertura":return f.hora_apertura;
            case "hora_cierre":return f.hora_cierre;
            case "hecho":for(Models.Finding i:f.incumplimientos)if(i.incumplimiento_id==Integer.parseInt(p[1]))return i.hecho;return "";
            case "check":Boolean checked=f.verificaciones.get(p[1]);return checked!=null&&checked==Boolean.parseBoolean(p[2])?"X":"";
            case "marca":for(Models.Price i:f.precios)if(i.producto_id>=7&&i.fila==Integer.parseInt(p[1]))return i.marca;return "";
            case "precio":for(Models.Price i:f.precios)if(i.producto_id==Integer.parseInt(p[1])&&i.fila==Integer.parseInt(p[2])){switch(p[3]){case "price":return i.price;case "publicado":return i.publicado;case "surtidor":return i.surtidor;case "descuento":return i.descuento;}}return "";
            default:return "";
        }
    }
    private StaticLayout fit(Field box,String text)throws IOException {
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTypeface(Typeface.create("sans-serif",box.bold?Typeface.BOLD:Typeface.NORMAL));
        for(float size=box.size;size>=Math.min(box.size,box.key.startsWith("precio.")?3.5f:6.5f);size-=.25f){
            paint.setTextSize(size);
            StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,Math.max(1,(int)box.w-2)).setAlignment("center".equals(box.align)?Layout.Alignment.ALIGN_CENTER:Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(0,1.12f).build();
            if(layout.getHeight()<=box.h)return layout;
        }
        throw new IOException("El texto de "+box.key.replace("datos.","")+" no cabe en el acta. Resúmalo para conservar las dos páginas.");
    }
    public void validateFit(Models.Fiscalization f)throws IOException {for(Field box:spec.fields){String text=value(f,box.key);if(!text.isEmpty())fit(box,text);}}
    public void write(Models.Fiscalization f,OutputStream output)throws IOException {
        validateFit(f);
        PdfDocument pdf=new PdfDocument();
        try {
            for(int i=0;i<2;i++){
                PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,i+1).create());Canvas c=page.getCanvas();
                c.save();c.scale(595/spec.width,842/spec.height);
                try(InputStream in=context.getAssets().open("acta_price_p"+(i+1)+".png")){
                    Bitmap bg=BitmapFactory.decodeStream(in);if(bg==null)throw new IOException("No se pudo abrir la plantilla del acta.");c.drawBitmap(bg,null,new RectF(0,0,spec.width,spec.height),new Paint(Paint.FILTER_BITMAP_FLAG));bg.recycle();
                }
                Paint white=new Paint();white.setColor(Color.WHITE);
                for(Field m:spec.masks)if(m.page==i)c.drawRect(m.x,m.y,m.x+m.w,m.y+m.h,white);
                for(Field box:spec.fields)if(box.page==i){String text=value(f,box.key);if(text.isEmpty())continue;StaticLayout layout=fit(box,text);c.save();c.translate(box.x+1,box.y);layout.draw(c);c.restore();}
                for(Field box:spec.signatures)if(box.page==i){String encoded=f.firmas.getOrDefault(box.key,"");if(encoded.isEmpty())continue;byte[] raw=Base64.decode(encoded,Base64.DEFAULT);Bitmap signature=BitmapFactory.decodeByteArray(raw,0,raw.length);if(signature==null)throw new IOException("Firma inválida.");RectF dest=new RectF(box.x,box.y,box.x+box.w,box.y+box.h);float scale=Math.min(box.w/signature.getWidth(),box.h/signature.getHeight());float sw=signature.getWidth()*scale,sh=signature.getHeight()*scale;dest.set(box.x+(box.w-sw)/2,box.y+(box.h-sh)/2,box.x+(box.w+sw)/2,box.y+(box.h+sh)/2);c.drawBitmap(signature,null,dest,new Paint(Paint.FILTER_BITMAP_FLAG));signature.recycle();}
                c.restore();pdf.finishPage(page);
            }
            pdf.writeTo(output);
        }finally{pdf.close();}
    }
}
