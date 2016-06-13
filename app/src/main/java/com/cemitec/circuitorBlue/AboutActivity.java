package com.cemitec.circuitorBlue;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class AboutActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		
		//WebView  browser=(WebView) findViewById(R.id.webView);
		//browser.setVerticalScrollBarEnabled(false);
		
		TextView tvTitle = (TextView) findViewById(R.id.textViewName);
		TextView tvVersion = (TextView) findViewById(R.id.textViewVersion);
		
		tvTitle.setText(getString(R.string.app_name)+" App");
		try {
			tvVersion.setText("v "+getApplicationContext().getPackageManager()
					.getPackageInfo(getApplicationContext().getPackageName(), 0).versionName);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		Context context = getApplicationContext();
		CharSequence text = "Arranca la Aplicacion!";
		int duration = Toast.LENGTH_SHORT;

		Toast toast = Toast.makeText(context, text, duration);
		//toast.show();
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
			case android.R.id.home:	finish(); return true;
        }
        return true;
    }
	
	public void facebook(View v) {
	}
	
	public void twitter(View v) {
	}

	public void llamadaAGrafico_onClick(View v)
	{
		Intent i = new Intent(this, DeviceScanActivity.class);
		startActivity(i);
	}
}
