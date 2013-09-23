package com.example.aal_app;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.androidplot.Plot;
import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.BarChart;
import org.achartengine.chart.PointStyle;
import org.achartengine.chart.XYChart;
import org.achartengine.model.*;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.*;
import org.achartengine.renderer.XYSeriesRenderer;
import org.teleal.cling.android.AndroidUpnpService;
import org.teleal.cling.android.AndroidUpnpServiceImpl;
import org.teleal.cling.controlpoint.ActionCallback;
import org.teleal.cling.controlpoint.SubscriptionCallback;
import org.teleal.cling.model.action.ActionArgumentValue;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.gena.CancelReason;
import org.teleal.cling.model.gena.GENASubscription;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.*;
import org.teleal.cling.model.state.StateVariableValue;
import org.teleal.cling.model.types.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.*;
import java.util.*;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import com.androidplot.Plot;
import com.androidplot.xy.*;

import java.util.Observable;
import java.util.Observer;


/**
 * @author Ferhat Özmen
 */



public class Switches extends Activity{

    private String EXTRA_MESSAGE = "UPNP Device";
    private static final String TAG = "AAL LOG: ";

    private Device upnp_device;
    private ArrayList input_value;
    private ArrayAdapter<DeviceDisplay> listAdapter;

    private AndroidUpnpService upnpService;
    private SubscriptionCallback callback;
    private String unique_device_identifier;
    private Bundle savedInstanceState;

    private GraphicalView mChart;
    private XYMultipleSeriesDataset mDataSet = new XYMultipleSeriesDataset();
    private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer();

    private TimeSeries mCurrentSeries;
    private org.achartengine.renderer.XYSeriesRenderer mCurrentRenderer;

    private void initChartBoolean()
    {
        mCurrentSeries = new TimeSeries("Sample Data");
        mDataSet.addSeries(mCurrentSeries);
        mCurrentRenderer = new XYSeriesRenderer();
        mRenderer.addSeriesRenderer(mCurrentRenderer);
        mRenderer.setPointSize(3);
        //mRenderer.setLabelFormat( new DecimalFormat( "0" ) );
        mRenderer.setYTitle( "Ein und Ausschalt Zyklus" );
        //mRenderer.setYLabels( 2 );
        //mRenderer.addYTextLabel( 0, "Ausgeschaltet" );
        //mRenderer.addYTextLabel( 1, "Eingeschaltet" );
        mRenderer.setYLabelsAlign( Paint.Align.LEFT );
        mRenderer.setYLabelsPadding( 2 );
        mRenderer.setLabelsTextSize( 15 );
        mRenderer.setShowGrid( true );
        mRenderer.setZoomButtonsVisible( true);
        mCurrentRenderer.setPointStyle( PointStyle.DIAMOND);
        mCurrentRenderer.setFillPoints( true );
        // mCurrentRenderer.setDisplayChartValues(true);
        mCurrentRenderer.setColor( Color.WHITE );
        //mRenderer.setPanEnabled( true, true );
        mCurrentRenderer.setShowLegendItem( true );
        mCurrentRenderer.setAnnotationsTextAlign( Paint.Align.LEFT );
        mCurrentRenderer.setAnnotationsTextSize(15);
    }

    private void addBoolData(long x, int y){
        mCurrentSeries.add(x, y);
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat( "HH:mm:ss" );
        mCurrentSeries.addAnnotation(format.format(date), x, y);

    }


    private final static String seekbar_process_tag = "SeekBar Process";

    private ServiceConnection serviceConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className,
                                       IBinder service)
        {
            upnpService = (AndroidUpnpService) service;
            onResume();
        }

        public void onServiceDisconnected(ComponentName className)
        {
            upnpService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.switches);

        Bundle extras = getIntent().getExtras();

        this.unique_device_identifier = extras.getString(EXTRA_MESSAGE);
        this.savedInstanceState = savedInstanceState;
        input_value = new ArrayList();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (upnpService != null && savedInstanceState == null)
        {
            upnp_device = upnpService.getRegistry().getDevice(UDN.valueOf
                    (unique_device_identifier), true);
        }

        if (this.upnp_device != null)
        {
            for (Service service : upnp_device.getServices())
            {
                for(Action action : service.getActions())
                {
                    generateUI(service, action);
                }
                setSubscription(service);
            }
            createUPnPServiceandActionInformations(upnp_device);
        }
        else
        {
            this.savedInstanceState = null;
            onCreate(savedInstanceState);
        }

        LinearLayout layout = (LinearLayout) findViewById(R.id.chart);

        if(mChart == null){
            initChartBoolean();
            mChart = ChartFactory.getCubeLineChartView( this, mDataSet,
                                                        mRenderer, 0.2f);
            layout.addView(mChart);
        } else {
            mChart.repaint();
        }

    }

    @Override
    protected void onStart()
    {
        super.onStart();
        getApplicationContext().bindService(
                new Intent(this, AndroidUpnpServiceImpl.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );
    }

    @Override protected void onDestroy()
    {
        super.onDestroy();
        if (upnpService != null)
        {
            getApplicationContext().unbindService(serviceConnection);
            this.listAdapter = null;
            this.upnp_device = null;
            this.upnpService = null;
            this.input_value = null;
        }

        if (callback != null)
        {
            callback.end();
        }

    }

    @Override
    protected void onPause()
    {
        super.onPause();
        onDestroy();
    }

    protected void executeAction(AndroidUpnpService upnpService,
                                 Service service, final Action action,
                                 final ActionArgument action_argument,
                                 ArrayList input_value, boolean isInput)
    {

        ActionInvocation setTargetInvocation = new SetTargetActionInvocation
                (service, action, action_argument, input_value, isInput);

        // Executes asynchronous in the background
        upnpService.getControlPoint().execute
                (new ActionCallback(setTargetInvocation)
                {
                    @Override
                    public void success(ActionInvocation invocation)
                    {
                        if (action.hasOutputArguments())
                        {
                            ActionArgumentValue value  = invocation.getOutput
                                    (action_argument.getName());
                            showToast("Received Value: " +  value.getValue()
                                    .toString(), false);
                        }
                    }

                    @Override
                    public void failure(ActionInvocation invocation,
                                        UpnpResponse operation,
                                        String defaultMsg)
                    {
                        showToast(defaultMsg, true);
                    }
                }
        );
    }

    public void createInputActions( final Action action,
                                    final ActionArgument action_argument)
    {
        LinearLayout ll = (LinearLayout) findViewById(R.id
                .LinearLayoutInputActionElements);

        TextView tv = (TextView) (findViewById( R.id.InputActionTitle ));
        tv.setText( "Input Action" );

        Switch sw = new Switch(this);
        sw.setText(action.getName());
        sw.setTag( action.getName() );
        sw.setOnCheckedChangeListener( new CompoundButton
                .OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {

                if ( isChecked ) {
                    input_value.add( true );
                    executeAction( upnpService, action.getService(), action,
                                   action_argument, input_value, true );
                    input_value.clear();
                } else {
                    input_value.add( false );
                    executeAction( upnpService, action.getService(), action,
                                   action_argument, input_value, true );
                    input_value.clear();
                }
            }
        } );
        ll.addView(sw);
    }

    public void createOutPutActions( final Action action,
                                     final ActionArgument action_argument)
    {
        LinearLayout ll = (LinearLayout) findViewById(R.id
                .LinearLayoutOutPutActionElements);
        TextView tv = (TextView) findViewById( R.id.OutPutActionTitle );
        tv.setText( "Output Actions" );

        Button button = new Button(this);
        button.setText(action.getName());
        button.setTag(action.getName());
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                executeAction(upnpService, action.getService(), action,
                        action_argument, input_value, false);
            }
        });
        ll.addView(button);
    }

    public void createSeekBarActions(final Service service,
                                     final Action action,
                                     final ActionArgument action_argument)
    {
        int max_range = (int) service.getStateVariable( action_argument
                             .getRelatedStateVariableName()).getTypeDetails()
                .getAllowedValueRange().getMaximum();

        final LinearLayout ll;
        ll = (LinearLayout) findViewById(R.id.LinearLayoutSeekBarElements);

        TextView titlev = (TextView) (findViewById( R.id.SeekBarActionTitle ));
        titlev.setText( "Seekbar Actions" );

        SeekBar sb = new SeekBar(this);
        sb.setTag(action.getName());
        sb.setMax(max_range);
        sb.setProgressDrawable( getResources().getDrawable(
                R.drawable.progressbar ) );

        sb.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener()
        {
            TextView tv;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser)
            {
                if (fromUser)
                {
                    tv = (TextView) ll.findViewWithTag(seekbar_process_tag);
                    tv.setText("Processing: " + progress + "%");
                }
                else
                {
                    tv = (TextView) ll.findViewWithTag(seekbar_process_tag);
                    tv.setText("Actual Process: " + progress + "%");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                tv = (TextView) ll.findViewWithTag(seekbar_process_tag);
                tv.setText("Actual Process: " + seekBar.getProgress() + "%");
                input_value.add( String.valueOf(seekBar.getProgress()) );
                executeAction( upnpService, action.getService(), action,
                               action_argument, input_value, true );
                input_value.clear();
            }
        } );

        TextView tv = new TextView(this);
        tv.setTag(seekbar_process_tag);

        ll.addView(tv);
        ll.addView(sb);
    }

    public void setSwitch(final boolean is_checked,final Action action)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                final View ll = findViewById(R.id
                        .LinearLayoutActionElements);

                Switch mySwitch;
                mySwitch = (Switch) ll.findViewWithTag(action.getName());
                mySwitch.setChecked(is_checked);
            }
        });
    }

    public void setSeekBar(final String value, final  Action action)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                final View ll = findViewById(R.id.LinearLayoutActionElements);

                SeekBar sb;
                sb = (SeekBar) ll.findViewWithTag( action.getName() );
                sb.setProgress( Integer.parseInt(value) );
            }
        });
    }

    private void startEventlistening(StateVariable state_variable)
    {
            this.callback =
                    new SwitchPowerSubscriptionCallback (state_variable, this);

            upnpService.getControlPoint().execute(callback);

    }

    private void generateUI(Service service, Action action)
    {

        for (ActionArgument action_argument : action.getInputArguments())
        {
            if(action_argument.getDatatype().getBuiltin().equals
                        (Datatype.Builtin.BOOLEAN))
            {
                    createInputActions(action, action_argument);
            }
            else if (action_argument.getDatatype().getBuiltin().equals
                        (Datatype.Builtin.UI1))
            {
                    createSeekBarActions(service, action, action_argument);
            }
        }

        for (ActionArgument action_argument : action.getOutputArguments())
        {
            createOutPutActions(action, action_argument);
        }

    }

    private void setSubscription(Service service)
    {
        for (StateVariable stateVariable : service.getStateVariables())
        {
            if (stateVariable.getEventDetails().isSendEvents())
            {
                startEventlistening(stateVariable);
            }
        }
    }

    protected void showToast(final String msg, final boolean longLength)
    {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(
                        Switches.this,
                        msg,
                        longLength ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    public void createUPnPServiceandActionInformations(Device upnp_device)
    {
        LinearLayout ll;
        ll = (LinearLayout) findViewById(R.id.LinearLayoutDeviceInformation);
        TextView tv;

        for (Service services : upnp_device.getServices())
        {
            tv = new TextView(this);
            tv.setText("Service " + services.getServiceType().getType() + ":");
            tv.setHighlightColor(1);
            tv.setTextColor(Color.rgb(50, 205, 50));
            tv.setTextSize(17);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            ll.addView(tv, p);

            for(Action actions : services.getActions())
            {
                tv = new TextView(this);
                tv.setText(actions.getName());
                tv.setTextSize( 15 );
                ll.addView(tv, p);
            }
        }
    }

    public void createUPnPGeneralInformations(Service service)
    {
        LinearLayout ll;
        ll = (LinearLayout) findViewById(R.id.LinearLayoutActionElements);

        TextView tv;

        tv = new TextView(this);
        tv.setText("Device Name: " + service.getDevice().getDetails()
                .getFriendlyName());
        tv.setTextSize(15);

        tv = new TextView(this);
        tv.setText("Device Discription: " +service.getDevice().getDetails()
                .getManufacturerDetails());
        tv.setTextSize(15);

        ll.addView(tv);
    }

    public void addnewPoint(long x, int y){
        addBoolData( x,y );
        if (mChart != null){
            //mRenderer.initAxesRangeForScale(0);
            mChart.repaint();
        }
    }

}