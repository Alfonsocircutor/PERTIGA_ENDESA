package com.cemitec.circuitorBlue;


import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MotionEvent;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.CatmullRomInterpolator;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import android.widget.ZoomControls;

import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static android.R.layout.simple_spinner_item;


public class DetailsActivity extends FragmentActivity implements ActionBar.TabListener {
	//private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private static TextView temperatura, pressione, umidita,segnale;
    private static ImageView temperaturaImg,pressioneImg, umiditaImg;
    private static ProgressBar progressx,progressy,progressz;
    public static int x,y,z;
    private static int [][] caratteristiche = new int [7][2]; //Estaba [5][2] add contro and config

    
    public static String mDeviceName;
    public static String mDeviceAddress;
    private static BluetoothLeService mBluetoothLeService;
    private static ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    
    private boolean foundAcceleration=false, foundTemperature=false;
     
    Intent gattServiceIntent;
    
    public static final int WRONG_FIRMWARE = 201;
    public static final int BOARD_DISCONNECTED = 202;

    static boolean  isStarted = false;

    static byte estado = -1;

    static XYPlotTensionActivity paginaTension;
    static XYPlotIntensidadActivity paginaIntensidad;

    static boolean modoDescarga = false; //0 Realtime  ; 1 Descarga.

    static View rootView;

    static Button buttonStop;
    static Button ButtonStart;

    static int dominioTemporal;

    static int zoomValue = 1;

    static int positionSpinner = 0;



    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
            	String currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
            	DeviceScanActivity.log.add(new visualLog(currentTime,"Unable to initialize Bluetooth"));
            	System.out.println("Unable to initialize Bluetooth");
                finish();
            }

            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {

            	Log.d("ble", "disconnesso");
            	disconnesso();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d("ble", "services discovered");
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                Log.d("Cemitec", "ACTION_DATA_AVAILABLE");

            	String valoreRisposta =  intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
            	String [] temp = valoreRisposta.split(";");
            	int caso = Integer.parseInt(temp[0]);
            	switch (caso){

            	case 3:
                   // Log.d("Cemitec", "Recibimos el ESTADO");
            		estado = Byte.parseByte(temp[1]);
                    Log.d("Cemitec", "Recibimos el ESTADO " + estado);
                    //Refrescamos grafico
                    paginaTension.setStatusText();
                    paginaIntensidad.setStatusText();

                    if (estado == 3){
                        Button buttonTR = (Button) rootView.findViewById(R.id.button4);
                        buttonTR.setEnabled(false);
                        Log.d("Cemitec", String.format("deshabilitamos el boton TR 5 "));
                    }
                    else{
                        Button buttonTR = (Button) rootView.findViewById(R.id.button4);
                        buttonTR.setEnabled(true);
                        Log.d("Cemitec", String.format("habilitamos el boton TR 3 "));
                    }

            		break;
            	}
            }
        }
    };
    
    public void disconnesso(){
    	mBluetoothLeService.close();

		setResult(BOARD_DISCONNECTED);
		finish();
    }

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide fragments for each of the
     * three primary sections of the app. We use a {@link android.support.v4.app.FragmentPagerAdapter}
     * derivative, which will keep every loaded fragment in memory. If this becomes too memory
     * intensive, it may be best to switch to a {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    AppSectionsPagerAdapter mAppSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will display the three primary sections of the app, one at a
     * time.
     */
    ViewPager mViewPager;
	private Thread dataThread;
	private boolean isBound;
	public static LinearLayout pressureHumidityView;
    public ActionBar actionBar;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        DeviceScanActivity.accesso = true;

        mAppSectionsPagerAdapter = new AppSectionsPagerAdapter(getSupportFragmentManager());

        actionBar = getActionBar();

        //actionBar.setHomeButtonEnabled(true);

        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mAppSectionsPagerAdapter);
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });


        actionBar.addTab(
                actionBar.newTab()
                        .setText("MODO DE CAPTURA") //Era Motion
                        .setTabListener(this));
        actionBar.addTab(
                actionBar.newTab()
                        .setText("TENSION")
                        .setTabListener(this));
        actionBar.addTab(
                actionBar.newTab()
                        .setText("INTENSIDAD")
                        .setTabListener(this));
        
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        String currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
    	DeviceScanActivity.log.add(new visualLog(currentTime,"Connected! Device Name: "+mDeviceName));
    	DeviceScanActivity.log.add(new visualLog(currentTime,"Device Address: "+mDeviceAddress));
        System.out.println("deviceName: " + mDeviceName);
        System.out.println("deviceAddress: " + mDeviceAddress);

        gattServiceIntent = new Intent(this, BluetoothLeService.class);

        isBound = bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        if (isBound == true){
            Toast.makeText(this, "ESPERA ...", Toast.LENGTH_SHORT)
                    .show();
        }
        Toast.makeText(this, "ESPERA ...", Toast.LENGTH_SHORT).show();
        
    }
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
	//	getMenuInflater().inflate(R.menu.details, menu);
		return true;
	}
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_log:
            	//Intent intent = new Intent(this, LogActivity.class);
        	    //startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            
            System.out.println("Connect request result=" + result);
        }
        
        if (dataThread==null) {
            dataThread = new Thread(new Runnable() {
            	@Override
            	public void run() {
            		try {
            			int j=0;
            			while (mGattCharacteristics == null || mGattCharacteristics.size()==0){
            				Thread.sleep(500);
            				System.out.println("thread__waiting_data");
            			}
                        //Data notification
                       // BluetoothGattCharacteristic characteristic0 = mGattCharacteristics.get(caratteristiche[0][0]).get(caratteristiche[0][1]);
                       // mBluetoothLeService.setCharacteristicNotification(characteristic0, true);
            			Thread.sleep(150);

            		} catch (Exception e) {
            			e.getLocalizedMessage();
            		}
            	}
            });
            dataThread.start();
        }
        
        super.onResume();

        Toast.makeText(this, "ESPERA ...", Toast.LENGTH_SHORT).show();
    }
    
    private void error() {
    	setResult(WRONG_FIRMWARE);
 	   DetailsActivity.this.finish();

    }

    @Override
    protected void onPause() {

    	unregisterReceiver(mGattUpdateReceiver);
        super.onPause();
        Toast.makeText(this, "DETALLES ON PAUSE", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        chiudiServizio();
        if(dataThread != null){
            Thread moribund = dataThread;
            dataThread = null;
            moribund.interrupt();
          }
        mBluetoothLeService = null;
        Toast.makeText(this, "DETALLES ON DESTROY", Toast.LENGTH_SHORT).show();
    }
    
    private void chiudiServizio () {
        if(mBluetoothLeService!=null)mBluetoothLeService.stopSelf();
        stopService(new Intent(this, BluetoothLeService.class));
        if (isBound) {
        	unbindService(mServiceConnection);
        	isBound = false;
        }
    }
    
    @Override
    public void onBackPressed() {
    	chiudiServizio();
        if(dataThread != null){
            Thread moribund = dataThread;
            dataThread = null;
            moribund.interrupt();
          }
        mBluetoothLeService = null;
        finish();
        Toast.makeText(this, "DETALLES BACK PRESSED", Toast.LENGTH_SHORT).show();
    	}


    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();


        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();

            uuid = gattService.getUuid().toString();

            currentServiceData.put(
                    LIST_NAME, unknownServiceString);

            currentServiceData.put(LIST_UUID, uuid);

            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();


            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, unknownCharaString);
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
                Log.d("Cemitec", String.format("ADD CHARAS "));
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        int i=0,j;
        for (ArrayList<BluetoothGattCharacteristic> service : mGattCharacteristics){
        	j=0;
        	for (BluetoothGattCharacteristic gatt : service){
        		UUID uid = gatt.getUuid();
        	if (BluetoothLeService.UUID_DATA.equals(uid)) {
                	String currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
        	    	DeviceScanActivity.log.add(new visualLog(currentTime,"Found Data"));
                	caratteristiche[0][0]=i;
                    caratteristiche[0][1]=j;

                } else if (BluetoothLeService.UUID_STATUS.equals(uid)) {
                	String currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
        	    	DeviceScanActivity.log.add(new visualLog(currentTime,"Found status"));
                	caratteristiche[1][0]=i;
        			caratteristiche[1][1]=j;
                } else if (BluetoothLeService.UUID_CONFIG.equals(uid)){
                    String currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    DeviceScanActivity.log.add(new visualLog(currentTime,"Found Configuracion"));
                    Log.d("Cemitec", "cartateristica configuracion ");
                    caratteristiche[2][0]=i;
                    caratteristiche[2][1]=j;
                }else if (BluetoothLeService.UUID_CONTROL.equals(uid)){
                    String currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    DeviceScanActivity.log.add(new visualLog(currentTime,"Found Control"));
                    Log.d("Cemitec", "cartateristica control ");
                    caratteristiche[3][0]=i;
                    caratteristiche[3][1]=j;
                }

        		j++;
        	}
        	i++;
        }
        Log.d("Cemitec", String.format("DETECTADAS CARACTERISTICAS "));
        Toast.makeText(this, "SELECCIONA MODO", Toast.LENGTH_SHORT).show();

        //TODO

        //BluetoothGattCharacteristic characteristic1 = mGattCharacteristics.get(caratteristiche[1][0]).get(caratteristiche[1][1]);
        //mBluetoothLeService.readCharacteristic(characteristic1);

        //Despues de detectar las caracteísticas se habilitan los botones

        Button buttonTR = (Button) rootView.findViewById(R.id.button4);
        buttonTR.setEnabled(true);
        //habilito los botones.
        Button buttonDescarga = (Button) rootView.findViewById(R.id.button5);
        buttonDescarga.setEnabled(true);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
    
    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to one of the primary
     * sections of the app.
     */
    public  class AppSectionsPagerAdapter extends FragmentPagerAdapter {

        public AppSectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case 0:
                    Log.d("Cemitec", String.format("item acelerometro "));
                    return new SeleccionModoFragment();
                case 1:
                    Log.d("Cemitec", String.format("item grafico "));
                    paginaTension = new XYPlotTensionActivity();
                    return paginaTension;
                case 2:
                    paginaIntensidad = new XYPlotIntensidadActivity();
                    return paginaIntensidad;
                default:
                    Log.d("Cemitec", String.format("item default "));
                	return new SeleccionModoFragment();
            }
        }

        @Override
        public int getCount() {
            return 3;
        }


    }

    /**
     * A dummy fragment representing a section of the app, but that simply displays dummy text.
     */
    public  class SeleccionModoFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            rootView = inflater.inflate(R.layout.configuracion_tester, container, false);
            //Cuando se crean los botones está deshabilitados. Despues de cargar las características se habilitan
            Button buttonTR = (Button) rootView.findViewById(R.id.button4);
            buttonTR.setEnabled(false);
            Log.d("Cemitec", String.format("deshabilitamos el boton TR 4 "));
            Button buttonDescarga = (Button) rootView.findViewById(R.id.button5);
            buttonDescarga.setEnabled(false);

            String colors[] = {"5 segundos","15 segundos","30 segundos",
                    "60 segundos","5 minutos", "15 minutos","30 minutos","60 minutos",
                    "2 horas", "8 horas", "12 horas", "24 horas" };
            Spinner spn = (Spinner) rootView.findViewById(R.id.spinner);

            ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(this.getActivity(),   android.R.layout.simple_spinner_item, colors);
            spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spn.setAdapter(spinnerArrayAdapter);

            spn.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                    Log.d("Cemitec", String.format("selecionamos spinner  " + position + " id " + id));
                    positionSpinner = position;
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                    Log.d("Cemitec", String.format("selecionamos spinner nothing selected  "));
                }

            });

            return rootView;
        }
    }



    public  class XYPlotTensionActivity extends Fragment  implements OnClickListener // , OnTouchListener
    {

        int numberOfPoints = 100;

        private XYPlot plotV;
        private boolean isSecondActivityLaunched;
        Bundle b; //Datos a representar
        View m_view;
        double valorEficaz = 0;


        public double getMaxFromData(Number[] data){
            double max = -1000000000.0;
            for(int i = 0; i < data.length; i++){
                if((double)data[i] > max) max = (double)data[i];
            }
            return max;
        }

        public double getMinFromData(Number[] data){
            double min = 1000000000.0;
            for(int i = 0; i < data.length; i++){
                if((double)data[i] < min) min = (double)data[i];
            }
            return min;
        }


        private BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if((isStarted == true) && (modoDescarga == false)) { //Cemitec -> es necesario??
                    numberOfPoints = 100;
                    b = intent.getExtras();
                    graph();
                    plotV.redraw();
                    Log.d("Cemitec", String.format("Intent del BROADCAST RECEIVER TENSION 100" + isStarted + modoDescarga));

                }
                else if((isStarted == false) && (modoDescarga == true)){
                    numberOfPoints = 512;
                    b = intent.getExtras();
                    graph();
                    plotV.redraw();
                    Log.d("Cemitec", String.format("Intent del BROADCAST RECEIVER TENSION 512" + isStarted + modoDescarga));
                }
                else{
                    Log.d("Cemitec", String.format("No DIBUJAMOS RECEIVER " + isStarted  + modoDescarga ));
                }
            }
        };

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            LocalBroadcastManager.getInstance(container.getContext()).registerReceiver(mReceiver, new IntentFilter("INTENT_NAME"));
            View rootView = inflater.inflate(R.layout.xy_plot_tension, container, false);
            Log.d("Cemitec", String.format("CREAMOS el GRAFICO "));
            m_view = rootView;
            Button b = (Button) rootView.findViewById(R.id.StartButtonV);
            b.setOnClickListener(this);
            Button b2 = (Button) rootView.findViewById(R.id.StopButtonV);
            b2.setOnClickListener(this);
            Button b3 = (Button) rootView.findViewById(R.id.StatusButtonV);
            b3.setOnClickListener(this);
            //plotV = (XYPlot) rootView.findViewById(R.id.plot_tension);
            //plotV.setOnTouchListener(this);
            ZoomControls zoom = (ZoomControls) rootView.findViewById(R.id.zoomControls);
            zoom.setOnZoomInClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    {
                        zoom_mas(view);
                    }
                }
            });
            zoom.setOnZoomOutClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    {
                        zoom_menos(view);
                    }
                }
            });

            //Cambiamos el tamaño del array a representar dependiendo del modo de captura.

            if(modoDescarga == false){

                numberOfPoints = 100;
            }
            else{
                numberOfPoints = 512;
            }


            return rootView;
        }

        @Override
        public void onClick(View v) {


            byte[] miDato = new byte[2];
            BluetoothGattCharacteristic characteristic1;
            switch (v.getId()) {
                case R.id.StatusButtonV:

                    //   characteristic1 = mGattCharacteristics.get(caratteristiche[1][0]).get(caratteristiche[1][1]);
                    //   mBluetoothLeService.readCharacteristic(characteristic1);



                    Log.d("Cemitec", String.format("LEEMOS CARACTERISTICA "));

                    Handler handler3 = new Handler();
                    handler3.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            EstableceStatus();
                        }
                    }, 200);


                    break;
                case R.id.StartButtonV:

                  //  ButtonStart = (Button) m_view.findViewById(R.id.StartButtonV);
                  //  ButtonStart.setBackgroundColor(Color.RED);
                    if (estado == 3){
                        Toast.makeText(m_view.getContext(), "SE ESTAN CAPTURANDO DATOS", Toast.LENGTH_SHORT).show();
                    }
                    else{
                        Toast.makeText(m_view.getContext(), "START OK", Toast.LENGTH_SHORT).show();
                    }
                    isStarted = true;
                    miDato[0] = 1;
                    miDato[1] = 0;

                    //  characteristic1 = mGattCharacteristics.get(caratteristiche[3][0]).get(caratteristiche[3][1]);
                    // Log.d("Cemitec", "caracteristica a escribir " + characteristic1);
                    // mBluetoothLeService.writeCharacteristic(miDato, characteristic1);
                    EstableceStart(miDato);


                    Log.d("Cemitec", String.format("LANZAMOS el GRAFICO "));
                    //Iniucializar a ceros el registro de datos

                    Button buttonTR = (Button) rootView.findViewById(R.id.button4);
                    buttonTR.setEnabled(false);
                    Button buttonDescarga = (Button) rootView.findViewById(R.id.button5);
                    buttonDescarga.setEnabled(false);

                    buttonStop = (Button)m_view.findViewById(R.id.StopButtonV);
                    buttonStop.setEnabled(false);


                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            buttonStop = (Button)m_view.findViewById(R.id.StopButtonV);
                            buttonStop.setEnabled(true);
                        }
                    }, 200);


                    break;
                case  R.id.StopButtonV:

                    isStarted = false;
                 //   ButtonStart = (Button) m_view.findViewById(R.id.StartButtonV);
                 //   ButtonStart.setBackgroundColor(Color.LTGRAY);

                    miDato[0] = 0;
                    miDato[1] = 1;

                    EstableceStop(miDato);

                    // characteristic1 = mGattCharacteristics.get(caratteristiche[3][0]).get(caratteristiche[3][1]);
                    // mBluetoothLeService.writeCharacteristic(miDato,characteristic1);
                    //  mBluetoothLeService.setNotificacion(0);
                    Log.d("Cemitec", String.format("PARAMOS el GRAFICO "));
                    Button buttonTR2 = (Button) rootView.findViewById(R.id.button4);
                    buttonTR2.setEnabled(true);
                    Button buttonDescarga2 = (Button) rootView.findViewById(R.id.button5);
                    buttonDescarga2.setEnabled(true);

                    ButtonStart = (Button)m_view.findViewById(R.id.StartButtonV);
                    ButtonStart.setEnabled(false);


                    Handler handler2 = new Handler();
                    handler2.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ButtonStart = (Button) m_view.findViewById(R.id.StartButtonV);
                            ButtonStart.setEnabled(true);
                        }
                    }, 200);


                    break;
            }
        }

        public void setStatusText()
        {
            TextView txtView = (TextView) m_view.findViewById(R.id.StatusTextV);
            txtView.setText("state = " + estado);
           // Toast.makeText(m_view.getContext(), "SE ESTAN CAPTURANDO DATOS", Toast.LENGTH_SHORT).show();
        }

        public void graph() {

            //Variables

            TextView txtView = (TextView) m_view.findViewById(R.id.StatusTextV);
            txtView.setText("state = " +estado);

            // initialize our XYPlot reference:
            plotV = (XYPlot) m_view.findViewById(R.id.plot_tension);


           // plotV.setRangeStep(XYStepMode.INCREMENT_BY_PIXELS, 20);

            plotV.clear();

            plotV.getLayoutManager().moveToTop(plotV.getTitleWidget());
            int[][] arrayReceived=null;

         //   Log.d("Cemitec", String.format("Bundle size " + b.size()));
            Object[] objectArray = (Object[]) b.getSerializable("key_array_array");
            Log.d("Cemitec", String.format("Bundle size " + objectArray.length));
            if(objectArray!=null){
                arrayReceived = new int[objectArray.length][];
                for(int i=0;i<objectArray.length;i++){
                    arrayReceived[i]=(int[]) objectArray[i];
                }
            }

            if(isStarted == true && modoDescarga == false) { //Tiempo REal
                numberOfPoints = numberOfPoints / zoomValue;
                if (numberOfPoints < 4) numberOfPoints = 4;
            }
            else{
                numberOfPoints = 512 / zoomValue;
                if (numberOfPoints < 4) numberOfPoints = 4;
            }

            Log.d("Cemitec", String.format("numberOfPoints " + numberOfPoints));

            Number[] series2Numbers = new Number[numberOfPoints];

            for (int i = 0; i < numberOfPoints; i++) {

                if(isStarted == true && modoDescarga == false) {

                    series2Numbers[i] = (Number) ((double)(arrayReceived[i][0]) * 996.0 / 4095.0 - 498.0);
                   // series2Numbers[i] = (Number) ((double)(arrayReceived[i][0]));
                    valorEficaz += ((double)(arrayReceived[i][0]) * 996.0 / 4095.0 - 498.0)* ((double)(arrayReceived[i][0]) * 996.0 / 4095.0 - 498.0);

                }
                else{
                    series2Numbers[i] =  (Number)((double) (arrayReceived[i][0]) / 10.0);//(Number) new Double(temp);
                }
            }
            valorEficaz = Math.sqrt(valorEficaz/(double)numberOfPoints);
            //alfonso
            valorEficaz = valorEficaz * (46.0/102.61);
            TextView tv = (TextView) m_view.findViewById(R.id.valorEficazText);
            //String total = Double.toString(valorEficaz);
            tv.setText(String.format( "%.2f", valorEficaz ));

            //Construcción del eje X


            Number[] ejex;

            if(isStarted == true && modoDescarga == false) //Tiempo REal
            {
                        ejex = new Number[numberOfPoints];

                for (int i = 0; i < numberOfPoints; i++) {

                    ejex[i] = (Number) ((double) (i));//(Number) new Double(temp);
                }
            }else{
                    ejex = new Number[numberOfPoints];

                for (int i = 0; i < numberOfPoints; i++) {

                    ejex[i] = (Number) ((double) (i*dominioTemporal / 1000.0));//(Number) new Double(temp);
                }

            }



            //XYSeries series2 = new SimpleXYSeries(Arrays.asList(series2Numbers),
            //        SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "VOLTIOS");

            //XYSeries series2 = new SimpleXYSeries(Arrays.asList(series2Numbers),
            //        Arrays.asList(ejex), "VOLTIOS ");

            XYSeries series2 = new SimpleXYSeries(Arrays.asList(ejex),Arrays.asList(series2Numbers),
                     "AMPS ");


            LineAndPointFormatter series2Format = new LineAndPointFormatter();
            series2Format.setPointLabelFormatter(new PointLabelFormatter());
            series2Format.configure(m_view.getContext().getApplicationContext(),
                    R.xml.line_point_formatter_with_labels_2);




            series2Format.setInterpolationParams(
                    new CatmullRomInterpolator.Params(10, CatmullRomInterpolator.Type.Uniform));

        //    Log.d("Cemitec", String.format("max " + getMinFromData(series2Numbers)));
       //     Log.d("Cemitec", String.format("min " + getMaxFromData(series2Numbers)));

            if(isStarted == true && modoDescarga == false) //Tiempo REal
            {
                if ( Math.abs(getMinFromData(series2Numbers) - getMaxFromData(series2Numbers)) < 0.1 ){
                   // plotV.setRangeBoundaries(getMaxFromData(series2Numbers) - 0.1 , getMaxFromData(series2Numbers) + 0.1, BoundaryMode.FIXED);
                    plotV.setRangeBoundaries(getMaxFromData(series2Numbers) - 0.1 , 500, BoundaryMode.FIXED);
                    plotV.setDomainBoundaries(0, 99, BoundaryMode.AUTO);
                }
                else {
                    //plotV.setRangeBoundaries(  getMinFromData(series2Numbers),   getMaxFromData(series2Numbers), BoundaryMode.FIXED);
                //    plotV.setRangeBoundaries(-500, 500, BoundaryMode.AUTO);
                    //Alfonso
                    plotV.setRangeBoundaries(-350, 350, BoundaryMode.FIXED);
                    plotV.setDomainBoundaries(0, 99, BoundaryMode.AUTO);
                }
            }
            else{

                if ( Math.abs(getMinFromData(series2Numbers) - getMaxFromData(series2Numbers)) < 0.1 ){
               //     plotV.setRangeBoundaries(getMaxFromData(series2Numbers) - 0.1 , getMaxFromData(series2Numbers) + 0.1, BoundaryMode.FIXED);
                    plotV.setRangeBoundaries(-0.1 , 400, BoundaryMode.FIXED);
                    plotV.setDomainBoundaries(0, 512.0, BoundaryMode.AUTO);
                }
                else {
                    //plotV.setRangeBoundaries(  getMinFromData(series2Numbers),   getMaxFromData(series2Numbers), BoundaryMode.FIXED);
                    plotV.setRangeBoundaries(0, 400, BoundaryMode.AUTO);
                    // plot2.setDomainBoundaries(0, 511, BoundaryMode.FIXED); dominioTemporal
                    //  Log.d("Cemitec", "Eje X" + dominioTemporal / 1000.0 * 512.0);
                    //   plot2.setDomainBoundaries(0, dominioTemporal / 1000.0 * 512.0, BoundaryMode.FIXED);
                    plotV.setDomainBoundaries(0, 512.0, BoundaryMode.AUTO);
                    //   plot2.setDomainStep(XYStepMode.INCREMENT_BY_VAL, 1);
                    //  plotV.setTicksPerDomainLabel(1);
                }

            }
            plotV.addSeries(series2, series2Format);

            // reduce the number of range labels
            plotV.setTicksPerRangeLabel(3);

            // rotate domain labels 45 degrees to make them more compact horizontally:
            plotV.getGraphWidget().setDomainLabelOrientation(-45);
        }


        private long lastTouchTime = -1;



     //   byte[] miDato;
        //BluetoothGattCharacteristic characteristic1;
    /*    @Override
        public boolean onTouch(View v, MotionEvent e) {

            miDato = new byte[2];
            BluetoothGattCharacteristic characteristic1;

            //Solo con el grafico.

            if(v.getId()== R.id.plot_tension) {

                if (e.getAction() == MotionEvent.ACTION_DOWN) {

                    long thisTime = System.currentTimeMillis();
                    if (thisTime - lastTouchTime < 250) {
                        // Double click


                        lastTouchTime = -1;
                        if (isStarted == true) { //Stop
                            Log.d("Cemitec", String.format(" Double Click Stop"));

                            isStarted = false;
                            miDato[0] = 0;
                            miDato[1] = 1;

                            this.getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        BluetoothGattCharacteristic characteristic1 = mGattCharacteristics.get(caratteristiche[3][0]).get(caratteristiche[3][1]);
                                        mBluetoothLeService.writeCharacteristic(miDato, characteristic1);
                                    }
                                });


                            mBluetoothLeService.setNotificacionIndex(0);
                            Log.d("Cemitec", String.format("PARAMOS el GRAFICO "));
                            Button buttonTR2 = (Button) rootView.findViewById(R.id.button4);
                            buttonTR2.setEnabled(true);
                            Log.d("Cemitec", String.format("habilitamos el boton TR 1 "));
                            Button buttonDescarga2 = (Button) rootView.findViewById(R.id.button5);
                            buttonDescarga2.setEnabled(true);
                        } else { //Start
                            Log.d("Cemitec", String.format(" Double Click Start"));

                            if (estado == 3) {
                                Toast.makeText(m_view.getContext(), "SE ESTAN CAPTURANDO DATOS", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(m_view.getContext(), "START OK", Toast.LENGTH_SHORT).show();
                            }
                            isStarted = true;
                            miDato[0] = 1;
                            miDato[1] = 0;
                            for (int i = 0; i < 512; i++) {
                                mBluetoothLeService.array2DMuestrasDescarga[i][0] = 0;
                                mBluetoothLeService.array2DMuestrasDescarga[i][1] = 0;
                            }

                            this.getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    BluetoothGattCharacteristic characteristic1 = mGattCharacteristics.get(caratteristiche[3][0]).get(caratteristiche[3][1]);
                                    mBluetoothLeService.writeCharacteristic(miDato, characteristic1);
                                }
                            });
                            Log.d("Cemitec", String.format("LANZAMOS el GRAFICO "));
                            //Iniucializar a ceros el registro de datos

                            Button buttonTR = (Button) rootView.findViewById(R.id.button4); //Botn Tiempo real
                            buttonTR.setEnabled(false);
                            Log.d("Cemitec", String.format("deshabilitamos el boton TR 1 "));
                            Button buttonDescarga = (Button) rootView.findViewById(R.id.button5); //Boton Descarga
                            buttonDescarga.setEnabled(false);
                        }
                    } else {
                        // too slow
                        //   Log.d("Cemitec", String.format("Single Click"));
                        lastTouchTime = thisTime;
                    }
                }
            }
           // Log.d("Cemitec", String.format("started" + isStarted));
            return false;
        }*/


        //*****************
        // Definition of the touch states
      /*  static final int NONE = 0;
        static final int ONE_FINGER_DRAG = 1;
        static final int TWO_FINGERS_DRAG = 2;
        int mode = NONE;

        PointF firstFinger;
        float distBetweenFingers;
        boolean stopThread = false;


        @Override
        public boolean onTouch(View arg0, MotionEvent event) {

            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: // Start gesture
                    firstFinger = new PointF(event.getX(), event.getY());
                    mode = ONE_FINGER_DRAG;
                    stopThread = true;
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                    mode = NONE;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN: // second finger
                    distBetweenFingers = spacing(event);
                    // the distance check is done to avoid false alarms
                    if (distBetweenFingers > 5f) {
                        mode = TWO_FINGERS_DRAG;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mode == ONE_FINGER_DRAG) {
                        PointF oldFirstFinger = firstFinger;
                        firstFinger = new PointF(event.getX(), event.getY());
                     //   scroll(oldFirstFinger.x - firstFinger.x);
                     //   plotOne.setDomainBoundaries(minXY.x, maxXY.x,
                     //           BoundaryMode.FIXED);
                     //   plotOne.redraw();
                     //   plotTwo.setDomainBoundaries(minXY.x, maxXY.x,
                     //           BoundaryMode.FIXED);
                     //   plotTwo.redraw();

                    } else if (mode == TWO_FINGERS_DRAG) {
                        float oldDist = distBetweenFingers;
                        distBetweenFingers = spacing(event);
                     //   Toast.makeText(this.getActivity(), "zoom = " + oldDist / distBetweenFingers, Toast.LENGTH_SHORT).show();
                        Log.d("Cemitec", String.format("zoom = " + oldDist / distBetweenFingers));
                        zoomValue += (oldDist / distBetweenFingers - 1 )*100;
                        zoom(zoomValue/2);
                      //  zoom(oldDist / distBetweenFingers);
                      //  plotOne.setDomainBoundaries(minXY.x, maxXY.x,
                      //          BoundaryMode.FIXED);
                      //  plotOne.redraw();
                      //  plotTwo.setDomainBoundaries(minXY.x, maxXY.x,
                      //          BoundaryMode.FIXED);
                      //  plotTwo.redraw();
                    }
                    break;
            }
            return true;
        }
        private float spacing(MotionEvent event) {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);
            return (float)Math.sqrt(x * x + y * y);
        }*/

    }

    public static class XYPlotIntensidadActivity extends Fragment  implements OnClickListener
    {

        int numberOfPoints = 100;
        private XYPlot plotI;
        private boolean isSecondActivityLaunched;
        Bundle b; //Datos a representar
        View m_view;

        double valorEficaz = 0;

        public double getMaxFromData(Number[] data){
            double max = -1000000000.0;
            for(int i = 0; i < data.length; i++){
                if((double)data[i] > max) max = (double)data[i];
            }
            return max;
        }

        public double getMinFromData(Number[] data){
            double min = 1000000000.0;
            for(int i = 0; i < data.length; i++){
                if((double)data[i] < min) min = (double)data[i];
            }
            return min;
        }

        private BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(isStarted == true && modoDescarga == false) { //Cemitec -> es necesario??
                    numberOfPoints = 100;
                    b = intent.getExtras();
                    graph();
                    plotI.redraw();
                    Log.d("Cemitec", String.format("Intent del BROADCAST RECEIVER INTENSITY " + isStarted));
                }
                else if(isStarted == false && modoDescarga == true){
                    numberOfPoints = 512;
                    b = intent.getExtras();
                    graph();
                    plotI.redraw();
                    Log.d("Cemitec", String.format("Intent del BROADCAST RECEIVER INTENSITY" + isStarted));
                }
                else{
                    Log.d("Cemitec", String.format("No DIBUJAMOS RECEIVER " + isStarted));
                }
            }
        };

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            LocalBroadcastManager.getInstance(container.getContext()).registerReceiver(mReceiver, new IntentFilter("INTENT_NAME"));
            View rootView = inflater.inflate(R.layout.xy_plot_intensidad, container, false);
            Log.d("Cemitec", String.format("CREAMOS el GRAFICO "));
            m_view = rootView;
            Button b = (Button) rootView.findViewById(R.id.StartButtonI);
            b.setOnClickListener(this);
            Button b2 = (Button) rootView.findViewById(R.id.StopButtonI);
            b2.setOnClickListener(this);
            Button b3 = (Button) rootView.findViewById(R.id.StatusButtonI);
            b3.setOnClickListener(this);

            b.setVisibility(View.INVISIBLE); //To set invisible
            b2.setVisibility(View.INVISIBLE); //To set visible
            b3.setVisibility(View.INVISIBLE); //To set visible


            return rootView;
        }

        @Override
        public void onClick(View v) {

            byte[] miDato = new byte[2];
            BluetoothGattCharacteristic characteristic1;
            Button ButtonStart;
            switch (v.getId()) {
                case R.id.StatusButtonI:

                    characteristic1 = mGattCharacteristics.get(caratteristiche[1][0]).get(caratteristiche[1][1]);
                    mBluetoothLeService.readCharacteristic(characteristic1);

                    Log.d("Cemitec", String.format("LEEMOS CARACTERISTICA "));


                    break;
                case R.id.StartButtonI:

                    ButtonStart = (Button) m_view.findViewById(R.id.StartButtonI);
                    ButtonStart.setBackgroundColor(Color.RED);
                    isStarted = true;


                    miDato[0] = 1;
                    miDato[1] = 0;


                    characteristic1 = mGattCharacteristics.get(caratteristiche[3][0]).get(caratteristiche[3][1]);

                    Log.d("Cemitec", "caracteristica a escribir " + characteristic1);

                    mBluetoothLeService.writeCharacteristic(miDato,characteristic1);

                    Log.d("Cemitec", String.format("LANZAMOS el GRAFICO "));

                    for(int i =0; i < 512; i++){
                        mBluetoothLeService.array2DMuestrasDescarga[i][0] = 0;
                        mBluetoothLeService.array2DMuestrasDescarga[i][1] = 0;
                    }

                    Button buttonTR = (Button) rootView.findViewById(R.id.button4);
                    buttonTR.setEnabled(false);
                    Log.d("Cemitec", String.format("deshabilitamos el boton TR 2 "));
                    Button buttonDescarga = (Button) rootView.findViewById(R.id.button5);
                    buttonDescarga.setEnabled(false);

                    break;
                case  R.id.StopButtonI:
                    ButtonStart = (Button) m_view.findViewById(R.id.StartButtonI);
                    ButtonStart.setBackgroundColor(Color.LTGRAY);
                    isStarted = false;


                    miDato[0] = 0;
                    miDato[1] = 1;

                    characteristic1 = mGattCharacteristics.get(caratteristiche[3][0]).get(caratteristiche[3][1]);
                    mBluetoothLeService.writeCharacteristic(miDato,characteristic1);

                    mBluetoothLeService.setNotificacionIndex(0);

                    Log.d("Cemitec", String.format("PARAMOS el GRAFICO "));

                    Button buttonTR2 = (Button) rootView.findViewById(R.id.button4);
                    buttonTR2.setEnabled(true);
                    Log.d("Cemitec", String.format("habilitamos el boton TR 1 "));
                    Button buttonDescarga2 = (Button) rootView.findViewById(R.id.button5);
                    buttonDescarga2.setEnabled(true);

                    break;
            }
        }
        public void setStatusText()
        {
            TextView txtView = (TextView) m_view.findViewById(R.id.StatusTextI);
            txtView.setText("state = " +estado);
        }

        public void graph() {



            TextView txtView = (TextView) m_view.findViewById(R.id.StatusTextI);
            txtView.setText("state = " +estado);

            // initialize our XYPlot reference:
            plotI = (XYPlot) m_view.findViewById(R.id.plot_intensidad);

            plotI.clear();

            plotI.getLayoutManager().moveToTop(plotI.getTitleWidget());
            int[][] arrayReceived=null;

            Object[] objectArray = (Object[]) b.getSerializable("key_array_array");
            if(objectArray!=null){
                arrayReceived = new int[objectArray.length][];
                for(int i=0;i<objectArray.length;i++){
                    arrayReceived[i]=(int[]) objectArray[i];
                }
            }

            if(isStarted == true && modoDescarga == false) { //Tiempo REal
                numberOfPoints = numberOfPoints / zoomValue;
                if (numberOfPoints < 4) numberOfPoints = 4;
            }
            else{
                numberOfPoints = 512 / zoomValue;
                if (numberOfPoints < 4) numberOfPoints = 4;
            }

            Number[] series2Numbers = new Number[numberOfPoints];
         //   Number[] series1Numbers = new Number[numberOfPoints];



            for (int i = 0; i < numberOfPoints; i++) {
                if(isStarted == true && modoDescarga == false) {
                    double temp = (double) ((double)(arrayReceived[i][1]) * 16.6 / 4095.0 - 8.3);
                   // double temp = (double) ((double)(arrayReceived[i][1]));
                    //double temp = (double) ((double)(arrayReceived[i][0]) * 990.0 / 4095.0 - 495.0);
                    series2Numbers[i] = (Number) new Double(temp);
                    valorEficaz += temp*temp;
                }
                else{
                    double temp = (double) (arrayReceived[i][1]) /10.0;
                    series2Numbers[i] = (Number) new Double(temp);
                }
            }


            //Construcción del eje X

            valorEficaz = Math.sqrt(valorEficaz/(double)numberOfPoints);

            TextView tv = (TextView) m_view.findViewById(R.id.valorEficazText);
            //String total = Double.toString(valorEficaz);
            tv.setText(String.format( "%.2f", valorEficaz ));



            Number[] ejex;

            if(isStarted == true && modoDescarga == false) //Tiempo REal
            {
                ejex = new Number[numberOfPoints];

                for (int i = 0; i < numberOfPoints; i++) {

                    ejex[i] = (Number) ((double) (i));//(Number) new Double(temp);
                }
            }else{
                ejex = new Number[numberOfPoints];

                for (int i = 0; i < numberOfPoints; i++) {

                    ejex[i] = (Number) ((double) (i*dominioTemporal / 1000.0));//(Number) new Double(temp);
                }

            }

            //XYSeries series2 = new SimpleXYSeries(Arrays.asList(series2Numbers),
            //        SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "VOLTIOS");

            //XYSeries series2 = new SimpleXYSeries(Arrays.asList(series2Numbers),
            //        Arrays.asList(ejex), "VOLTIOS ");

            XYSeries series1 = new SimpleXYSeries(Arrays.asList(ejex),Arrays.asList(series2Numbers),
                    "AMPERIOS ");

         //   XYSeries series1 = new SimpleXYSeries(Arrays.asList(series1Numbers),
          //          SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "AMPERIOS (ms)");

          //  XYSeries series2 = new SimpleXYSeries(Arrays.asList(series2Numbers),
           //         SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "VOLTIOS (ms)");


            // create formatters to use for drawing a series using LineAndPointRenderer
            // and configure them from xml:
            LineAndPointFormatter series1Format = new LineAndPointFormatter();
            series1Format.setPointLabelFormatter(new PointLabelFormatter());
            series1Format.configure(m_view.getContext().getApplicationContext(),
                    R.xml.line_point_formatter_with_labels);


            series1Format.setInterpolationParams(
                    new CatmullRomInterpolator.Params(10, CatmullRomInterpolator.Type.Uniform));

            // add a new series' to the xyplot:

            if(isStarted == true && modoDescarga == false) //Tiempo REal
            {
                if ( Math.abs(getMinFromData(series2Numbers) - getMaxFromData(series2Numbers)) < 0.1) {
                   // plotI.setRangeBoundaries(getMaxFromData(series2Numbers) - 0.1, getMaxFromData(series2Numbers) + 0.1, BoundaryMode.FIXED);
                    plotI.setRangeBoundaries(getMaxFromData(series2Numbers) - 0.1, 8.3, BoundaryMode.FIXED);
                    plotI.setDomainBoundaries(0, 99, BoundaryMode.AUTO);
                }
                else {
                    plotI.setRangeBoundaries(-8.3, 8.3, BoundaryMode.AUTO);
                    plotI.setDomainBoundaries(0, 99, BoundaryMode.AUTO);
                }
            }
            else{
                if ( Math.abs(getMinFromData(series2Numbers) - getMaxFromData(series2Numbers)) < 0.1 ) {
                  //  plotI.setRangeBoundaries(getMaxFromData(series2Numbers) - 0.1, getMaxFromData(series2Numbers) + 0.1, BoundaryMode.FIXED);
                    plotI.setRangeBoundaries(-0.1, 10, BoundaryMode.FIXED);
                    plotI.setDomainBoundaries(0, 512.0, BoundaryMode.AUTO);
                }
                else {
                    plotI.setRangeBoundaries(0, 10, BoundaryMode.AUTO);
                    //  plot2.setDomainBoundaries(0, dominioTemporal / 1000.0 * 512.0, BoundaryMode.FIXED);
                    plotI.setDomainBoundaries(0, 512.0, BoundaryMode.AUTO);
                }
            }

            plotI.addSeries(series1, series1Format);

            // reduce the number of range labels
            plotI.setTicksPerRangeLabel(3);

            // rotate domain labels 45 degrees to make them more compact horizontally:
            plotI.getGraphWidget().setDomainLabelOrientation(-45);
        }

    }

  /*  public static void zoom(float zoom){
        zoomValue = (int)zoom;
        if(zoomValue > 10) zoomValue = 10;
        if(zoomValue < 1) zoomValue = 1;
        if(modoDescarga == true) {
            paginaTension.graph();
            paginaIntensidad.graph();
            paginaIntensidad.plotI.redraw();
            paginaTension.plotV.redraw();
        }
    }*/

    public static  void zoom_mas(View view){
        zoomValue = (zoomValue + 1);
        if(zoomValue > 10) zoomValue = 10;

        if(modoDescarga == true) {
            paginaTension.graph();
            paginaIntensidad.graph();
            paginaIntensidad.plotI.redraw();
            paginaTension.plotV.redraw();
        }
    }

    public static void zoom_menos(View view){
        zoomValue = (zoomValue - 1);
        if(zoomValue < 1) zoomValue = 1;
        if(modoDescarga == true) {
            paginaTension.graph();
            paginaIntensidad.graph();
            paginaIntensidad.plotI.redraw();
            paginaTension.plotV.redraw();
        }
    }

    private void EstableceStart(final byte[] miDato ) {
        runOnUiThread(new Runnable() {
            BluetoothGattCharacteristic characteristic1 =  mGattCharacteristics.get(caratteristiche[3][0]).get(caratteristiche[3][1]);
            @Override
            public void run() {
                mBluetoothLeService.writeCharacteristic(miDato ,characteristic1);                     //Call method to write the characteristic
                mBluetoothLeService.setNotificacionIndex(0);

            }
        });
    }

    private void EstableceStop(final byte[] miDato ) {
        runOnUiThread(new Runnable() {
            BluetoothGattCharacteristic characteristic1 =  mGattCharacteristics.get(caratteristiche[3][0]).get(caratteristiche[3][1]);
            @Override
            public void run() {
                mBluetoothLeService.writeCharacteristic(miDato ,characteristic1);//Call method to write the characteristic
                mBluetoothLeService.setNotificacionIndex(0);
            }
        });
    }


    private void EstableceStatus() {
        runOnUiThread(new Runnable() {
            BluetoothGattCharacteristic  characteristic1 = mGattCharacteristics.get(caratteristiche[1][0]).get(caratteristiche[1][1]);
            @Override
            public void run() {

                while (mBluetoothLeService.indexNotificacion > 0 && isStarted == true) {
                    Log.d("Cemitec", String.format("Estabelce Estatus " + mBluetoothLeService.indexNotificacion));
                }
                Log.d("Cemitec", String.format("Se acabo el  while "));
                mBluetoothLeService.readCharacteristic(characteristic1);                     //Call method to write the characteristic
            }
        });
    }

    byte[] miDato;
    public void llamada_tiempoReal(View view) {
        Log.d("Cemitec", String.format("MODO TIEMPO REAL "));
       // BluetoothGattCharacteristic characteristic0 = mGattCharacteristics.get(caratteristiche[0][0]).get(caratteristiche[0][1]);
       // mBluetoothLeService.setCharacteristicNotification(characteristic0, true);
        //Escribir en  Config 0
        miDato = new byte[5];
        miDato[0] = 0;
        miDato[1] = 0;
        miDato[2] = 0;
        miDato[3] = 0;
        miDato[4] = 0;
        modoDescarga = false;
        mBluetoothLeService.setModo(modoDescarga);
        mBluetoothLeService.setNotificacionIndex(0);


        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BluetoothGattCharacteristic characteristic1 = mGattCharacteristics.get(caratteristiche[2][0]).get(caratteristiche[2][1]);
                mBluetoothLeService.writeCharacteristic(miDato,characteristic1);
            }
        });



        Toast.makeText(this, "PRESIONA START PARA VER LAS SEÑALES EN TIEMPO REAL", Toast.LENGTH_SHORT).show();
        actionBar.setSelectedNavigationItem(1);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BluetoothGattCharacteristic characteristic0 = mGattCharacteristics.get(caratteristiche[0][0]).get(caratteristiche[0][1]);
                mBluetoothLeService.setCharacteristicNotification(characteristic0, true);
            }
        });


    }
    //byte[] miDato;
    public void llamada_Descarga(View view)
    {
        Log.d("Cemitec", String.format("MODO DESCARGA "));

        miDato = new byte[5];
        miDato[0] = 1;

        int[] tiemposDeCaptura = {5000, 15000, 30000, 60000, 300000 , 900000 , 1800000 , 3600000,  7200000 , 28800000, 43200000, 86400000};
        dominioTemporal = tiemposDeCaptura[positionSpinner];

        miDato[1] = (byte)((dominioTemporal >> 0) & 0xff);
        miDato[2] = (byte)((dominioTemporal >> 8) & 0xff);
        miDato[3] = (byte)((dominioTemporal >> 16) & 0xff);
        miDato[4] = (byte)((dominioTemporal >> 24) & 0xff);

        modoDescarga = true;
        mBluetoothLeService.setModo(modoDescarga);
        mBluetoothLeService.setNotificacionIndex(0);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BluetoothGattCharacteristic characteristic1 = mGattCharacteristics.get(caratteristiche[2][0]).get(caratteristiche[2][1]);
                mBluetoothLeService.writeCharacteristic(miDato, characteristic1);
            }
        });

        actionBar.setSelectedNavigationItem(1);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BluetoothGattCharacteristic characteristic0 = mGattCharacteristics.get(caratteristiche[0][0]).get(caratteristiche[0][1]);
                mBluetoothLeService.setCharacteristicNotification(characteristic0, true);
            }
        });

    }
}

