package com.example.practica8bluetoothinternacionalizacion.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Set;


public class DeviceListActivity extends Activity {

    // Debug
    private static final String TAG = "DeviceListActivity";
    private static final boolean D = true;

    // Regresa extra del Intent
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    // Campos miembros
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    private ArrayAdapter<String> mNewDevicesArrayAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configurar la ventana
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.device_list);

        // Da el result CANCELED en caso de que el usuario regrese atrás
        setResult(Activity.RESULT_CANCELED);

        // Inicializa el botón para descubrir dispositivos
        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
            }
        });

        // Inicializa array adapter. Uno para los dispositivos ya emparejados y
        // uno para los nuevos dispositivos descubiertos
        mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        // Busca y configura el ListView de los dispositivos emparejados
        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        // Busca y configura el ListView de los nuevos dispositivos descubiertos
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Registra cuando un dispositivo es descubierto
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        // Registrar cuando ha finalizado de descubrir
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        // Obtiene el adaptador local de Bluetooth
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // Obtiene los dispositivos actualmente emparejados
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        // Si hay dispositivos emparejados, agrega cada uno al ArrayAdapter
        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = getResources().getText(R.string.none_paired).toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Infla en menú; agrega estos items al action bar si esta presente
        getMenuInflater().inflate(R.menu.device_list, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Nos aseguramos que no descubra más
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }

        // Quita el registro el listener
        this.unregisterReceiver(mReceiver);
    }

    /**
     * Comienza a descubrir dispositivos con el BluetoothAdapter
     */
    private void doDiscovery() {
        if (D) Log.d(TAG, "doDiscovery()");

        // Indica el título de escaneando
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        // Enciende el subtítulo del nuevo dispositivo
        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        // Si estamos descubriendo, lo detenemos
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }

        // Petición de descubrir desde BluetoothAdapter
        mBtAdapter.startDiscovery();
    }

    // El listener de on-click para todos los dispositivos en el ListView
    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancela el discovery porque es costoso para nosotros al conectar
            mBtAdapter.cancelDiscovery();

            // Obtiene la dirección MAC del dispositivo, Agarra los últimos 17 carácteres para mostrarlos en la lista
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            // Crea el resultado del intent incluyendo la dirección MAC
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            // guarda el resultado y finaliza esta actividad
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // cuando encuentra un dispositivo
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Obtiene el objeto Bluetooth del intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Si esta listo para emparejar, lo brinca, porque ya se han enlistado
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
                // Cuando termina de descubrir, cambia el título del Activity
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            }
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
