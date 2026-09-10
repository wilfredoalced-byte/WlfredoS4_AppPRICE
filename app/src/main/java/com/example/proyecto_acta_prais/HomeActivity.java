package com.example.proyecto_acta_prais;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.io.*;
import java.util.*;

public class HomeActivity extends AppCompatActivity {
    private Repository repo;
    private LinearLayout body,list;
    private TextView status;
    private String mode="inicio", query="";
    private boolean syncing=false,first=true;
    @Override protected void onCreate(Bundle state) { super.onCreate(state);repo=new Repository(this); }
    @Override protected void onResume() {
        super.onResume();
        if(!repo.session.valid()) { startActivity(new Intent(this,LoginActivity.class));finish();return; }
        show(mode);
        if(first) { first=false;sync(false); }
    }
    private void show(String section) {
        mode=section;
        body=Ui.screen(this,"PRICE",repo.session.user().nombre+" · "+repo.session.user().rol);
        status=Ui.text(body,"Guardados en el teléfono · Pendientes de sincronización: "+repo.store.pending(),13);
        if(!"inicio".equals(mode))Ui.button(body,"Volver al menú",v->show("inicio"));
        switch(mode) {
            case "establecimientos":case "fiscalizaciones": showList();break;
            case "reportes": reports();break;
            case "configuracion": settings();break;
            default:
                Ui.text(body,"Menú principal",20);
                Ui.menuButton(body,R.drawable.ic_building,"Establecimientos",v->{query="";show("establecimientos");});
                Ui.menuButton(body,R.drawable.ic_clipboard,"Fiscalizaciones",v->{query="";show("fiscalizaciones");});
                Ui.menuButton(body,R.drawable.ic_chart,"Reportes",v->show("reportes"));
                Ui.menuButton(body,R.drawable.ic_settings,"Configuración",v->show("configuracion"));
                Ui.menuButton(body,R.drawable.ic_sync,"Sincronizar ahora",v->sync(true));
                Ui.text(body,"Puede trabajar sin conexión después del primer ingreso y de descargar los catálogos. La sesión dura 8 horas.",14);
        }
    }
    private void sync(boolean dialog) {
        if(syncing)return;syncing=true;status.setText("Sincronizando registros y catálogos…");
        Repository.async(repo::sync,(value,error)->{syncing=false;if(isFinishing())return;show(mode);status.setText(error==null?value:error);if(dialog)Ui.message(this,error==null?value:error);});
    }
    private void showList() {
        boolean est="establecimientos".equals(mode);
        Ui.text(body,est?"Establecimientos":"Fiscalizaciones",21);
        EditText search=Ui.field(body,est?"Buscar agente, RUC o código":"Buscar expediente, agente, código, RUC o fecha",query,InputType.TYPE_CLASS_TEXT);
        Ui.button(body,"Actualizar y sincronizar",v->sync(true));
        if(repo.session.writable())Ui.button(body,est?"Agregar establecimiento":"Nueva fiscalización",v->{if(est)editEstablishment(new Models.Establishment());else chooseEstablishment();});
        list=Ui.column(this);body.addView(list);renderList();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();renderList();}public void afterTextChanged(Editable e){} });
    }
    private void renderList() {
        list.removeAllViews();String q=query.toLowerCase(Locale.ROOT);int count=0;
        if("establecimientos".equals(mode)) {
            for(Models.Establishment e:repo.store.list(mode,Models.Establishment.class,false)) {
                if(!(e.agente+" "+e.ruc+" "+e.codigo+" "+e.direccion).toLowerCase(Locale.ROOT).contains(q))continue;
                count++;LinearLayout card=Ui.card(list);Ui.text(card,e.agente,18);Ui.text(card,"Código: "+e.codigo+" · RUC/DNI: "+e.ruc+"\n"+e.direccion,14);syncLabel(card,mode,e.uuid);
                Ui.button(card,"Ver detalle",v->establishmentDetails(e));
                if(repo.session.writable()) {Ui.button(card,"Editar",v->editEstablishment(e));Ui.button(card,"Crear fiscalización aquí",v->newFiscalization(e));}
                if(repo.session.admin())Ui.button(card,"Eliminar",v->delete("establecimientos",e));
            }
        } else {
            for(Models.Fiscalization f:repo.store.list(mode,Models.Fiscalization.class,false)) {
                if(!(f.expediente+" "+f.fecha+" "+f.datos.get("agente")+" "+f.datos.get("codigo")+" "+f.datos.get("ruc")).toLowerCase(Locale.ROOT).contains(q))continue;
                count++;LinearLayout card=Ui.card(list);Ui.text(card,f.expediente,18);Ui.text(card,f.datos.get("agente")+"\n"+f.fecha+" · "+f.estado,14);syncLabel(card,mode,f.uuid);
                Ui.button(card,f.closed()?"Consultar acta":"Abrir fiscalización",v->openFiscalization(f.uuid));
                Ui.button(card,"Historial",v->history(f));
                if(f.closed()&&f.id!=0)Ui.button(card,"Ver PDF del servidor",v->serverPdf(f));
                if(repo.session.admin()&&!f.closed())Ui.button(card,"Eliminar",v->delete("fiscalizaciones",f));
            }
        }
        if(count==0)Ui.text(list,"No hay registros para mostrar.",16);
    }
    private void syncLabel(LinearLayout c,String type,String uuid) {
        boolean pending=repo.store.dirty(type,uuid);Ui.text(c,pending?"Pendiente de enviar":"Sincronizado",12);
        String error=repo.store.error(type,uuid);
        if(!error.isEmpty()) {Ui.text(c,error,13);Ui.button(c,"Revisar conflicto",v->resolve(type,uuid));}
    }
    private void establishmentDetails(Models.Establishment e) {
        Ui.message(this,"Agente: "+e.agente+"\nCódigo: "+e.codigo+"\nRegistro: "+e.registro+"\nRUC/DNI: "+e.ruc+"\nDirección: "+e.direccion+"\nUbicación: "+e.distrito+", "+e.provincia+", "+e.departamento+"\nTeléfono: "+e.telefono);
    }
    private void editEstablishment(Models.Establishment input) {
        Models.Establishment e=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(input),Models.Establishment.class);
        LinearLayout form=Ui.column(this);form.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,16));ScrollView sc=new ScrollView(this);sc.addView(form);
        List<RegistroHidrocarburos.Entry> registroData=RegistroHidrocarburos.all(this);
        List<String> codigos=new ArrayList<>(),rucs=new ArrayList<>(),registros=new ArrayList<>();
        for(RegistroHidrocarburos.Entry r:registroData){codigos.add(r.codigo);rucs.add(r.ruc);registros.add(r.registro);}

        EditText agente=Ui.field(form,"Agente / razón social *",e.agente,InputType.TYPE_CLASS_TEXT);
        AutoCompleteTextView codigo=Ui.autocomplete(form,"Código Osinergmin *",e.codigo,codigos);
        AutoCompleteTextView ruc=Ui.autocomplete(form,"RUC (11) o DNI (8) *",e.ruc,rucs);
        AutoCompleteTextView registro=Ui.autocomplete(form,"Registro de Hidrocarburos",e.registro,registros);
        EditText direccion=Ui.field(form,"Dirección *",e.direccion,InputType.TYPE_CLASS_TEXT);
        EditText distrito=Ui.field(form,"Distrito *",e.distrito,InputType.TYPE_CLASS_TEXT);
        EditText provincia=Ui.field(form,"Provincia *",e.provincia,InputType.TYPE_CLASS_TEXT);
        EditText departamento=Ui.field(form,"Departamento *",e.departamento,InputType.TYPE_CLASS_TEXT);
        EditText telefono=Ui.field(form,"Teléfono",e.telefono,InputType.TYPE_CLASS_PHONE);
        if(!registroData.isEmpty())Ui.infoBox(form,"Si conoce el código Osinergmin, el RUC o el N.° de registro, escríbalo o selecciónelo: el resto de los datos se completa solo, con la información oficial del Registro de Hidrocarburos.");

        boolean[] applying={false};
        Runnable lookup=()->{
            if(applying[0])return;
            RegistroHidrocarburos.Entry found=RegistroHidrocarburos.find(this,Ui.value(codigo));
            if(found==null)found=RegistroHidrocarburos.find(this,Ui.value(ruc));
            if(found==null)found=RegistroHidrocarburos.find(this,Ui.value(registro));
            if(found==null)return;
            applying[0]=true;
            codigo.setText(found.codigo);ruc.setText(found.ruc);registro.setText(found.registro);
            agente.setText(found.agente);direccion.setText(found.direccion);
            distrito.setText(found.distrito);provincia.setText(found.provincia);departamento.setText(found.departamento);
            applying[0]=false;
        };
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable s){lookup.run();}};
        codigo.addTextChangedListener(watcher);ruc.addTextChangedListener(watcher);registro.addTextChangedListener(watcher);

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Establecimiento").setView(sc).setPositiveButton("Guardar",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            e.agente=Ui.value(agente);e.codigo=Ui.value(codigo);e.ruc=Ui.value(ruc);e.registro=Ui.value(registro);e.direccion=Ui.value(direccion);e.distrito=Ui.value(distrito);e.provincia=Ui.value(provincia);e.departamento=Ui.value(departamento);e.telefono=Ui.value(telefono);
            String error=Validators.establishment(e);
            if(error!=null) {Ui.message(this,error);return;}
            for(Models.Establishment other:repo.store.list("establecimientos",Models.Establishment.class,false))if(!other.uuid.equals(e.uuid)&&other.codigo.equalsIgnoreCase(e.codigo)){Ui.message(this,"Ya existe ese código de establecimiento.");return;}
            repo.store.save("establecimientos",e,true);dialog.dismiss();show(mode);sync(false);
        }));dialog.show();dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.9));
    }
    private void chooseEstablishment() {
        List<Models.Establishment> items=repo.store.list("establecimientos",Models.Establishment.class,false);
        if(items.isEmpty()){Ui.message(this,"Registre primero un establecimiento.");show("establecimientos");return;}
        String[] names=new String[items.size()];for(int i=0;i<items.size();i++)names[i]=items.get(i).toString();
        new AlertDialog.Builder(this).setTitle("Seleccione el establecimiento").setItems(names,(d,i)->newFiscalization(items.get(i))).setNegativeButton("Cancelar",null).show();
    }
    private void newFiscalization(Models.Establishment e) {
        if(repo.store.catalogs("incumplimientos").size()!=6||repo.store.catalogs("productos").isEmpty()){Ui.message(this,"Sincronice una vez para descargar los catálogos de la API.");return;}
        Models.Fiscalization f=new Models.Fiscalization();f.establecimiento_uuid=e.uuid;f.establecimiento_id=e.id;f.usuario_id=repo.session.user().id;
        f.expediente="PRICE-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date());f.fecha=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).format(new Date());f.hora_apertura=new SimpleDateFormat("HH:mm",Locale.ROOT).format(new Date());
        f.datos.put("agente",e.agente);f.datos.put("codigo",e.codigo);f.datos.put("registro",e.registro);f.datos.put("ruc",e.ruc);f.datos.put("direccion",e.direccion);f.datos.put("distrito",e.distrito);f.datos.put("provincia",e.provincia);f.datos.put("departamento",e.departamento);f.datos.put("telefono",e.telefono);f.datos.put("dni_fiscal",repo.session.user().dni);f.datos.put("nombre_fiscal",repo.session.user().nombre);
        repo.store.save("fiscalizaciones",f,true);openFiscalization(f.uuid);
    }
    private void openFiscalization(String id){startActivity(new Intent(this,MainActivity.class).putExtra("uuid",id));}
    private void delete(String type,Models.Record record) {
        new AlertDialog.Builder(this).setTitle("Eliminar registro").setMessage("¿Desea eliminar este registro? Esta acción requiere confirmación.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,w)->{
            if("establecimientos".equals(type))for(Models.Fiscalization f:repo.store.list("fiscalizaciones",Models.Fiscalization.class,false))if(f.establecimiento_uuid.equals(record.uuid)){Ui.message(this,"Tiene fiscalizaciones asociadas.");return;}
            if(record.id==0){repo.store.remove(type,record.uuid);show(mode);return;}
            Repository.async(()->Repository.require(("establecimientos".equals(type)?repo.api.deleteEstablishment(record.uuid,String.valueOf(record.version)):repo.api.deleteFiscalization(record.uuid,String.valueOf(record.version))).execute()),(result,error)->{if(error!=null)Ui.message(this,error);else{repo.store.remove(type,record.uuid);show(mode);}});
        }).show();
    }
    private void reports() {
        List<Models.Fiscalization> all=repo.store.list("fiscalizaciones",Models.Fiscalization.class,false);
        Ui.text(body,"Resumen de fiscalizaciones",21);Ui.text(body,"Total: "+all.size()+"\nEstablecimientos: "+repo.store.list("establecimientos",Models.Establishment.class,false).size(),18);
        for(String s:new String[]{"BORRADOR","EN PROCESO","FINALIZADA","ACTA GENERADA"}){int n=0;for(Models.Fiscalization f:all)if(s.equals(f.estado))n++;Ui.text(body,s+": "+n,17);}
        int findings=0,photos=0;for(Models.Fiscalization f:all){findings+=f.incumplimientos.size();photos+=f.documentos.size();}
        Ui.text(body,"Incumplimientos registrados: "+findings+"\nFotografías adjuntas: "+photos,16);
        Ui.button(body,"Ver fiscalizaciones",v->{query="";show("fiscalizaciones");});
    }
    private void settings() {
        Ui.text(body,"Configuración",21);Ui.text(body,"Servidor: "+repo.session.url()+"\nRol: "+repo.session.user().rol+"\nPendientes: "+repo.store.pending(),16);
        Ui.button(body,"Comprobar conexión",v->Repository.async(()->Repository.require(repo.api.health().execute()),(r,e)->Ui.message(this,e==null?"API disponible. Base de datos: "+r.get("database"):e)));
        Ui.button(body,"Sincronizar",v->sync(true));
        Ui.button(body,"Cerrar sesión / cambiar servidor",v->new AlertDialog.Builder(this).setTitle("Cerrar sesión").setMessage("Los registros locales se conservan para esta cuenta. Pendientes: "+repo.store.pending()).setNegativeButton("Cancelar",null).setPositiveButton("Cerrar sesión",(d,w)->{Repository.async(()->repo.api.logout().execute(),(a,b)->{});repo.session.clear();startActivity(new Intent(this,LoginActivity.class));finish();}).show());
    }
    private void serverPdf(Models.Fiscalization f) {
        status.setText("Descargando el acta de la API…");
        Repository.async(()->{
            File file=new File(getCacheDir(),"ACTA_SERVIDOR_"+f.uuid+".pdf");
            try(okhttp3.ResponseBody body=Repository.require(repo.api.pdf(f.uuid).execute());InputStream in=body.byteStream();OutputStream out=new FileOutputStream(file)) {byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            Models.Fiscalization current=Repository.require(repo.api.fiscalization(f.uuid).execute());
            if(!repo.store.dirty("fiscalizaciones",f.uuid))repo.store.save("fiscalizaciones",current,false);
            return file;
        },(file,error)->{if(error!=null)Ui.message(this,error);else startActivity(new Intent(this,PdfPreviewActivity.class).putExtra("path",file.getAbsolutePath()).putExtra("share",true));});
    }
    private void history(Models.Fiscalization f){if(f.id==0){Ui.message(this,"El historial del servidor estará disponible después de sincronizar.");return;}Repository.async(()->Repository.require(repo.api.history(f.uuid).execute()),(r,e)->{if(e!=null){Ui.message(this,e);return;}StringBuilder out=new StringBuilder();for(Map<String,Object> i:r)out.append(i.get("fecha")).append(" · ").append(i.get("accion")).append(" · usuario ").append(i.get("usuario_id")).append('\n');Ui.message(this,out.length()==0?"Sin cambios registrados.":out.toString());});}
    private void resolve(String type,String uuid) {
        Ui.message(this,"Si el error indica datos incompletos, abra y corrija el registro. Para un conflicto de versión, puede consultar la copia del servidor y decidir si desea conservarla.");
        Repository.async(()->"establecimientos".equals(type)?Repository.require(repo.api.establishment(uuid).execute()):Repository.require(repo.api.fiscalization(uuid).execute()),(remote,error)->{
            if(error!=null){Ui.message(this,error);return;}
            String summary=remote instanceof Models.Establishment?((Models.Establishment)remote).agente:((Models.Fiscalization)remote).expediente+" · "+((Models.Fiscalization)remote).estado;
            new AlertDialog.Builder(this).setTitle("Copia del servidor").setMessage(summary+"\nVersión "+remote.version+"\n¿Reemplazar los cambios locales pendientes por esta copia?").setNegativeButton("Conservar mis cambios",null).setPositiveButton("Usar copia del servidor",(d,w)->{repo.store.save(type,remote,false);repo.store.clearRecovery(uuid);show(mode);}).show();
        });
    }
}
