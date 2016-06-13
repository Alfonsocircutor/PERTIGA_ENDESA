package com.cemitec.circuitorBlue;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;


/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();
    
    private Queue<BluetoothGattCharacteristic> characteristicReadQueue = new LinkedList<BluetoothGattCharacteristic>();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt; //Cemitec:: Clase que proporciona el interface con GATT se crea asociando la clase callback mGattCallback

    public final static String ACTION_GATT_CONNECTED =
            "com.st.bluenrg.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.st.bluenrg.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.st.bluenrg.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.st.bluenrg.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.st.bluenrg.EXTRA_DATA";
    public final static String ACTION_ARRAY_DATA_AVAILABLE =
            "com.st.bluenrg.ACTION_ARRAY_DATA_AVAILABLE";

    

    public final static UUID UUID_DATA =
            UUID.fromString("a32e5520-e477-11e2-a9e3-0002a5d5c51b"); //Notification
    public final static UUID UUID_STATUS =
            UUID.fromString("cd20c480-e48b-11e2-840b-0002a5d5c51b"); //READ
    public final static UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_CONFIG =
            UUID.fromString("0c366e80-cf3a-11e1-9ab4-0002a5d5c51b"); //WRITE
    public final static UUID UUID_CONTROL =
            UUID.fromString("0a366e80-cf3a-11e1-9ab4-0002a5d5c51b"); //WRITE

    //Data received by notification
    public int indexNotificacion=0;
    int[][] array2DMuestras = new int[102][2];
    int[][] array2DMuestrasDescarga = new  int[512][2];
    byte[] arrayMuestrasDecargaBytes = new  byte[512*4+4];
    boolean modoDescargaLE; //0 RealTime; 1 Descarga
    boolean descargaParcial = false;
    int indexByteDescargaParcial = 0;
    boolean desincronizado = false;


    //Cemitec: Creamos la clase gatt callback
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");

                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
                mBluetoothAdapter.disable();
                mBluetoothAdapter.enable();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        	Log.d("ble", "stefano108onServicesDiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override //Cemitec Cuando viene por bluetooth nuevos valores se llama automaticamente esta funcion
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
        	Log.d("ble", "stefano104onCharacteristicRead");
        	characteristicReadQueue.remove(); //Cemitec Eliminamos la COLA
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }else{
        		Log.d(TAG, "onCharacteristicRead error: " + status);
    		}
            if(characteristicReadQueue.size() > 0) //Cemitec????
        		mBluetoothGatt.readCharacteristic(characteristicReadQueue.element());
        		
        }

        
        @Override
        public void onReadRemoteRssi (BluetoothGatt gatt, int rssi, int status){
        	Log.d("ble", "stefano105onReadRemoteRssi");
        	if (status == BluetoothGatt.GATT_SUCCESS) {
        		final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
                intent.putExtra(EXTRA_DATA, "5;"+rssi);
                sendBroadcast(intent);
            }else{
        		Log.d(TAG, "onReadRemoteRssi error: " + status);
    		}
        }
        
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) { 
        	Log.d("ble", "stefano106onDescriptorWrite");        
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Callback: Wrote GATT Descriptor successfully.");           
            }           
            else{
                Log.d(TAG, "Callback: Error writing GATT Descriptor: "+ status);
            }

            if(characteristicReadQueue.size() > 0)
                mBluetoothGatt.readCharacteristic(characteristicReadQueue.element());
        };

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
        	Log.d("ble", "stefano107onCharacteristicChanged");      
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic); //Cemitec vienen nuevo datos y actualizamos con el broascaster.
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    public void setModo(boolean modoDescarga){
        modoDescargaLE = modoDescarga;
    }
    public void setNotificacionIndex(int index){
        indexNotificacion = index;
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {



        final Intent intent = new Intent(action);


       if (UUID_DATA.equals(characteristic.getUuid())) {

          // Log.d("Cemitec", "MODO DESCARGA = " + modoDescargaLE );

           if(modoDescargaLE == false) {

               byte[] value = characteristic.getValue();
               //    Log.d("Cemitec", "Recibiendo Descarga TIEMPO REAL = " + Sensor.detectaMarca(value) );
               for (int indexMuestra = 0; indexMuestra < 6; indexMuestra++) {
                   array2DMuestras[indexNotificacion * 6 + indexMuestra][0] = (int) Sensor.extractTension(value, indexMuestra);
                   array2DMuestras[indexNotificacion * 6 + indexMuestra][1] = (int) Sensor.extractIntensidad(value, indexMuestra);
               }
               if (indexNotificacion == 16) {


                   if (Sensor.detectaMarca(value) == true){

                       Log.d("Cemitec", "Recibiendo Descarga TIEMPO REAL = " + indexNotificacion );

                       Intent graphIntent = new Intent("INTENT_NAME");
                       Bundle graphicsBundle = new Bundle();
                       graphicsBundle.putSerializable("key_array_array", array2DMuestras);
                       graphIntent.putExtras(graphicsBundle);
                       LocalBroadcastManager.getInstance(this).sendBroadcast(graphIntent);
                       desincronizado = false;
                       indexNotificacion = -1;
                   } else {

                       value = characteristic.getValue();
                       if (Sensor.detectaMarca(value) != true){
                           indexNotificacion = 16;
                           desincronizado = true;
                       }
                       else{
                           indexNotificacion = -1;
                           desincronizado = false;
                       }
                   }
               }

               if (desincronizado == false) indexNotificacion++;
           }
           else{ //modo Descarga modoDescargaLE = true

               //Se va a marcar la notificación con FF al final de la última muestra valida para poder descargar parcialmente el registro.

               Log.d("Cemitec", "Recibiendo Descarga = " + indexNotificacion );

               byte[] value = characteristic.getValue(); //Recibida Notificacion
               byte[] byteMask = new byte[2];

               for (int indexByte = 0; indexByte < 18; indexByte++) {
                   arrayMuestrasDecargaBytes[indexNotificacion * 18 + indexByte] = value[indexByte];
                   byteMask[0]= value[indexByte];


                   if((indexNotificacion*18 + indexByte) >= 2048){
                       Log.d("Cemitec", "DESCARGA COMPLETA2 = " + indexNotificacion + " indexByte = " + indexByte);
                       break;
                   }

                   //si 2 bytes seguidos a 0xFF se sale
                   if((indexByte > 0) && ((byteMask[0] & byteMask[1]) == (byte)0xa5 )){
                   //if((indexByte > 0) && ((byteMask[0] == 0xa5) && (byteMask[1] == 0xa5))){
                       //Enviamos al grafico el subconjunto de muestras el index sample se forma con el indexNotificacion*18 + indexByte - 2
                       indexByteDescargaParcial = indexByte;
                       descargaParcial = true;
                      // No me daja  Toast.makeText(this, "DESCARGA PARCIAL", Toast.LENGTH_SHORT).show();
                       Log.d("Cemitec", "DESCARGA PARCIAL = " + indexNotificacion + " indexByte = " + indexByte);
                       break;
                   }
                   byteMask[1] = byteMask[0];
               }
               if (descargaParcial == true) {

                   for(int indexSample = 0; indexSample < ((indexNotificacion*18 + indexByteDescargaParcial - 2))/4 ; indexSample++) {
                       array2DMuestrasDescarga[indexSample][0] = ((arrayMuestrasDecargaBytes[indexSample*4]&0xff) + ((arrayMuestrasDecargaBytes[indexSample*4 +1]&0xff)<<8) );
                       array2DMuestrasDescarga[indexSample][1] = ((arrayMuestrasDecargaBytes[indexSample*4 + 2]&0xff) + ((arrayMuestrasDecargaBytes[indexSample*4 + 2 + 1]&0xff)<<8));
                   }
                   Log.d("Cemitec", "DESCARGA PARCIAL to Chart = " + indexNotificacion );
                   Intent graphIntent = new Intent("INTENT_NAME");
                   Bundle graphicsBundle = new Bundle();
                   graphicsBundle.putSerializable("key_array_array", array2DMuestrasDescarga);
                   graphIntent.putExtras(graphicsBundle);
                   LocalBroadcastManager.getInstance(this).sendBroadcast(graphIntent);
                   descargaParcial = false;
               }
               else if (indexNotificacion == 113){ //Descarga normal
                   //Parsear el contenido de  array2DMuestrasDescarga

                  // No me daja Toast.makeText(this, "DESCARGA COMPLETA", Toast.LENGTH_SHORT).show();
                   Log.d("Cemitec", "DESCARGA COMPLETA = " + indexNotificacion );

                   for(int indexSample = 0; indexSample < 512; indexSample++) {
                       array2DMuestrasDescarga[indexSample][0] = ((arrayMuestrasDecargaBytes[indexSample*4]&0xff) + ((arrayMuestrasDecargaBytes[indexSample*4 +1]&0xff)<<8) );
                       array2DMuestrasDescarga[indexSample][1] = ((arrayMuestrasDecargaBytes[indexSample*4 + 2]&0xff) + ((arrayMuestrasDecargaBytes[indexSample*4 + 2 + 1]&0xff)<<8));
                   }

                   Log.d("Cemitec", "DESCARGA ARRAY = " + array2DMuestrasDescarga[15][0] );
                   Log.d("Cemitec", "DESCARGA VALUE = " +   value[0] + " " + value[1] + " " + value[2] + " " + value[3]);

                   Intent graphIntent = new Intent("INTENT_NAME");
                   Bundle graphicsBundle = new Bundle();
                   graphicsBundle.putSerializable("key_array_array", array2DMuestrasDescarga);
                   graphIntent.putExtras(graphicsBundle);
                   LocalBroadcastManager.getInstance(this).sendBroadcast(graphIntent);
               }
               indexNotificacion++;
           }

            
        } else if (UUID_STATUS.equals(characteristic.getUuid())) {
        	byte estado = (characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8,0)).byteValue();

            Log.d("Cemitec", String.format("COMPROBACION STATUS " + estado));
            intent.putExtra(EXTRA_DATA, String.valueOf("3;" + estado)); //El numero indica el tipo de dato recibido ->
            sendBroadcast(intent);

        }else {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                Log.d(TAG, "Caratteristica generica");
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        
      //  sendBroadcast(intent);
    }
    

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        if (mBluetoothGatt!=null) mBluetoothGatt.close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        //Cemitec, Creamos  isi no existe el interfaz GATT
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
      
        characteristicReadQueue.add(characteristic);
        if(characteristicReadQueue.size() == 1)
        	System.out.println("lectura");
            mBluetoothGatt.readCharacteristic(characteristic); 
    }


    //Write Characteristic

    public boolean writeCharacteristic(byte value[],BluetoothGattCharacteristic characteristic){

        //check mBluetoothGatt is available
        if (mBluetoothGatt == null) {
            Log.e(TAG, "lost connection while writing");
            return false;
        }

        boolean status1 = false;

        characteristic.setValue(value);
        status1 = mBluetoothGatt.writeCharacteristic(characteristic);
        Log.v("Cemitec", "WRITE CHARATERISTICS STATUS: " + status1);

        if (characteristic == null) {
            Log.e(TAG, "char not found!");
            return false;
        }

        return status1;
    }



    public boolean readRemoteRssi (){
    	return mBluetoothGatt.readRemoteRssi();
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        
       if (UUID_DATA.equals(characteristic.getUuid())) {
            Log.d("stefano", "UUID_DATA setCharacteristicNotification");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[] { 0x00, 0x00 });
            return mBluetoothGatt.writeDescriptor(descriptor); //descriptor write operation successfully started?
        }
        return false;
    }
    

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
