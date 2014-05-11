package com.example.practica8bluetoothinternacionalizacion.app;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
//import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class Principal extends Activity {

    // Debug
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Typos de mensajes enviados desde el manejador de BluetoothChatService
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Nombre de llaves recibidas desde el manejador de BluetoothChatService
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Código de las respuesta de Intent
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    // Nombre del dispositivo conectado
    private String mConnectedDeviceName = null;
    // Array adapter para hilo de conversación
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer para la salida de mensajes
    private StringBuffer mOutStringBuffer;
    // Bluetooth adapter local
    private BluetoothAdapter mBluetoothAdapter = null;
    // objeto de BluetoothChatService
    private BluetoothChatService mChatService = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_principal);

        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Obtiene el adaptador Bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Si el adaptador es null, Entonces no soporta bluetooth y cierra la app
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.not_support, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(D) Log.e(TAG, "+ ON RESUME +");

        // verifica en onResume() si el BT fue no disponible durante el onStart()
        // porque los pasamos si estaba disponible...
        // onResume() será llamada cuando el activity regrese ACTION_REQUEST_ENABLE.
        if (mChatService != null) {
            // Solo si el estado es STATE_NONE, sabemos que no esta inicializado todavía
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Iniciar el BluetoothChatService
                mChatService.start();
            }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Inicializa el array adapter para el hilo de la conversación
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Inicializa los campos componentes con listener para regresar la llave
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Inicializa el botón de envío con listener a los eventos click
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Envía mensaje usando contenido del edit text
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });

        // Inicializa BluetoothChatService para realizar la conexión bluetooth
        mChatService = new BluetoothChatService(this, mHandler);

        // Inicialializa el buffer de mensajes de salida
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // Si bluetooth no esta encendido, regresa que esta disponible.
        // setupChat() entonces será llamada en onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // De otra manera, configuramos la sesión de chat
        } else {
            if (mChatService == null) setupChat();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Detenemos el servicio de chat
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Verififica si actualmente estamos conectado antes de intentar cualquier cosa
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Verifica que actualmente enviaron algo
        if (message.length() > 0) {
            // Obtiene el mensaje en bytes y llama a BluetoothChatService para escribirlo
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Regresa el buffer string de salida a cero y limpia el campo editText
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    // La acción de listener para EditText, el listener devuelve la llave
    private TextView.OnEditorActionListener mWriteListener =
            new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    // Si la acción es un evento key-up regresa la llave, envía el mensaje
                    if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                        String message = view.getText().toString();
                        sendMessage(message);
                    }
                    if(D) Log.i(TAG, "END onEditorAction");
                    return true;
                }
            };

    private final void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(resId);
    }

    private final void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(subTitle);
    }

    // El manejador que regresa información de vuelta a BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // Construye un string desde el buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // Construye un string desde un byte de buffer válido
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    // Guardar la conexión del nombre del dispositivo
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // Cuando DeviceListActivity regresa con que dispositivo esta conectado
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // Cuando DeviceListActivity regresa con que dispositivo esta conectado
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // Cuando regresa que esta disponible el Bluetooth
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth esta disponible, para iniciar la configuración de inicio de sesión del chat
                    setupChat();
                } else {
                    // El usuario no tenía disponible el bluetooth y ocurrió un error
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Obtiene la MAC address del dispositivo
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Obtiene el objeto BluetoothDevice
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Intenta conectarse al dispositivo
        mChatService.connect(device, secure);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Infla el menú; agrega estos items al action bar si esta presente
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
            case R.id.secure_connect_scan:
                // Lanza el DeviceListActivity para ver los archivos y escanearlos
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            case R.id.insecure_connect_scan:
                // Lanza el DeviceListActivity para ver los archivos y escanearlos
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            case R.id.discoverable:
                // Asegurar que este dispositivo es visible para otros
                ensureDiscoverable();
                return true;
        }
        return false;
    }
}
