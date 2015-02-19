package com.example.dhivya.igest;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;

/**
 * Created by Dhivya on 19-02-2015.
 */
public class gest_recog extends ActionBarActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_task_l);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        Bundle extras=getIntent().getExtras();
        if(extras!=null)
        {
            String device=extras.getString("Connected Device");
            if(device!=null) {
                getSupportActionBar().setTitle(device);
            }else{
                getSupportActionBar().setTitle(R.string.title_not_connected);
            }
        }
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
