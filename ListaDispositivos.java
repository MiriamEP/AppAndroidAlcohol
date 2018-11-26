package com.example.mirian.app_v4;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Set;

/**
 * Created by Mirian on 27/04/2017.
 */

public class ListaDispositivos extends Activity {

    public static String DIRECCION_DISPOSITIVO = "direccionMAC_disp"; //para el intent de la Activity principal

    private BluetoothAdapter AdaptadorBT_aux;
    private ArrayAdapter<String> ArrayDispEmparejados;
    private ArrayAdapter<String> ArrayDispEncontrados;

    @Override
    protected void onCreate(Bundle saveInstanceState){
        super.onCreate(saveInstanceState);
        setContentView(R.layout.activity_lista_disp);

        //En caso de que el usuario salga, cancelamos la actividad
        setResult(Activity.RESULT_CANCELED);
        //Se inicializa el botón que buscará dispositivos
        Button botonBuscar = (Button)findViewById(R.id.boton_escanear);
        botonBuscar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hacerDescubrimiento();
                v.setVisibility(View.GONE);
            }
        });

        //Inicializamos arrays. Uno para los dispositivps emparejados y otros para los nuevos dispositivos encontrados
        ArrayDispEmparejados = new ArrayAdapter<String>(this,R.layout.nombre_disp);
        ArrayDispEncontrados = new ArrayAdapter<String>(this,R.layout.nombre_disp);

        //Lanzamos ListView para dispositivos emparejados
        ListView viewDispEmparejados = (ListView) findViewById(R.id.disp_emparejados);
        viewDispEmparejados.setAdapter(ArrayDispEmparejados);
        viewDispEmparejados.setOnItemClickListener(ClickListenerDisp);
        //Lanzamos ListView para dispositivos nuevos
        ListView viewDispNuevos = (ListView) findViewById(R.id.disp_nuevos);
        viewDispNuevos.setAdapter(ArrayDispEncontrados);
        viewDispNuevos.setOnItemClickListener(ClickListenerDisp);
        //Registro para el broadcast cuando el dispositivo es descubierto
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(receptor, filter);
        //Registro del broadcast cuando el descubrimiento ha acabado
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(receptor, filter);
        //Obtener el adaptador BT local
        AdaptadorBT_aux = BluetoothAdapter.getDefaultAdapter();
        //obtener dispositivo emparejado
        Set<BluetoothDevice> dispEmparejados = AdaptadorBT_aux.getBondedDevices();
        //Si hay dispositivo emparejados, añadir cada uno al array
        if(dispEmparejados.size()>0){
            findViewById(R.id.titulo_disp_emparejados).setVisibility(View.VISIBLE);
            for(BluetoothDevice device : dispEmparejados){
                ArrayDispEmparejados.add(device.getName() + "\n" + device.getAddress());
            }
        }else {
            String noDisp = getResources().getText(R.string.noHayEmparejados).toString();
            ArrayDispEmparejados.add(noDisp);
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        //cancelamos descubrimiento
        if (AdaptadorBT_aux != null){
            AdaptadorBT_aux.cancelDiscovery();
        }
        //eliminamos los register
        this.unregisterReceiver(receptor);
    }

    private void hacerDescubrimiento(){

        //se hace visible el subtítulo de nuevos dispositivos
        findViewById(R.id.titulo_disp_nuevos).setVisibility(View.VISIBLE);
        //Se detiene cualquier proceso de descubrimiento activo
        if(AdaptadorBT_aux.isDiscovering()){
            AdaptadorBT_aux.cancelDiscovery();
        }
        //inicia el proceso de detección
        if(AdaptadorBT_aux.isEnabled()){
            AdaptadorBT_aux.startDiscovery();
        }
    }

    //creamos el clickListener para todos los dispositivos de las ListViews
    private AdapterView.OnItemClickListener ClickListenerDisp = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            //Cancelamos el descubrimiento porque consume recursos
            AdaptadorBT_aux.cancelDiscovery();
            //Obtenemos la dirección MAC, que son los últimos 17 caracteres
            String extraerMAC = ((TextView) view).getText().toString();
            String direccionMAC = extraerMAC.substring(extraerMAC.length()-17);
            //creamos un intent y le añadimos la direccion MAC y se lo enviamos a la actividad principal
            Intent intentAux = new Intent();
            intentAux.putExtra(DIRECCION_DISPOSITIVO,direccionMAC);
            //El resultado OK y terminamos
            setResult(Activity.RESULT_OK, intentAux);
            finish();
        }
    };

    // BroadcastReceiver esta atento a los dispositivos descubiertos y coloca la lista de nombres y direcciones en la lista
    // de la interfaz
    private final BroadcastReceiver receptor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String accion = intent.getAction();
            //Cuando se ha descubierto un dispositivo
            if(BluetoothDevice.ACTION_FOUND.equals(accion)){
                //Se obtiene el objeto BluetoothDevice del Intent
                BluetoothDevice disp = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //Si está emparejado, se omite, ya ha sido añadido
                if (disp.getBondState() != BluetoothDevice.BOND_BONDED){
                    ArrayDispEncontrados.add(disp.getName() + "\n" + disp.getAddress());
                }else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(accion)){
                    setTitle(R.string.seleccionaDisp); //cuando la acción ha terminado sin haber encontrado un dispositivo al que conectar, nos avisa de que no hay disp disponibles
                    if(ArrayDispEmparejados.getCount() == 0){
                        String SinDisp = getResources().getText(R.string.NoEcontrado).toString();
                        ArrayDispEncontrados.add(SinDisp);
                    }
                }
            }
        }
    };
}
