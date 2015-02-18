package com.example.dhivya.igest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class BluetoothCommandService {
    // Debugging
    private static final String TAG = "BluetoothCommandService";
    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothCommandService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        //mConnectionLostCount = 0;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Main_Activity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_LISTEN);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device)
    {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        // Now we need wait for initialization to start. and we store socket..
        //setFlagSocket(socket);
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Main_Activity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Main_Activity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // save connected device
        //mSavedDevice = device;
        // reset connection lost count
        //mConnectionLostCount = 0;

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */

    private void connectionFailed() {
        setState(STATE_LISTEN);
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Main_Activity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Main_Activity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private class ConnectThread extends Thread
    {
        private BluetoothSocket mmSocket=null;
        private BluetoothDevice mmDevice=null;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                tmp = mmDevice.createRfcommSocketToServiceRecord(UUID.randomUUID());
                Log.d("socket", "comm socket created");
                mmSocket = tmp;
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("", e.getMessage());
            }
        }

        @Override
        public void run()
        {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();
            try {
                    mmSocket.connect();
                    Log.d("socket", "connection established");
                }
                catch(Exception e) {
                    e.printStackTrace();
                    Log.d("", "trying fallback...");
                    try {

                        mmSocket = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(mmDevice, 1);
                        mmSocket.connect();
                        Log.d("", "Connected");
                        } catch (Exception ex) {
                        ex.printStackTrace();
                        connectionFailed();
                        // Close the socket
                        try {
                            mmSocket.close();
                        } catch (IOException e2) {
                            Log.e(TAG, "unable to close() socket during connection failure", e2);
                        }
                        // Start the service over to restart listening mode
                        BluetoothCommandService.this.start();
                        return;
                    }
                }
            // Reset the ConnectThread because we're done
            synchronized (BluetoothCommandService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel()
        {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread
    {
        private BluetoothSocket mmSocket;
        private InputStream mmInStream;

        public ConnectedThread(BluetoothSocket socket)
        {
            mmSocket=socket;
            InputStream tmpIn = null;
            try {
                tmpIn = mmSocket.getInputStream();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            mmInStream = tmpIn;
        }
        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
        }
        public void cancel()
        {
            try{
                mmSocket.close();
            }catch (Exception e){
                e.printStackTrace();}
        }
    }
}