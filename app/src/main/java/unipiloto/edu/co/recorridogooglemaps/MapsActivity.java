package unipiloto.edu.co.recorridogooglemaps;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private static final int REQUEST_LOCATION_PERMS = 1001;
    private GoogleMap mMap;
    private List<LatLng> puntos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Ejemplo de puntos representativos (puedes recibirlos vía Intent)
        puntos.add(new LatLng(4.710989, -74.072090)); // Bogotá
        puntos.add(new LatLng(4.711500, -74.070000));
        puntos.add(new LatLng(4.712000, -74.075000));

        Button btn = findViewById(R.id.btnOpenExternal);
        btn.setOnClickListener(v -> {
            if (!puntos.isEmpty()) {
                LatLng origen = puntos.get(0);
                LatLng destino = puntos.get(puntos.size() - 1);
                List<LatLng> intermedios = puntos.subList(1, puntos.size() - 1);
                abrirEnGoogleMaps(origen, destino, intermedios);
            }
        });

        // Verificar permisos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION_PERMS);

        } else {
            inicializarMapa();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMS) {
            boolean ok = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) { ok = false; break; }
            }
            if (ok) {
                inicializarMapa();
            } else {
                Toast.makeText(this, "Permiso de ubicación requerido", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void inicializarMapa() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Agregar marcadores
        for (LatLng p : puntos) {
            mMap.addMarker(new MarkerOptions().position(p));
        }
        if (!puntos.isEmpty()) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(puntos.get(0), 14f));
            LatLng origen = puntos.get(0);
            LatLng destino = puntos.get(puntos.size() - 1);
            List<LatLng> waypoints = puntos.subList(1, puntos.size() - 1);
            obtenerRutasConAlternativas(origen, destino, waypoints);
        }
    }

    private void obtenerRutasConAlternativas(LatLng origen, LatLng destino, List<LatLng> waypoints) {
        String origin  = origen.latitude + "," + origen.longitude;
        String dest    = destino.latitude + "," + destino.longitude;

        StringBuilder wp = new StringBuilder();
        for (LatLng p : waypoints) {
            if (wp.length() > 0) wp.append("|");
            wp.append(p.latitude).append(",").append(p.longitude);
        }

        String url = "https://maps.googleapis.com/maps/api/directions/json"
                + "?origin="       + origin
                + "&destination="  + dest
                + (wp.length()>0 ? "&waypoints=" + wp : "")
                + "&alternatives=true"
                + "&key=TU_API_KEY_GOOGLE_MAPS";

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                this::dibujarRutas,
                error -> Log.e("MAPS", "Error Directions API", error)
        );
        Volley.newRequestQueue(this).add(req);
    }

    private void dibujarRutas(JSONObject json) {
        try {
            JSONArray routes = json.getJSONArray("routes");
            for (int i = 0; i < routes.length(); i++) {
                String poly = routes.getJSONObject(i)
                        .getJSONObject("overview_polyline")
                        .getString("points");
                List<LatLng> coords = PolyUtil.decode(poly);
                mMap.addPolyline(new PolylineOptions()
                        .addAll(coords)
                        .width(8)
                        .color(i==0 ? 0xFF2196F3 : 0x802196F3)
                );
            }
        } catch (JSONException e) {
            Log.e("MAPS", "Parse Directions error", e);
        }
    }

    private void abrirEnGoogleMaps(LatLng origen, LatLng destino, List<LatLng> puntosIntermedios) {
        String uri = "https://www.google.com/maps/dir/?api=1"
                + "&origin="      + origen.latitude + "," + origen.longitude
                + "&destination=" + destino.latitude + "," + destino.longitude;

        if (!puntosIntermedios.isEmpty()) {
            StringBuilder wp = new StringBuilder();
            for (LatLng p : puntosIntermedios) {
                if (wp.length()>0) wp.append("|");
                wp.append(p.latitude).append(",").append(p.longitude);
            }
            uri += "&waypoints=" + wp;
        }
        uri += "&travelmode=walking";

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        startActivity(intent);
    }
}