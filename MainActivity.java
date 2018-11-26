package com.example.mirian.app_v4;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.SimpleDateFormat;


public class MainActivity extends AppCompatActivity {

    //Para Depurar
    private static final String TAG = "MainActivity";
    private static final boolean D = true;
    //Adaptador local BT
    private BluetoothAdapter AdaptadorBT = null;
    //Objeto para ServicioBT
    private ServicioBT servicioBT = null;
    //Mensajes enviados desde el handler ServicioBT
    public static final int CAMBIO_ESTADO =1;
    public static final int LEER = 2;
    //public static final int ESCRIBIR = 3; No nos hace falta. No hacemos escritura.
    public static final int NOMBRE_DISPOSITIVO = 4;
    public static final int MENSAJE_TOAST = 5;
    //mensaje clave  para los handle del ServicioBT
    public static final String DEVICE_NAME= "nombre_disp";
    public static final String TOAST = "toast";
    //Declaraciones Intent
    private static final int SOLICITAR_BT = 1;
    private static final int DEVUELVE_DISPOSITIVO = 2;

    private TextView temperatura; //uso la variable temperatura porque hice pruebas con el sensor de temperatura del arduino y no lo cambie
    private TextView fechaHora;
    //nombre del dispositivo conectado
    private String NombreDispConectado = null;
    //formato hora que se muestra por la pantalla
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    //------------------------GRÁFICA--------------------------
    private LineGraphSeries<DataPoint> datos;
    private int lastX = 0;
    private final Handler handlerTimer = new Handler();
    private Runnable mTimer1;
    //---------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) { //inicializamos los componentes principales de la actividad
        super.onCreate(savedInstanceState); //guardamos el estado de la actividad
        if(D) Log.e(TAG, "+++ ON CREATE +++");
        setContentView(R.layout.activity_main); //define el diseño de la interfaz del usuario
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar); //barras de actividad de la app

        temperatura = (TextView) findViewById(R.id.Temperatura);
        fechaHora = (TextView) findViewById(R.id.fechaHora);
        //-------------------------GRÁFICA------------------------
        //Instanciamos la gráfica
        GraphView grafica = (GraphView)findViewById(R.id.graph);
        //Añadimos el nombre de los ejes de la gráfica y tamaño de la letra
        grafica.getGridLabelRenderer().setVerticalAxisTitle("Corriente (nA)");
        grafica.getGridLabelRenderer().setHorizontalAxisTitle("Segundos");
        grafica.getGridLabelRenderer().setHorizontalAxisTitleTextSize(25);
        grafica.getGridLabelRenderer().setVerticalAxisTitleTextSize(25);
        //Dato recibido
        datos = new LineGraphSeries<DataPoint>();
        grafica.addSeries(datos);
        //Ajustes de la gráfica:
        Viewport viewport = grafica.getViewport();
        viewport.setXAxisBoundsManual(true);//eje X
        viewport.setMinX(0);
        viewport.setMaxX(5000);
        viewport.setYAxisBoundsManual(true); //eje Y
        viewport.setMinY(-1000);
        viewport.setMaxY(0);
        viewport.setScalable(true);//que sea escalable: es decir aumentar o reducir
        datos.setThickness(2); //grosor de la curva
        //---------------------------------------------------------
        //Obtenemos el adaptador local BT. Punto de entrada de toda interacción de BT -> ver otros dispositivos; consultar lista dispositivos sincronizados...
        AdaptadorBT = BluetoothAdapter.getDefaultAdapter();
        //En caso de null, terminamos la actividad
        if(AdaptadorBT == null){
            Toast.makeText(this, "El dispositivo BT no es compatible", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Opciones de menú de la app: Autodescibrimiento, escaneo y salir -> XML menu_main
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //Las tres opciones que presenta nuestro menú desplegable
        switch (item.getItemId()){
            case R.id.Descubrir:
                hacerDetectable(); //vamos al método que hace que el dispositivo sea detectable
                return true;
            case R.id.Escanear:
                Toast.makeText(this, "Congrats funciona el desplegable", Toast.LENGTH_SHORT).show();
                Intent dispIntent = new Intent(this, ListaDispositivos.class); //lanzamos la actividad Lista de dispositivos
                startActivityForResult(dispIntent, DEVUELVE_DISPOSITIVO);
                return true;
            case R.id.Apagar:
                AdaptadorBT.disable();
                finish(); //salimos de la app
                System.exit(0);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void hacerDetectable(){
        //método para hacer dispositivo detectable durante 1 min
        if(AdaptadorBT.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE){
            Intent hacerDetectableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            hacerDetectableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
            startActivity(hacerDetectableIntent);
        }
    }
    //Ciclo de vida de una actividad: onStart -> onResume -> (La actividad se está ajecutando) -> onPause -> onStop -> onDestroy
    @Override
    public void onStart(){
        super.onStart();
        if(D) Log.e(TAG, "+++ ON START +++");
        //Si el BT no está habilitado, se solicita que sea habilitado
        if (!AdaptadorBT.isEnabled()) {
            Intent habilitarIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(habilitarIntent, SOLICITAR_BT);
        } else {
            //Codigo para ir al metodo configurar "chat" si el BT está habilitado
            if (servicioBT == null) {
                iniciarChat();
            }
        }
    }

    @Override
    public synchronized void onResume(){ //a continuación siempre de onStart. Si entra en pausa o parada vuelve aquí.
        if(D) Log.e(TAG, "+++ ON RESUME +++");
        super.onResume();

        if (servicioBT != null) {
            Toast.makeText(getApplicationContext(), "ServidorBT es distinto null", Toast.LENGTH_SHORT).show();
            Toast.makeText(getApplicationContext(), "Estado: " + servicioBT.getEstado(), Toast.LENGTH_SHORT).show();
            //Obtenemos el estado de nuestra app. Si no tiene ninguno, iniciamos "chat"
            if (servicioBT.getEstado() == servicioBT.SIN_ESTADO) {
                servicioBT.start();
            }
        }
        //---------------------------------------------------------
        }

    @Override
    public synchronized void onPause(){
        super.onPause();
    }

    @Override
    public void onStop(){
        super.onStop();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
    }
    //Inicio del "chat" -> accedemos a la actividad ServicioBT
    private void iniciarChat(){
        if(D) Log.e(TAG, "+++ INICIAR CHAT +++");
        servicioBT = new ServicioBT(this,handlerBT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if(D) Log.e(TAG, "+++ ON ACTIVITY RESULT +++");
        switch (requestCode){

        case SOLICITAR_BT: //Resultado tras la petición de habilitar o no BT
            if(D) Log.e(TAG, "+++ ON ACTIVITY RESULT: ENCENDER BT +++");

            if (resultCode == AppCompatActivity.RESULT_OK){
                //Si el BT ya está habilitado se configura el "chat"
                Toast.makeText(this, "Congrats has activado el BT con exito", Toast.LENGTH_SHORT).show();
                iniciarChat();

            }else{
                Toast.makeText(this, "ohhhh el BT no fue habilitado... Prueba otra vez", Toast.LENGTH_SHORT).show();
                finish(); //salimos de la app
            }

            break;


        case DEVUELVE_DISPOSITIVO:
            //La actividad Lista de Dispositivos nos devuelve un dispositivo para conectar
            if(resultCode == Activity.RESULT_OK){
                String direccionMAC = data.getExtras().getString(ListaDispositivos.DIRECCION_DISPOSITIVO); //Nos devuelve la direccion MAC
                Toast.makeText(getApplicationContext(), "Ha elegido: " + direccionMAC, Toast.LENGTH_SHORT).show(); //lo mostramos por pantalla
                if(AdaptadorBT != null && AdaptadorBT.isEnabled()){ //si es distinto de null y está disponible

                    BluetoothDevice dispositivo = AdaptadorBT.getRemoteDevice(direccionMAC); //obtenemos el objeto BluetoothDevice (representa el dispositivo BT remoto) para una dirección MAC dada. Permite crear una conexion

                    if(dispositivo == null){
                        Toast.makeText(getApplicationContext(), "Variable vacia", Toast.LENGTH_SHORT).show();
                    }else {
                        if(servicioBT == null){
                            Toast.makeText(getApplicationContext(), "Servicio BT es null", Toast.LENGTH_SHORT).show();
                        }else{
                            Toast.makeText(getApplicationContext(), "Se ha conectado: " + dispositivo, Toast.LENGTH_SHORT).show();
                            servicioBT.conectar(dispositivo); //le pasamos el dispositivo remoto al que conectar
                        }

                    }

                }else{
                    Toast.makeText(getApplicationContext(), "AdaptadorBT es null", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private final Handler handlerBT = new Handler(){
        public void handleMessage(Message msg){ //te permite enviar y procesar objetos Message o Runnable asociados a un subproceso (ServicioBT)
            if(D) Log.i(TAG, "+++ Handler +++");
            switch (msg.what){
            case CAMBIO_ESTADO:
                if(D) Log.i(TAG, "CAMBIO_ESTADO " + msg.arg1);
                switch (msg.arg1){
                    case ServicioBT.CONECTADO:
                        if(D) Log.i(TAG, "+++ Handler +++ CONECTADO");
                        break;
                    case ServicioBT.CONECTANDO:
                        if(D) Log.i(TAG, "+++ Handler +++ CONECTANDO");
                        break;
                    case ServicioBT.EN_ESCUCHA:
                    case ServicioBT.SIN_ESTADO:
                        if(D) Log.i(TAG, "+++ Handler +++ SIN_ESTADO");
                        break;
                }
                break;

            case LEER:
                if(D) Log.i(TAG, "+++ Handler +++ LEER");
                Toast.makeText(getApplicationContext(), "Ha entrado en modo Lectura", Toast.LENGTH_SHORT).show();
                //--------------LECTURA DEL DATO-----------------------
                final String leerMensaje = msg.obj.toString(); //convertimos a string
                fechaHora.setText(sdf.format(new java.util.Date())); //indicamos la hora del dato recogido
                if((leerMensaje.length() > 0)){//siempre que mi variable no esté vacía
                    temperatura.setText(leerMensaje);//mostramos el dato leído
                //--------------REPRESENTACIÓN DEL DATO----------------
                    mTimer1 = new Runnable() {
                        @Override
                        public void run() {
                            double prueba = Double.parseDouble(leerMensaje);//lo convertimos a double para que se pueda representar en la gráfica
                            datos.appendData(new DataPoint((lastX++), prueba), true, 4000); //me construyo mi eje X (segundos)
                        }
                    };
                    handlerTimer.postDelayed(mTimer1,500);
                    if(D) Log.d(TAG, "+++ HANDLE LEER:+++" + leerMensaje);
                }
                break;
            case NOMBRE_DISPOSITIVO:
                //Salva el nombre dispositivo conectado
                if(D) Log.i(TAG, "+++ Handler +++ NOMBRE_DISPOSITIVO");
                NombreDispConectado = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Entrega NOMBRE_DISPOSITIVO", Toast.LENGTH_SHORT).show();
                Toast.makeText(getApplicationContext(), "Se ha conectado a " + NombreDispConectado, Toast.LENGTH_SHORT).show();
                break;
            case MENSAJE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                break;

            }
        }
    };

}
