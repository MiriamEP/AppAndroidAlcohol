package com.example.mirian.app_v4;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by Mirian on 02/05/2017.
 */

public class ServicioBT {

    //Depurar
    private static final String TAG = "ServicioBT";
    private static final boolean D = true;

    private final BluetoothAdapter AdaptadorBT_aux;
    private final Handler handlerBT;
    private static final String NAME = "ServicioBT";

    private BufferedReader mBufferedReader = null;

    private int estado; //Variable que nos guarda el estado
    //private HiloAceptaConexion hiloAceptaConexion; //hilo acepta conexiones
    private HiloConexionSocket hiloConexionSocket; //conecta un dispositivo y suspende la actividad de descubrimiento
    private HiloTransmision hiloTransmision; //gestiona la conexion y transferencia de datos
    //Constantes para indicar el estado de conexion
    public static final int SIN_ESTADO = 0; //No se está haciendo nada
    public static final int EN_ESCUCHA = 1; //escuchamos conexiones entrantes
    public static final int CONECTANDO = 2; //inicializamos conexiones de nuestro disp al remoto
    public static final int CONECTADO = 3; //conectado al dispositivo remoto

    private static final UUID miUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public ServicioBT(Context context, Handler handler){
        if(D) Log.e(TAG, "+++ SERVICIO BT +++");
        AdaptadorBT_aux = BluetoothAdapter.getDefaultAdapter();
        estado = SIN_ESTADO;
        handlerBT = handler;
    }

    private synchronized void setEstado(int estado_aux){
        estado = estado_aux;
        //Informa del nuevo estado al Handler para actualizar la actividad ppal
        handlerBT.obtainMessage(MainActivity.CAMBIO_ESTADO, estado_aux,-1).sendToTarget();
    }

    public synchronized int getEstado(){
        return estado;
    } //devuelve el estado actual de la conexion

    //inicio del servicio e inicia el hilo que hace de servidor
    public synchronized void start(){
        if(D) Log.e(TAG, "+++ START SERVICIO BT +++");
        //Cancela el subproceso que está intentando realizar una conexion
        if (hiloConexionSocket != null){
            hiloConexionSocket.cancel();
            hiloConexionSocket = null;
        }
        //cancela hilo de la conexion que está corriendo
        if(hiloTransmision != null){
            hiloTransmision.cancel();
            hiloTransmision = null;
        }
        setEstado(EN_ESCUCHA); //a la escucha de peticiones
    }

    //El método conectar inicia la conexion. Se invoca desde la actividad principal. El usuario ha elegido un
    //dispositivo de los emparejados o de los nuevos. El método sincronizado nos asegura que sólo se ejecuta una
    //invocación al método.

    public synchronized void conectar(BluetoothDevice dispositivo){
        if(D) Log.e(TAG, "+++ CONECTAR +++" + dispositivo);
        //Cancelamos los subprocesos que están intentando una conexion
        if(estado == CONECTANDO){
            if(hiloConexionSocket != null){
                hiloConexionSocket.cancel();
                hiloConexionSocket = null;
            }
        }

        //Código para cancelar el hilo de transferencia
        if(hiloTransmision != null){
            hiloTransmision.cancel();
            hiloTransmision = null;
        }
        //Inicia el subproceso para conectar con el dispositivo remoto requerido. Crea una instancia de ConexionSocketThread
        //y después invoca al método start.
        try {
            hiloConexionSocket = new HiloConexionSocket(dispositivo, miUUID);
            hiloConexionSocket.start();
            setEstado(CONECTANDO);
        }catch (Exception e){
            Log.e(TAG, "FALLO EN CONECTAR", e);
        }
    }

    public synchronized void conectado(BluetoothSocket socket, BluetoothDevice dispositivo){
        if(D) Log.e(TAG, "+++ CONECTADO+++");

        if (hiloConexionSocket != null){
            hiloConexionSocket.cancel();
            hiloConexionSocket = null;
        }

        //Código para cancelar hilo de transmisión
        if(hiloTransmision != null){
            hiloTransmision.cancel();
            hiloTransmision = null;
        }

        //Empieza el hilo con el que se administrará la conexion y las transmisiones
        hiloTransmision = new HiloTransmision(socket);
        hiloTransmision.start();

        //enviar el nombre del dispositivo conectado de vuelta a la mainActivity
        Message msg = handlerBT.obtainMessage(MainActivity.NOMBRE_DISPOSITIVO);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, dispositivo.getName());
        msg.setData(bundle);
        handlerBT.sendMessage(msg);
        setEstado(CONECTADO);

    }

    private void conexionPerdida(){
        setEstado(EN_ESCUCHA);
        Message msg = handlerBT.obtainMessage(MainActivity.MENSAJE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Se perdió la conexión");
        msg.setData(bundle);
        handlerBT.sendMessage(msg);
    }

    private class HiloConexionSocket extends Thread {
        //conexion como cliente: inicia una conexion con un dispositivo remoto en el dispositivo que mantenga un socket servidor abierto
        private final BluetoothSocket aux_socket;
        private final BluetoothDevice aux_disp;
        private UUID auxUUID;

        public HiloConexionSocket(BluetoothDevice dispositivo, UUID uuid){
            if(D) Log.d(TAG, "+++ HILO CONEXION SOCKET +++");
            aux_disp = dispositivo;
            auxUUID = uuid;
            BluetoothSocket temporal = null;
            //obtenemos un BluetoothSocket del bluetoothDevice
            try{
                Log.d(TAG, "El UUID es: "+auxUUID);
                temporal = dispositivo.createRfcommSocketToServiceRecord(uuid);

            }catch(IOException e){
                Log.e(TAG, "FALLO EN HILO CONEXION SOCKET:fallo en obtener BluetoothSocket", e);
            }

            aux_socket = temporal;
        }

        public void run(){
            //cancelamos el descubrimiento porque ralentiza la conexion
            AdaptadorBT_aux.cancelDiscovery();

            if(aux_socket == null){
                Log.d(TAG, "+++ HILO CONEXION SOCKET: aux socket es null +++");
            }else{
                Log.d(TAG, "+++ HILO CONEXION SOCKET: aux socket no es null +++");
            }
            //Hacemos una conexion al bluetoothSocket
            try{

                aux_socket.connect(); //connect realiza una búsqueda SDP en el dispositivo remoto a fin de que coincida el uuid. Si la búsqueda tiene éxito
                //y el dispositivo remoto acepta la conexión, éste último  compartirá el canal RFCOMM. Caso contrario excepción
            }catch (IOException e){
                Log.e(TAG, "FALLO HILO CONEXION SOCKET: conexion fallida", e);

                try{ //cerrar socket
                    aux_socket.close();
                }catch (IOException e2){
                    Log.e(TAG, "FALLO HILO CONEXION SOCKET: cerrar socket", e2);
                }

                if(aux_socket == null){
                    Log.e(TAG, "+++ HILO CONEXION SOCKET: aux socket es null +++");
                }else{
                    Log.e(TAG, "+++ HILO CONEXION SOCKET: aux socket no es null +++");
                }

                ServicioBT.this.start();
                return;
            }


            //reseteamos el hilo porque ya está hecha la conexion
            synchronized (ServicioBT.this){
                hiloConexionSocket = null;
            }

            conectado(aux_socket,aux_disp);
        }

        public void cancel(){
            try{
                aux_socket.close();
            }catch (IOException e){
                Log.e(TAG, "FALLO HILO CONEXION SOCKET: cerrar socket 2", e);
            }
        }

    }

    //Este hilo corre durante una conexión con un dispositivo remoto
    private class HiloTransmision extends Thread {
        private final BluetoothSocket socket_aux;
        private InputStreamReader entrada;
        private final OutputStream salida;

        public HiloTransmision (BluetoothSocket socket){
            if(D) Log.e(TAG, "+++ HILO TRANSMISION +++");
            socket_aux = socket;
            InputStream entrada_aux = null;
            OutputStream salida_aux = null;

            try {
                entrada_aux = socket.getInputStream();
                salida_aux = socket.getOutputStream();
            }catch (IOException e){
                Log.e(TAG, "FALLO HILO TRANSMISION: al crear los socket ", e);
            }

            entrada = new InputStreamReader(entrada_aux);
            if(entrada == null){
                if(D) Log.e(TAG, "+++ CONECTADO: entrada vacía+++");
            }else{
                if(D) Log.e(TAG, "+++ CONECTADO: entrada no vacía+++");
            }
            salida = salida_aux;
        }

        public void run(){
            Log.i(TAG, "HiloTransmision RUN");
            //Se mantiene a la escucha de entradas mientras está conectado
            while (true) {
                try {
                    //leemos datos de entrada
                    mBufferedReader = new BufferedReader(entrada);
                    String aString = mBufferedReader.readLine();
                    if(D) Log.d(TAG, "+++ HANDLE LEER buff:+++" + aString);

                    //lo enviamos a la actividad principal
                    handlerBT.obtainMessage(MainActivity.LEER,-1, -1, aString).sendToTarget();

                } catch (IOException e) {
                    Log.e(TAG, "HiloTransmision: Se ha perdido la conexion", e);
                    conexionPerdida();
                    break;
                }
            }
        }


        public void cancel(){
            try {
                socket_aux.close();
            }catch (IOException e){
                Log.e(TAG, "HiloTransmision: Fallo al cerrar el socket", e);
            }
        }

    }

}
