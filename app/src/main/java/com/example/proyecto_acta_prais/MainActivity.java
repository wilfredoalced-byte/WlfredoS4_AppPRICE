package com.example.proyecto_acta_prais;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final int SAVE_PDF=501, PHOTO=502, LOCATION=503;
    private Repository repo;
    private Models.Fiscalization data;
    private final Map<String,EditText> fields=new LinkedHashMap<>();
    private final List<PriceInputs> priceInputs=new ArrayList<>();
    private final List<FindingInputs> findingInputs=new ArrayList<>();
    private EditText apertura,cierre;
    private TextView fecha,status,attachments,gps,signatureStatus;
    private RadioGroup telPub,telReg,horario;
    private Button dateButton,saveButton,finalizeButton;
    private File pdfFile;
    private boolean editable,loaded=false;
    private String selectedDate="";
    private LocationManager locationManager;
    private LocationListener locationListener;
    static class PriceInputs {int id,row;EditText brand;EditText[] values=new EditText[4];}
    static class FindingInputs {int id;CheckBox selected;EditText fact;}

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);repo=new Repository(this);
        if(!repo.session.valid()){startActivity(new Intent(this,LoginActivity.class));finish();return;}
        String id=getIntent().getStringExtra("uuid");
        data=id==null?null:repo.store.get("fiscalizaciones",id,Models.Fiscalization.class);
        if(data==null){Ui.message(this,"No se encontró la fiscalización.");finish();return;}
        editable=repo.session.writable()&&!data.closed()&&(repo.session.admin()||data.usuario_id==repo.session.user().id);
        Models.Fiscalization recovered=repo.store.recovery(id);
        if(editable&&recovered!=null){recovered.id=data.id;recovered.version=data.version;data=recovered;}
        setContentView(R.layout.activity_main);Ui.insets(findViewById(R.id.main));
        bind();populate();prices();findings();extras();loaded=true;
        if(!editable)disableForm();
        findViewById(R.id.btnVistaPrevia).setOnClickListener(v->preview());
        findViewById(R.id.btnGenerarPdf).setOnClickListener(v->{if(!data.closed())finalizeAct(true);else export();});
    }
    private void bind(){
        int[] ids={R.id.etExpediente,R.id.etAgente,R.id.etCodigoOsi,R.id.etRegistroHidro,R.id.etDireccion,R.id.etDistrito,R.id.etProvincia,R.id.etDepartamento,R.id.etRuc,R.id.etTelefono,R.id.etDniFiscal,R.id.etNombreFiscal,R.id.etOtras,R.id.etDocumentos,R.id.etObservaciones,R.id.etNegativa,R.id.etDniRecibe,R.id.etNombreRecibe,R.id.etRelacionRecibe};
        String[] keys={"expediente","agente","codigo","registro","direccion","distrito","provincia","departamento","ruc","telefono","dni_fiscal","nombre_fiscal","otras","documentacion","observaciones","negativa","dni_recibe","nombre_recibe","relacion_recibe"};
        int[] max={28,120,35,35,150,45,45,35,11,30,8,70,300,300,300,300,8,70,45};
        for(int i=0;i<ids.length;i++){EditText e=findViewById(ids[i]);fields.put(keys[i],e);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max[i])});}
        apertura=findViewById(R.id.etHoraApertura);cierre=findViewById(R.id.etHoraCierre);fecha=findViewById(R.id.tvFecha);dateButton=findViewById(R.id.btnFecha);
        telPub=findViewById(R.id.rgTelPub);telReg=findViewById(R.id.rgTelReg);horario=findViewById(R.id.rgHorario);
        dateButton.setOnClickListener(v->{Calendar c=Calendar.getInstance();new DatePickerDialog(this,(picker,y,m,d)->{selectedDate=String.format(Locale.ROOT,"%02d/%02d/%04d",d,m+1,y);fecha.setText("Fecha: "+selectedDate);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});
        for(EditText e:new EditText[]{apertura,cierre}){e.setFocusable(false);e.setOnClickListener(v->{Calendar c=Calendar.getInstance();new TimePickerDialog(this,(picker,h,m)->e.setText(String.format(Locale.ROOT,"%02d:%02d",h,m)),c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();});}
        if(!repo.session.admin()){fields.get("dni_fiscal").setEnabled(false);fields.get("nombre_fiscal").setEnabled(false);}
    }
    private void populate(){
        fields.get("expediente").setText(data.expediente);
        for(String key:fields.keySet())if(!"expediente".equals(key))fields.get(key).setText(data.datos.getOrDefault(key,""));
        selectedDate=data.fecha;fecha.setText("Fecha: "+selectedDate);apertura.setText(data.hora_apertura);cierre.setText(data.hora_cierre);
        check(telPub,data.verificaciones.get("telefono_publicado"),R.id.telPubSi,R.id.telPubNo);
        check(telReg,data.verificaciones.get("telefono_actualizado"),R.id.telRegSi,R.id.telRegNo);
        check(horario,data.verificaciones.get("horario_publicado"),R.id.horarioSi,R.id.horarioNo);
    }
    private void check(RadioGroup group,Boolean v,int yes,int no){if(v!=null)group.check(v?yes:no);}
    private Boolean answer(RadioGroup g,int yes){return g.getCheckedRadioButtonId()==-1?null:g.getCheckedRadioButtonId()==yes;}
    private void prices(){
        LinearLayout container=findViewById(R.id.priceContainer);Ui.text(container,"III. PRECIOS REGISTRADOS",18);Ui.text(container,"Abra cada producto que comercializa el establecimiento. Use punto o coma decimal. Los campos vacíos indican que no se registró un precio.",13);
        List<Models.Catalog> products=repo.store.catalogs("productos");
        for(Models.Catalog product:products)if(product.id<=6)addPrice(container,product,0,null);
        for(int row=0;row<3;row++){
            int rowNumber=row;
            LinearLayout brandGroup=Ui.column(this);
            Button toggle=Ui.button(container,"GLP en cilindros · marca "+(row+1),v->brandGroup.setVisibility(brandGroup.getVisibility()==View.GONE?View.VISIBLE:View.GONE));
            container.addView(brandGroup);brandGroup.setVisibility(View.GONE);
            String brand="";for(Models.Price p:data.precios)if(p.producto_id>=7&&p.fila==row){brand=p.marca;break;}
            EditText brandInput=Ui.field(brandGroup,"Marca / color del cilindro",brand,InputType.TYPE_CLASS_TEXT);brandInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(18)});
            for(Models.Catalog product:products)if(product.id>=7)addPrice(brandGroup,product,rowNumber,brandInput);
        }
    }
    private void addPrice(LinearLayout parent,Models.Catalog product,int row,EditText brand){
        LinearLayout inputs=Ui.column(this);Ui.button(parent,product.nombre,v->inputs.setVisibility(inputs.getVisibility()==View.GONE?View.VISIBLE:View.GONE));parent.addView(inputs);inputs.setVisibility(View.GONE);
        PriceInputs p=new PriceInputs();p.id=product.id;p.row=row;p.brand=brand;
        Models.Price existing=null;for(Models.Price item:data.precios)if(item.producto_id==p.id&&item.fila==row){existing=item;break;}
        String[] values=existing==null?new String[]{"","","",""}:new String[]{existing.price,existing.publicado,existing.surtidor,existing.descuento};
        String[] labels={"Registrado en PRICE (S/)","Publicado (S/)","Surtidor / dispensador (S/)","Con descuento (S/)"};
        for(int i=0;i<4;i++){p.values[i]=Ui.field(inputs,labels[i],values[i],InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);p.values[i].setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});}
        priceInputs.add(p);
    }
    private void findings(){
        LinearLayout box=findViewById(R.id.factsContainer);Ui.text(box,"V. INCUMPLIMIENTOS Y HECHOS",18);Ui.text(box,"Seleccione los incumplimientos observados y describa lo que verificó. Si no existen, deje las casillas sin seleccionar.",13);
        List<Models.Catalog> catalog=repo.store.catalogs("incumplimientos");
        if(catalog.isEmpty())Ui.text(box,"Sincronice desde el menú para descargar el catálogo.",14);
        for(Models.Catalog item:catalog){
            FindingInputs input=new FindingInputs();input.id=item.id;input.selected=new CheckBox(this);input.selected.setText(item.id+". "+item.descripcion);Ui.tint(input.selected);box.addView(input.selected);
            Ui.text(box,"Base legal: "+item.base_legal,12);
            input.fact=Ui.field(box,"Hechos verificados del ítem "+item.id,"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.fact.setSingleLine(false);input.fact.setMinLines(2);input.fact.setGravity(android.view.Gravity.TOP);
            int[] limits={650,650,350,300,350,450};input.fact.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limits[item.id-1])});
            for(Models.Finding f:data.incumplimientos)if(f.incumplimiento_id==item.id){input.selected.setChecked(true);input.fact.setText(f.hecho);}
            input.fact.setVisibility(input.selected.isChecked()?View.VISIBLE:View.GONE);
            input.selected.setOnCheckedChangeListener((b,checked)->input.fact.setVisibility(checked?View.VISIBLE:View.GONE));findingInputs.add(input);
        }
    }
    private void extras(){
        LinearLayout box=findViewById(R.id.extraContainer);status=Ui.text(box,"Estado: "+data.estado,16);
        signatureStatus=Ui.text(box,"",14);updateSignatureStatus();
        if(editable){Ui.button(box,"Firmar como fiscalizador",v->sign("fiscalizador"));Ui.button(box,"Firma de quien recibe",v->sign("recibe"));}
        Ui.text(box,"Fotografías de la diligencia",17);attachments=Ui.text(box,"",14);updateAttachments();
        if(editable){Ui.button(box,"Adjuntar fotografía",v->{if(data.documentos.size()>=4){Ui.message(this,"Puede adjuntar hasta 4 fotografías.");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PHOTO);});Ui.button(box,"Quitar fotografía",v->removePhoto());}
        else Ui.button(box,"Ver fotografías",v->viewPhotos());
        gps=Ui.text(box,"",14);updateGps();if(editable)Ui.button(box,"Registrar ubicación GPS",v->requestGps());
        if(editable){saveButton=Ui.button(box,"Guardar avance",v->save());finalizeButton=Ui.button(box,"Finalizar fiscalización",v->finalizeAct(false));}
        Ui.button(box,"Volver a fiscalizaciones",v->finish());
    }
    private void disableForm(){
        for(EditText e:fields.values())e.setEnabled(false);apertura.setEnabled(false);cierre.setEnabled(false);dateButton.setEnabled(false);
        disable(telPub);disable(telReg);disable(horario);
        for(PriceInputs p:priceInputs){for(EditText e:p.values)e.setEnabled(false);if(p.brand!=null)p.brand.setEnabled(false);}
        for(FindingInputs f:findingInputs){f.selected.setEnabled(false);f.fact.setEnabled(false);}
        if(saveButton!=null)saveButton.setEnabled(false);if(finalizeButton!=null)finalizeButton.setEnabled(false);
        LinearLayout extras=findViewById(R.id.extraContainer);
        for(int i=0;i<extras.getChildCount();i++){View child=extras.getChildAt(i);if(child instanceof Button){String label=((Button)child).getText().toString();if(!label.equals("Volver a fiscalizaciones")&&!label.equals("Ver fotografías"))child.setEnabled(false);}}
    }
    private void disable(ViewGroup group){for(int i=0;i<group.getChildCount();i++)group.getChildAt(i).setEnabled(false);}
    private Models.Fiscalization collect(){
        if(!editable)return data;
        data.expediente=Ui.value(fields.get("expediente"));data.fecha=selectedDate;data.hora_apertura=Ui.value(apertura);data.hora_cierre=Ui.value(cierre);
        for(String key:fields.keySet())if(!"expediente".equals(key))data.datos.put(key,Ui.value(fields.get(key)));
        data.verificaciones.put("telefono_publicado",answer(telPub,R.id.telPubSi));data.verificaciones.put("telefono_actualizado",answer(telReg,R.id.telRegSi));data.verificaciones.put("horario_publicado",answer(horario,R.id.horarioSi));
        data.precios.clear();
        for(PriceInputs input:priceInputs){Models.Price p=new Models.Price(input.id);p.fila=input.row;p.marca=input.brand==null?"":Ui.value(input.brand);p.price=Ui.value(input.values[0]).replace(',','.');p.publicado=Ui.value(input.values[1]).replace(',','.');p.surtidor=Ui.value(input.values[2]).replace(',','.');p.descuento=Ui.value(input.values[3]).replace(',','.');if(!(p.price+p.publicado+p.surtidor+p.descuento).isEmpty())data.precios.add(p);}
        List<Models.Finding> previous=new ArrayList<>(data.incumplimientos);data.incumplimientos.clear();
        for(FindingInputs input:findingInputs)if(input.selected.isChecked()) {Models.Finding f=new Models.Finding();f.incumplimiento_id=input.id;f.hecho=Ui.value(input.fact);f.usuario_id=repo.session.user().id;f.fecha=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.ROOT).format(new Date());for(Models.Finding old:previous)if(old.incumplimiento_id==f.incumplimiento_id&&old.hecho.equals(f.hecho)){f.fecha=old.fecha;f.usuario_id=old.usuario_id;}data.incumplimientos.add(f);}
        return data;
    }
    private boolean valid(boolean full){
        collect();if(!repo.session.valid()){repo.store.recovery(data);Ui.message(this,"La sesión venció. Su avance está guardado. Vuelva al menú e inicie sesión.");return false;}String error=Validators.fiscalization(data,full);
        if(error==null&&full)try{new ActaRenderer(this).validateFit(data);}catch(Exception e){error=e.getMessage();}
        if(error!=null){Ui.message(this,error);return false;}return true;
    }
    private void persist(){
        Models.Fiscalization latest=repo.store.get("fiscalizaciones",data.uuid,Models.Fiscalization.class);if(latest!=null){data.id=latest.id;data.version=latest.version;}
        repo.store.save("fiscalizaciones",data,true);repo.store.clearRecovery(data.uuid);status.setText("Estado: "+data.estado+" · guardado en el teléfono");
    }
    private void sync(){Repository.async(repo::sync,(result,error)->{if(isFinishing())return;status.setText(error==null?result:error);Models.Fiscalization latest=repo.store.get("fiscalizaciones",data.uuid,Models.Fiscalization.class);if(latest!=null){data.id=latest.id;data.version=latest.version;}});}
    private void save(){if(!editable||!valid(false))return;for(Models.Fiscalization f:repo.store.list("fiscalizaciones",Models.Fiscalization.class,false))if(!f.uuid.equals(data.uuid)&&f.expediente.equalsIgnoreCase(data.expediente)){Ui.message(this,"Ya existe ese expediente.");return;}data.estado="EN PROCESO";persist();Toast.makeText(this,"Guardado correctamente",Toast.LENGTH_SHORT).show();sync();}
    private void finalizeAct(boolean afterExport){
        if(!editable){Ui.message(this,"Su cuenta solo puede consultar esta fiscalización.");return;}if(!valid(true))return;
        new AlertDialog.Builder(this).setTitle("Finalizar fiscalización").setMessage("Revise los datos antes de continuar. Al finalizar, el acta quedará cerrada para edición.").setNegativeButton("Seguir revisando",null).setPositiveButton("Finalizar",(d,w)->{data.estado="FINALIZADA";persist();editable=false;disableForm();sync();if(afterExport)export();}).show();
    }
    private boolean createPdf(){
        if(!valid(true))return false;
        try{pdfFile=new File(getCacheDir(),"ACTA_PRICE_"+data.expediente.replaceAll("[^a-zA-Z0-9_-]","_")+".pdf");try(OutputStream out=new FileOutputStream(pdfFile)){new ActaRenderer(this).write(data,out);}return true;}
        catch(Exception e){Ui.message(this,"No se pudo generar el PDF: "+e.getMessage());return false;}
    }
    private void preview(){if(!createPdf())return;startActivity(new Intent(this,PdfPreviewActivity.class).putExtra("path",pdfFile.getAbsolutePath()).putExtra("share",data.closed()));}
    private void export(){if(!createPdf())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf").putExtra(Intent.EXTRA_TITLE,pdfFile.getName());startActivityForResult(i,SAVE_PDF);}
    private void sign(String key){
        if(!editable)return;LinearLayout box=Ui.column(this);Ui.text(box,"Dibuje su firma con el dedo.",16);SignatureView canvas=new SignatureView(this);box.addView(canvas,new LinearLayout.LayoutParams(-1,Ui.dp(this,220)));Ui.button(box,"Limpiar firma",v->canvas.clear());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("fiscalizador".equals(key)?"Firma del fiscalizador":"Firma de quien recibe").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar firma",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String png=canvas.png();if(png.isEmpty()){Ui.message(this,"Dibuje la firma antes de guardarla.");return;}data.firmas.put(key,png);updateSignatureStatus();dialog.dismiss();}));dialog.show();
    }
    private void updateSignatureStatus(){signatureStatus.setText("Fiscalizador: "+(data.firmas.getOrDefault("fiscalizador","").isEmpty()?"firma pendiente":"firma registrada")+"\nQuien recibe: "+(data.firmas.getOrDefault("recibe","").isEmpty()?"firma pendiente":"firma registrada"));}
    private void updateAttachments(){StringBuilder s=new StringBuilder();for(int i=0;i<data.documentos.size();i++)s.append(i+1).append(". ").append(data.documentos.get(i).nombre).append('\n');attachments.setText(s.length()==0?"Sin fotografías adjuntas.":s.toString());}
    private void removePhoto(){if(data.documentos.isEmpty()){Ui.message(this,"No hay fotografías.");return;}String[] names=new String[data.documentos.size()];for(int i=0;i<names.length;i++)names[i]=data.documentos.get(i).nombre;new AlertDialog.Builder(this).setTitle("Seleccione la fotografía que desea quitar").setItems(names,(d,i)->new AlertDialog.Builder(this).setMessage("¿Quitar esta fotografía?").setNegativeButton("Cancelar",null).setPositiveButton("Quitar",(a,b)->{data.documentos.remove(i);updateAttachments();}).show()).show();}
    private void viewPhotos(){if(data.documentos.isEmpty()){Ui.message(this,"No hay fotografías.");return;}String[] names=new String[data.documentos.size()];for(int i=0;i<names.length;i++)names[i]=data.documentos.get(i).nombre;new AlertDialog.Builder(this).setItems(names,(d,i)->{byte[] b=Base64.decode(data.documentos.get(i).contenido,Base64.DEFAULT);ImageView im=new ImageView(this);im.setAdjustViewBounds(true);im.setImageBitmap(BitmapFactory.decodeByteArray(b,0,b.length));new AlertDialog.Builder(this).setTitle(names[i]).setView(im).setPositiveButton("Cerrar",null).show();}).show();}
    private void updateGps(){gps.setText(data.latitud==null?"Ubicación GPS pendiente.":"Latitud: "+data.latitud+"\nLongitud: "+data.longitud);}
    private void requestGps(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION);return;}locate();
    }
    private void locate(){
        try{
            locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
            String provider=locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
            if(!locationManager.isProviderEnabled(provider)){Ui.message(this,"Active la ubicación del dispositivo e intente otra vez.");return;}
            gps.setText("Obteniendo ubicación…");locationListener=new LocationListener(){public void onLocationChanged(Location l){data.latitud=l.getLatitude();data.longitud=l.getLongitude();updateGps();stopLocation();}public void onStatusChanged(String p,int s,Bundle b){}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}};
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)locationManager.requestLocationUpdates(provider,1000,0,locationListener);
            gps.postDelayed(()->{if(locationListener!=null){stopLocation();gps.setText("No se recibió ubicación. Intente en un lugar con mejor señal.");}},25000);
        }catch(Exception e){Ui.message(this,"No se pudo obtener la ubicación: "+e.getMessage());}
    }
    private void stopLocation(){if(locationManager!=null&&locationListener!=null){locationManager.removeUpdates(locationListener);locationListener=null;}}
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){super.onRequestPermissionsResult(req,permissions,results);if(req==LOCATION){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)locate();else Ui.message(this,"Permiso de ubicación no concedido. Puede continuar sin GPS.");}}
    @Override protected void onPause(){if(loaded&&editable)repo.store.recovery(collect());stopLocation();super.onPause();}
    @Override protected void onActivityResult(int req,int result,Intent intent){
        super.onActivityResult(req,result,intent);if(result!=RESULT_OK||intent==null||intent.getData()==null)return;Uri uri=intent.getData();
        if(req==SAVE_PDF){
            if(pdfFile==null||!pdfFile.exists()){Ui.message(this,"Vuelva a pulsar Generar PDF para crear el archivo.");return;}
            try(InputStream in=new FileInputStream(pdfFile);OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null)throw new IOException("No se pudo abrir el destino.");byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                if(repo.session.writable()&&(repo.session.admin()||data.usuario_id==repo.session.user().id)){data.estado="ACTA GENERADA";persist();sync();}
                new AlertDialog.Builder(this).setTitle("PDF guardado correctamente").setMessage("El Acta PRICE contiene dos páginas.").setPositiveButton("Ver y compartir",(d,w)->startActivity(new Intent(this,PdfPreviewActivity.class).putExtra("path",pdfFile.getAbsolutePath()).putExtra("share",true))).setNegativeButton("Cerrar",null).show();
            }catch(Exception e){Ui.message(this,"No se pudo guardar: "+e.getMessage());}
        }else if(req==PHOTO){
            Repository.async(()->{
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
                options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1200)options.inSampleSize*=2;options.inJustDecodeBounds=false;
                Bitmap bitmap;try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}if(bitmap==null)throw new Exception("Seleccione una imagen válida.");
                ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,75,out);bitmap.recycle();
                Models.Attachment doc=new Models.Attachment();doc.nombre="FOTO_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+".jpg";doc.contenido=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);if(doc.contenido.length()>1800000)throw new Exception("La fotografía es demasiado grande.");return doc;
            },(doc,error)->{if(isFinishing())return;if(error!=null)Ui.message(this,error);else if(editable&&data.documentos.size()<4){data.documentos.add(doc);updateAttachments();repo.store.recovery(collect());}});
        }
    }
}
