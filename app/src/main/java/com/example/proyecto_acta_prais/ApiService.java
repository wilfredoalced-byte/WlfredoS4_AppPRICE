package com.example.proyecto_acta_prais;

import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {
    @POST("api/login") Call<Models.LoginResponse> login(@Body Models.LoginRequest request);
    @GET("api/me") Call<Models.User> me();
    @POST("api/logout") Call<Map<String,String>> logout();
    @GET("api/health") Call<Map<String,String>> health();
    @GET("api/establecimientos") Call<List<Models.Establishment>> establishments(@Query("q") String query);
    @GET("api/establecimientos/{id}") Call<Models.Establishment> establishment(@Path("id") String id);
    @POST("api/establecimientos") Call<Models.Establishment> createEstablishment(@Body Models.Establishment data);
    @PUT("api/establecimientos/{id}") Call<Models.Establishment> updateEstablishment(@Path("id") String id,@Body Models.Establishment data);
    @DELETE("api/establecimientos/{id}") Call<Map<String,String>> deleteEstablishment(@Path("id") String id,@Header("If-Match") String version);
    @GET("api/fiscalizaciones") Call<List<Models.Fiscalization>> fiscalizations(@Query("q") String query);
    @GET("api/fiscalizaciones/{id}") Call<Models.Fiscalization> fiscalization(@Path("id") String id);
    @POST("api/fiscalizaciones") Call<Models.Fiscalization> createFiscalization(@Body Models.Fiscalization data);
    @PUT("api/fiscalizaciones/{id}") Call<Models.Fiscalization> updateFiscalization(@Path("id") String id,@Body Models.Fiscalization data);
    @DELETE("api/fiscalizaciones/{id}") Call<Map<String,String>> deleteFiscalization(@Path("id") String id,@Header("If-Match") String version);
    @GET("api/incumplimientos") Call<List<Models.Catalog>> findings();
    @GET("api/productos") Call<List<Models.Catalog>> products();
    @POST("api/fiscalizaciones/{id}/incumplimientos") Call<Models.Fiscalization> addFinding(@Path("id") String id,@Body Models.Finding item);
    @GET("api/fiscalizaciones/{id}/historial") Call<List<Map<String,Object>>> history(@Path("id") String id);
    @Streaming @GET("api/fiscalizaciones/{id}/acta/pdf") Call<ResponseBody> pdf(@Path("id") String id);
}
