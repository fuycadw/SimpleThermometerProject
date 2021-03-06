package fr.tvbarthel.apps.simplethermometer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import fr.tvbarthel.apps.simplethermometer.dialogfragments.AboutDialogFragment;
import fr.tvbarthel.apps.simplethermometer.dialogfragments.ChangeColorDialogFragment;
import fr.tvbarthel.apps.simplethermometer.dialogfragments.SharedPreferenceColorPickerDialogFragment;
import fr.tvbarthel.apps.simplethermometer.dialogfragments.TemperatureUnitPickerDialogFragment;
import fr.tvbarthel.apps.simplethermometer.utils.ConnectivityUtils;
import fr.tvbarthel.apps.simplethermometer.utils.PreferenceUtils;
import fr.tvbarthel.apps.simplethermometer.widget.STWidgetProvider;

public class MainActivity extends ActionBarActivity implements SharedPreferences.OnSharedPreferenceChangeListener,
		ChangeColorDialogFragment.Listener, TemperatureLoader.Listener {

	/*
		UI Elements
	 */

	//Display the temperature with the unit symbol
	private TextView mTextViewTemperature;
	//Root View
	private RelativeLayout mRelativeLayoutBackground;
	//ImageView of the fair weather icon
	private ImageView mImageViewFair;
	//ImageView of the change weather icon
	private ImageView mImageViewChange;
	//ImageView of the rain weather icon
	private ImageView mImageViewRain;
	//ImageView of the storm weather icon
	private ImageView mImageViewStorm;

	/*
		Other
	 */

	//Default Shared Preferences used in the app
	private SharedPreferences mDefaultSharedPreferences;
	//An AsyncTask used to start the temperature
	private TemperatureLoader mTemperatureLoader;
	//A single Toast used to display textToast
	private Toast mTextToast;

	/*
		Activity Overrides
	 */

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mTemperatureLoader = new TemperatureLoader(this, getApplicationContext());

		//Retrieve the UI elements references
		mTextViewTemperature = (TextView) findViewById(R.id.textViewTemperature);
		mRelativeLayoutBackground = (RelativeLayout) findViewById(R.id.relativeLayout);
		mImageViewFair = (ImageView) findViewById(R.id.imageViewFair);
		mImageViewChange = (ImageView) findViewById(R.id.imageViewChange);
		mImageViewRain = (ImageView) findViewById(R.id.imageViewRain);
		mImageViewStorm = (ImageView) findViewById(R.id.imageViewStorm);

		//Retrieve the default shared preferences instance
		mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		//Listen to the shared preference changes
		mDefaultSharedPreferences.registerOnSharedPreferenceChangeListener(this);
		//Set the background color
		setBackgroundColor();
		//Set the text color
		setTextColor();
		//Set the icon color
		setIconColor();
		//Display the temperature
		displayLastKnownTemperature();
		//refresh the temperature if it's outdated
		refreshTemperatureIfOutdated();
	}

	@Override
	protected void onPause() {
		super.onPause();
		//Stop listening to shared preference changes
		mDefaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
		//hide Toast if displayed
		hideToastIfDisplayed();
		//Pause the temperature Loader
		mTemperatureLoader.pause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_item_action_set_color:
				//Ask for the color you want to change through an AlertDialogFragment
				ChangeColorDialogFragment.newInstance(getResources().getStringArray(R.array.change_color_options)
				).show(getSupportFragmentManager(), null);
				return true;
			case R.id.menu_item_action_temperature_unit:
				//Ask for the temperature unit you want to use
				pickTemperatureUnit();
				return true;
			case R.id.menu_item_action_manual_refresh:
				//Manually update the temperature if it's outdated
				refreshTemperatureIfOutdated(true);
				return true;
			case R.id.menu_item_action_about:
				//Show the about AlertDialogFragment
				displayAbout();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	/*
		SharedPreferences.OnSharedPreferenceChangeListener Override
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String sharedPreferenceKey) {
		boolean broadcastChangeToWidgets = false;
		//the shared preference with the key "sharedPreferenceKey" has changed
		if (sharedPreferenceKey.equals(PreferenceUtils.PREF_KEY_BACKGROUND_COLOR)) {
			//Set the new background color stored in the SharedPreferences "sharedPreferences"
			setBackgroundColor(sharedPreferences);
			broadcastChangeToWidgets = true;
		} else if (sharedPreferenceKey.equals(PreferenceUtils.PREF_KEY_TEXT_COLOR)) {
			//Set the new text color stored in the SharedPreferences "sharedPreferences"
			setTextColor(sharedPreferences);
			broadcastChangeToWidgets = true;
		} else if (sharedPreferenceKey.equals(PreferenceUtils.PREF_KEY_ICON_COLOR)) {
			//Set the new icon color stored in the SharedPreferences "sharedPreferences"
			setIconColor(sharedPreferences);
			broadcastChangeToWidgets = true;
		} else if (sharedPreferenceKey.equals(PreferenceUtils.PREF_KEY_TEMPERATURE_UNIT_STRING)) {
			//Display the temperature with the new unit stored in the SharedPreferences "sharedPreferences"
			displayLastKnownTemperature();
			broadcastChangeToWidgets = true;
		} else if (sharedPreferenceKey.equals(PreferenceUtils.PREF_KEY_LAST_TEMPERATURE_IN_CELSIUS)) {
			//Display the temperature with the new value stored in the SharedPreferences "sharedPreferences"
			//This mainly happens when the App is displayed and an app widget background service
			//update the temperature value.
			displayLastKnownTemperature();
			broadcastChangeToWidgets = true;
		}

		if (broadcastChangeToWidgets) {
			//A change has to be propagate to the app widgets
			Intent intent = new Intent(this, STWidgetProvider.class);
			intent.setAction(STWidgetProvider.APPWIDGET_DATA_CHANGED);
			sendBroadcast(intent);
		}

	}

	/*
		TemperatureLoader.Listener Override
	 */

	@Override
	public void onTemperatureLoadingSuccess() {
		//The temperature has been correctly loaded
		//and stored in the defaultSharedPreferences
		//so the last known temperature should be the
		//new temperature that has just been retrieved.
		displayLastKnownTemperature();
	}

	@Override
	public void onTemperatureLoadingProgress(int progress) {
		//Display the weather loader progress
		mTextViewTemperature.setText(String.format(getString(R.string.message_loading_progress), progress));
	}

	@Override
	public void onTemperatureLoadingFail(int stringResourceId) {
		//Show the reason of the failure
		makeTextToast(stringResourceId);
		//Display the last known temperature
		displayLastKnownTemperature();
	}

	@Override
	public void onTemperatureLoadingCancelled() {
		//Display the last known temperature
		displayLastKnownTemperature();
	}


	/*
		ChangeColorDialogFragment.Listener Override
	 */
	@Override
	public void onChangeColorRequested(int which) {
		String sharedPrefColor = PreferenceUtils.PREF_KEY_BACKGROUND_COLOR;
		if (which == 1) {
			sharedPrefColor = PreferenceUtils.PREF_KEY_TEXT_COLOR;
		} else if (which == 2) {
			sharedPrefColor = PreferenceUtils.PREF_KEY_ICON_COLOR;
		}
		pickSharedPreferenceColor(sharedPrefColor);
	}

	/**
	 * Display the temperature with a unit symbol.
	 * The temperature and the unit are retrieved from {@code mDefaultSharedPreferences}
	 * so the temperature should be up to date.
	 */
	private void displayLastKnownTemperature() {
		final String temperature = PreferenceUtils.getTemperatureAsString(this, mDefaultSharedPreferences);
		mTextViewTemperature.setText(temperature);
	}

	/**
	 * Retrieve the icon color stored in a {@link android.content.SharedPreferences},
	 * and apply a color filter to the icon ImageViews.
	 *
	 * @param sharedPreferences the {@link android.content.SharedPreferences} used to retrieve the icon color
	 */
	private void setIconColor(SharedPreferences sharedPreferences) {
		//Retrieve the icon color
		final int iconColor = PreferenceUtils.getIconColor(this, sharedPreferences);
		//Apply a color Filter to the four ImageViews
		mImageViewFair.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
		mImageViewChange.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
		mImageViewRain.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
		mImageViewStorm.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
	}

	private void setIconColor() {
		setIconColor(mDefaultSharedPreferences);
	}

	/**
	 * Retrieve the text color stored in a {@link android.content.SharedPreferences},
	 * and set it to the textViews.
	 *
	 * @param sharedPreferences the {@link android.content.SharedPreferences} used to retrieve the text color
	 */
	private void setTextColor(SharedPreferences sharedPreferences) {
		//Retrieve the text color
		final int textColor = PreferenceUtils.getTextColor(this, sharedPreferences);
		//Set the text color to the temperature textView
		mTextViewTemperature.setTextColor(textColor);
	}

	private void setTextColor() {
		setTextColor(mDefaultSharedPreferences);
	}


	/**
	 * Retrieve the background color stored in a {@link android.content.SharedPreferences},
	 * and use it to set the background color of {@code mRelativeLayoutBackground}.
	 *
	 * @param sharedPreferences the {@link android.content.SharedPreferences} used to retrieve the background color
	 */
	private void setBackgroundColor(SharedPreferences sharedPreferences) {
		final int backgroundColor = PreferenceUtils.getBackgroundColor(this, sharedPreferences);
		mRelativeLayoutBackground.setBackgroundColor(backgroundColor);
	}

	private void setBackgroundColor() {
		setBackgroundColor(mDefaultSharedPreferences);
	}

	/**
	 * Show a {@link fr.tvbarthel.apps.simplethermometer.dialogfragments.TemperatureUnitPickerDialogFragment}
	 * to ask the user to chose a temperature unit.
	 */
	private void pickTemperatureUnit() {
		TemperatureUnitPickerDialogFragment.newInstance(getResources().getStringArray(R.array.pref_temperature_name),
				getResources().getStringArray(R.array.pref_temperature_unit_symbols)).show(getSupportFragmentManager(), null);
	}

	/**
	 * Show a {@link fr.tvbarthel.apps.simplethermometer.dialogfragments.SharedPreferenceColorPickerDialogFragment}
	 * to ask the user to chose a color to store for the sharedPreference with the key {@code preferenceKey}
	 *
	 * @param preferenceKey the {@link String} representing the sharedPreference key.
	 */
	private void pickSharedPreferenceColor(String preferenceKey) {
		SharedPreferenceColorPickerDialogFragment.newInstance(preferenceKey,
				getResources().getStringArray(R.array.pref_color_list_names),
				getResources().getIntArray(R.array.pref_color_list_colors)).show(getSupportFragmentManager(), null);
	}


	/**
	 * Show a textToast.
	 *
	 * @param message the {@link String} to show.
	 */
	private void makeTextToast(String message) {
		//hide mTextToast if showing
		hideToastIfDisplayed();
		//make a toast that just contains a text view
		mTextToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
		mTextToast.show();
	}

	private void makeTextToast(int stringId) {
		makeTextToast(getString(stringId));
	}

	/**
	 * Hide {@code mTextToast} if displayed
	 */
	private void hideToastIfDisplayed() {
		if (mTextToast != null) {
			mTextToast.cancel();
			mTextToast = null;
		}
	}


	/**
	 * Refresh the temperature if it's outdated
	 *
	 * @param manualRefresh true if it's a manual refresh request
	 */
	private void refreshTemperatureIfOutdated(boolean manualRefresh) {
		//Get the update Interval
		long updateInterval = TemperatureLoader.UPDATE_INTERVAL_IN_MILLIS;
		if (manualRefresh) updateInterval = TemperatureLoader.UPDATE_INTERVAL_IN_MILLIS_MANUAL;

		if (TemperatureLoader.isTemperatureOutdated(mDefaultSharedPreferences, updateInterval)) {
			if (!ConnectivityUtils.isNetworkConnected(this)) {
				//there is no connection available
				makeTextToast(R.string.error_message_network_not_connected);
			} else {
				mTemperatureLoader.start();
			}
		}
	}

	private void refreshTemperatureIfOutdated() {
		refreshTemperatureIfOutdated(false);
	}


	/**
	 * Show the about information in a {@link fr.tvbarthel.apps.simplethermometer.dialogfragments.AboutDialogFragment}
	 */
	private void displayAbout() {
		new AboutDialogFragment().show(getSupportFragmentManager(), null);
	}

}
